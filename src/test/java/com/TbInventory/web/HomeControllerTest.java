package com.TbInventory.web;

import com.example.test.FastTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Fast unit tests for HomeController.
 *
 * These tests use @WebMvcTest which:
 * - Only loads the web layer (controllers, not services/repositories)
 * - Uses MockMvc to simulate HTTP requests
 * - Runs very fast (no database, minimal Spring context)
 *
 * Perfect for testing:
 * - HTTP routing
 * - Request/response handling
 * - View selection
 * - Model attributes
 */
@FastTest
@WebMvcTest(HomeController.class)
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void homeRoute_ReturnsCorrectViewAndModel() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("title"))
                .andExpect(model().attribute("title", "Trailbreaker Inventory"));
    }

    @Test
    void homeAlternateRoute_ReturnsCorrectViewAndModel() throws Exception {
        mockMvc.perform(get("/home"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("title"))
                .andExpect(model().attribute("title", "Trailbreaker Inventory"));
    }

    @Test
    void homeRoute_Returns200Status() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
    }
}
