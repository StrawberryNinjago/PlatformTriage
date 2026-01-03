package com.example.Triage;

import com.example.Triage.model.enums.SqlOperationType;
import com.example.Triage.service.db.SqlParserService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sample test cases for SqlParserService
 * Demonstrates how to test the SQL Sandbox feature
 */
class SqlParserServiceTest {

    private final SqlParserService sqlParserService = new SqlParserService();

    @Test
    void testParseSelectWithWhereClause() {
        String sql = "SELECT * FROM cart_item WHERE cart_id = ? AND los_id = ?";
        SqlParserService.ParsedSql result = sqlParserService.parseSql(sql);

        assertTrue(result.isValid(), "SQL should be valid");
        assertEquals(SqlOperationType.SELECT, result.getOperationType());
        assertEquals("cart_item", result.getTableName());
        assertEquals(Arrays.asList("cart_id", "los_id"), result.getWhereColumns());
    }

    @Test
    void testParseInsertStatement() {
        String sql = "INSERT INTO cart_item (cart_id, los_id, product_code) VALUES (?, ?, ?)";
        SqlParserService.ParsedSql result = sqlParserService.parseSql(sql);

        assertTrue(result.isValid(), "SQL should be valid");
        assertEquals(SqlOperationType.INSERT, result.getOperationType());
        assertEquals("cart_item", result.getTableName());
        assertEquals(Arrays.asList("cart_id", "los_id", "product_code"), result.getColumns());
    }

    @Test
    void testParseUpdateStatement() {
        String sql = "UPDATE cart_item SET quantity = ?, unit_price_cents = ? WHERE cart_id = ?";
        SqlParserService.ParsedSql result = sqlParserService.parseSql(sql);

        assertTrue(result.isValid(), "SQL should be valid");
        assertEquals(SqlOperationType.UPDATE, result.getOperationType());
        assertEquals("cart_item", result.getTableName());
        assertEquals(Arrays.asList("quantity", "unit_price_cents"), result.getColumns());
        assertEquals(Arrays.asList("cart_id"), result.getWhereColumns());
    }

    @Test
    void testParseDeleteStatement() {
        String sql = "DELETE FROM cart WHERE cart_id = ?";
        SqlParserService.ParsedSql result = sqlParserService.parseSql(sql);

        assertTrue(result.isValid(), "SQL should be valid");
        assertEquals(SqlOperationType.DELETE, result.getOperationType());
        assertEquals("cart", result.getTableName());
        assertEquals(Arrays.asList("cart_id"), result.getWhereColumns());
    }

    @Test
    void testRejectMultipleStatements() {
        String sql = "SELECT * FROM cart; DELETE FROM cart;";
        SqlParserService.ParsedSql result = sqlParserService.parseSql(sql);

        assertFalse(result.isValid(), "Multiple statements should be rejected");
        assertTrue(result.getErrorMessage().contains("Multiple statements detected"));
    }

    @Test
    void testRejectEmptySQL() {
        String sql = "";
        SqlParserService.ParsedSql result = sqlParserService.parseSql(sql);

        assertFalse(result.isValid(), "Empty SQL should be rejected");
        assertEquals("SQL is empty", result.getErrorMessage());
    }

    @Test
    void testDetectOperationType() {
        assertEquals(SqlOperationType.SELECT,
                sqlParserService.parseSql("SELECT * FROM cart").getOperationType());
        assertEquals(SqlOperationType.INSERT,
                sqlParserService.parseSql("INSERT INTO cart VALUES (?)").getOperationType());
        assertEquals(SqlOperationType.UPDATE,
                sqlParserService.parseSql("UPDATE cart SET name = ?").getOperationType());
        assertEquals(SqlOperationType.DELETE,
                sqlParserService.parseSql("DELETE FROM cart").getOperationType());
    }

    @Test
    void testParseWithSchemaQualifiedTable() {
        String sql = "SELECT * FROM public.cart_item WHERE cart_id = ?";
        SqlParserService.ParsedSql result = sqlParserService.parseSql(sql);

        assertTrue(result.isValid());
        assertEquals("cart_item", result.getTableName(), "Should extract table name without schema");
    }

    @Test
    void testParseComplexWhereClause() {
        String sql = "SELECT * FROM cart WHERE cart_id = ? AND status = ? OR created_at > ?";
        SqlParserService.ParsedSql result = sqlParserService.parseSql(sql);

        assertTrue(result.isValid());
        assertTrue(result.getWhereColumns().contains("cart_id"));
        assertTrue(result.getWhereColumns().contains("status"));
        assertTrue(result.getWhereColumns().contains("created_at"));
    }
}

