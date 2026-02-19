package com.example.smoketests.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SmokeTestGenerationServiceTest {

    private static final String SAMPLE_SWAGGER = """
            {
              "swagger": "2.0",
              "info": {
                "title": "Shopping Cart API",
                "description": "API for managing shopping carts and products with user authentication.",
                "version": "1.0"
              },
              "paths": {
                "/cart": {
                  "post": {
                    "summary": "Create a cart",
                    "description": "Creates a new shopping cart for a user or guest.",
                    "responses": {
                      "201": {
                        "description": "Cart created successfully"
                      }
                    }
                  },
                  "get": {
                    "summary": "Get cart",
                    "responses": {
                      "200": {
                        "description": "Cart fetched successfully"
                      }
                    }
                  }
                },
                "/cart/items": {
                  "post": {
                    "summary": "Add to cart",
                    "description": "Adds one or more items to the shopping cart.",
                    "responses": {
                      "200": {
                        "description": "Items added to cart successfully"
                      }
                    }
                  }
                }
              }
            }
            """;

    @Test
    void generatesOrderedTestsWithDependenciesFromSwaggerSpec() {
        SmokeTestGenerationService service = new SmokeTestGenerationService();

        var response = service.generatePreview(SAMPLE_SWAGGER, true);

        assertNotNull(response);
        assertEquals("Shopping Cart API", response.getTitle());
        assertEquals(3, response.getOperationCount());
        assertTrue(response.isOrderEnforced());
        assertNotNull(response.getGeneratedAt());

        var createCart = response.getTests().stream()
                .filter(t -> "POST".equals(t.getMethod()) && "/cart".equals(t.getPath()))
                .findFirst()
                .orElseThrow();
        var getCart = response.getTests().stream()
                .filter(t -> "GET".equals(t.getMethod()) && "/cart".equals(t.getPath()))
                .findFirst()
                .orElseThrow();
        var addItems = response.getTests().stream()
                .filter(t -> "POST".equals(t.getMethod()) && "/cart/items".equals(t.getPath()))
                .findFirst()
                .orElseThrow();

        assertEquals(201, createCart.getExpectedStatus());
        assertEquals(200, getCart.getExpectedStatus());
        assertEquals(200, addItems.getExpectedStatus());

        assertTrue(getCart.getDependsOn().contains("POST /cart"));
        assertTrue(addItems.getDependsOn().contains("POST /cart"));
        assertFalse(createCart.getDependsOn().contains("POST /cart"));

        assertTrue(createCart.getExecutionOrder() < getCart.getExecutionOrder());
        assertTrue(createCart.getExecutionOrder() < addItems.getExecutionOrder());
    }
}
