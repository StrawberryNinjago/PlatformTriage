package com.example.Triage.core;

import com.example.Triage.model.response.DbPrivilegesResponse;
import com.example.Triage.model.response.DbPrivilegesResponse.ValidationStatus;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class DbPrivilegesService {

    private static final List<String> EXPECTED_PRIVILEGES = Arrays.asList(
            "SELECT", "INSERT", "UPDATE", "DELETE"
    );

    public DbPrivilegesResponse checkPrivileges(DbConnectContext ctx, String schema, String table) 
            throws SQLException {
        DataSource ds = buildDataSource(ctx);

        try (Connection c = ds.getConnection()) {
            // Get table owner
            String owner = getTableOwner(c, schema, table);
            if (owner == null) {
                return new DbPrivilegesResponse(
                    schema, table, ValidationStatus.FAIL, 
                    null, getCurrentUser(c), List.of(), EXPECTED_PRIVILEGES,
                    String.format("Table %s.%s not found", schema, table)
                );
            }

            // Get current user
            String currentUser = getCurrentUser(c);

            // Get granted privileges
            List<String> grantedPrivileges = getGrantedPrivileges(c, schema, table, currentUser);

            // Check for missing privileges
            List<String> missingPrivileges = new ArrayList<>();
            for (String expected : EXPECTED_PRIVILEGES) {
                if (!grantedPrivileges.contains(expected)) {
                    missingPrivileges.add(expected);
                }
            }

            // Determine status
            ValidationStatus status;
            String message;

            if (missingPrivileges.isEmpty()) {
                status = ValidationStatus.PASS;
                message = String.format("User '%s' has all expected privileges on %s.%s", 
                    currentUser, schema, table);
            } else if (grantedPrivileges.isEmpty()) {
                status = ValidationStatus.FAIL;
                message = String.format("User '%s' has NO privileges on %s.%s", 
                    currentUser, schema, table);
            } else {
                status = ValidationStatus.WARNING;
                message = String.format("User '%s' is missing %d privilege(s): %s", 
                    currentUser, missingPrivileges.size(), String.join(", ", missingPrivileges));
            }

            return new DbPrivilegesResponse(
                schema, table, status, owner, currentUser, 
                grantedPrivileges, missingPrivileges, message
            );
        }
    }

    private String getTableOwner(Connection c, String schema, String table) throws SQLException {
        String sql = "SELECT tableowner FROM pg_tables WHERE schemaname = ? AND tablename = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("tableowner");
                }
                return null;
            }
        }
    }

    private String getCurrentUser(Connection c) throws SQLException {
        String sql = "SELECT current_user";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getString(1);
        }
    }

    private List<String> getGrantedPrivileges(Connection c, String schema, String table, String user) 
            throws SQLException {
        String sql = """
                SELECT privilege_type 
                FROM information_schema.table_privileges 
                WHERE table_schema = ? 
                  AND table_name = ? 
                  AND grantee IN (?, 'PUBLIC')
                """;
        
        List<String> privileges = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            ps.setString(3, user);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    privileges.add(rs.getString("privilege_type"));
                }
            }
        }
        return privileges;
    }

    private DataSource buildDataSource(DbConnectContext ctx) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        String sslMode = (ctx.sslMode() == null || ctx.sslMode().isBlank()) ? "require" : ctx.sslMode();

        String url = String.format(
                "jdbc:postgresql://%s:%d/%s?sslmode=%s",
                ctx.host(), ctx.port(), ctx.database(), sslMode);

        ds.setUrl(url);
        ds.setUser(ctx.username());
        ds.setPassword(ctx.password());
        ds.setConnectTimeout(5);
        ds.setLoginTimeout(5);
        return ds;
    }
}

