package com.example.test;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;
import com.TbInventory.TbInvApplication;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = TbInvApplication.class)
class TbInvApplicationTests {
    
    @Autowired
    private DataSource dataSource;
    
    @Test
    void contextLoads() {
        
    }
    
    @Test
    void testDatabaseConnection() {
        try {
            assertNotNull(dataSource, "DataSource is null - check your database configuration");
            
            try (Connection connection = dataSource.getConnection()) {
                assertNotNull(connection, "Could not establish database connection");
                assertTrue(connection.isValid(1), "Database connection is not valid");
                
                System.out.println("Database connection successful!");
                System.out.println("Database URL: " + connection.getMetaData().getURL());
                System.out.println("Database User: " + connection.getMetaData().getUserName());
                System.out.println("Database Product: " + connection.getMetaData().getDatabaseProductName());
                System.out.println("Database Version: " + connection.getMetaData().getDatabaseProductVersion());
                
            } catch (SQLException sqlEx) {
                String errorMessage = getDetailedSQLErrorMessage(sqlEx);
                System.err.println(errorMessage);
                throw new RuntimeException(errorMessage, sqlEx);
            }
            
        } catch (Exception ex) {
            System.err.println("Database connection test failed: " + ex.getMessage());
            throw ex;
        }
    }
    
    private String getDetailedSQLErrorMessage(SQLException ex) {
        String baseMessage = "Database connection failed: ";
        
        // Common PostgreSQL error codes and user-friendly messages
        switch (ex.getSQLState()) {
            case "08001": // Cannot establish connection
                return baseMessage + "Cannot reach database server at 192.168.0.160:5432. " +
                       "Check if PostgreSQL container is running and port 5432 is accessible.";
                       
            case "28P01": // Invalid password
                return baseMessage + "Invalid username or password. " +
                       "Check POSTGRES_USER and POSTGRES_PASSWORD in your .env file.";
                       
            case "3D000": // Database does not exist
                return baseMessage + "Database 'tbdatabase' does not exist. " +
                       "Check POSTGRES_DB in your .env file or create the database.";
                       
            case "08004": // Server rejected connection
                return baseMessage + "Server rejected the connection. " +
                       "Check PostgreSQL configuration and connection limits.";
                       
            default:
                return baseMessage + ex.getMessage() + 
                       " (SQL State: " + ex.getSQLState() + ", Error Code: " + ex.getErrorCode() + ")";
        }
    }
}