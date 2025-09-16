# General Flow for Authorization

## The Spring Security Flow

**1. User logs in:**
```java
// Spring calls your UserDetailsService
@Bean
public UserDetailsService userDetailsService() {
    return username -> {
        // You fetch user from database
        User user = userRepository.findByUsername(username);
        
        // You load their permissions from RBAC tables
        Set<String> permissions = loadUserPermissions(user.getId());
        // Example: ["create_batch", "edit_tank", "view_reports"]
        
        // Spring stores this in memory
        return org.springframework.security.core.userdetails.User.builder()
            .username(user.getUsername())
            .password(user.getPasswordHash())
            .authorities(permissions) // Spring remembers these!
            .build();
    };
}
```

**2. Spring stores user info in SecurityContext:**
```java
// Spring automatically creates this and stores in session/thread:
SecurityContext context = {
    authentication: {
        principal: "john_doe",
        authorities: ["create_batch", "edit_tank", "view_reports"]
    }
}
```

**3. `@PreAuthorize` checks against stored authorities:**
```java
@PreAuthorize("hasAuthority('create_batch')")  
@PostMapping("/batches")
public ResponseEntity<Batch> createBatch() {
    // Before this method runs, Spring automatically does:
    // 1. Gets current user from SecurityContext
    // 2. Checks: does user have "create_batch" in their authorities list?
    // 3. If YES -> method executes
    // 4. If NO -> throws AccessDeniedException (returns 403 Forbidden)
}
```

## What `@PreAuthorize` actually does:

**Behind the scenes Spring creates something like:**
```java
// This is conceptually what happens (simplified)
public ResponseEntity<Batch> createBatch() {
    // Spring's @PreAuthorize logic:
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Collection<String> userAuthorities = auth.getAuthorities();
    
    if (!userAuthorities.contains("create_batch")) {
        throw new AccessDeniedException("Access Denied");
    }
    
    // Only if check passes, your actual method runs:
    return ResponseEntity.ok(batchService.create(batch));
}
```

## The key insight:
- **Login time**: You load permissions from database → Spring caches them
- **Request time**: `@PreAuthorize` checks cached permissions → No database queries!

**Flow summary:**
1. Login → Database query to load permissions → Cached in memory
2. Each request → `@PreAuthorize` → Check cached permissions → Execute method or deny

The genius is that after login, permission checks are just memory lookups, not database queries. That's why it's fast!

Does this clarify how the pieces fit together?
