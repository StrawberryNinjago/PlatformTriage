package com.example.Triage.service.db;

import com.example.Triage.model.dto.CapabilityStatus;
import com.example.Triage.model.dto.DbConnectContextDto;
import com.example.Triage.model.dto.EnvironmentCapabilityMatrix;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Service to check what capabilities are available for a given database connection.
 * This is critical for graceful degradation in environment comparison.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnvironmentCapabilityService {

    private final DbDataSourceFactory dataSourceFactory;

    /**
     * Build a complete capability matrix for a connection
     */
    public EnvironmentCapabilityMatrix buildCapabilityMatrix(
            String environmentName,
            String connectionId,
            DbConnectContextDto ctx) {
        
        DataSource ds = dataSourceFactory.build(ctx);

        return new EnvironmentCapabilityMatrix(
                environmentName,
                connectionId,
                checkConnect(ds),
                checkIdentity(ds),
                checkTables(ds, ctx.schema()),
                checkColumns(ds, ctx.schema()),
                checkConstraints(ds, ctx.schema()),
                checkIndexes(ds, ctx.schema()),
                checkFlywayHistory(ds),
                checkGrants(ds, ctx.schema())
        );
    }

    private CapabilityStatus checkConnect(DataSource ds) {
        try (Connection conn = ds.getConnection()) {
            return CapabilityStatus.createAvailable();
        } catch (Exception e) {
            log.warn("Connect capability check failed: {}", e.getMessage());
            return CapabilityStatus.createUnavailable(
                    "Cannot establish connection",
                    "CONNECT_FAILED"
            );
        }
    }

    private CapabilityStatus checkIdentity(DataSource ds) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT current_user, current_database()");
             ResultSet rs = ps.executeQuery()) {
            
            if (rs.next()) {
                return CapabilityStatus.createAvailable();
            }
            return CapabilityStatus.createUnavailable("Cannot read identity", "IDENTITY_READ_FAILED");
        } catch (Exception e) {
            log.warn("Identity capability check failed: {}", e.getMessage());
            return CapabilityStatus.createUnavailable(
                    "Permission denied reading identity",
                    "IDENTITY_PERMISSION_DENIED"
            );
        }
    }

    private CapabilityStatus checkTables(DataSource ds, String schema) {
        String sql = """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = ?
                LIMIT 1
                """;
        
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                return CapabilityStatus.createAvailable();
            }
        } catch (Exception e) {
            log.warn("Tables capability check failed: {}", e.getMessage());
            return CapabilityStatus.createUnavailable(
                    "Cannot read table metadata from information_schema",
                    "TABLES_READ_FAILED"
            );
        }
    }

    private CapabilityStatus checkColumns(DataSource ds, String schema) {
        String sql = """
                SELECT column_name, data_type
                FROM information_schema.columns
                WHERE table_schema = ?
                LIMIT 1
                """;
        
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                return CapabilityStatus.createAvailable();
            }
        } catch (Exception e) {
            log.warn("Columns capability check failed: {}", e.getMessage());
            return CapabilityStatus.createUnavailable(
                    "Cannot read column metadata from information_schema",
                    "COLUMNS_READ_FAILED"
            );
        }
    }

    private CapabilityStatus checkConstraints(DataSource ds, String schema) {
        String sql = """
                SELECT constraint_name, constraint_type
                FROM information_schema.table_constraints
                WHERE table_schema = ?
                LIMIT 1
                """;
        
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                return CapabilityStatus.createAvailable();
            }
        } catch (Exception e) {
            log.warn("Constraints capability check failed: {}", e.getMessage());
            return CapabilityStatus.createUnavailable(
                    "Cannot read constraint metadata from information_schema",
                    "CONSTRAINTS_READ_FAILED"
            );
        }
    }

    private CapabilityStatus checkIndexes(DataSource ds, String schema) {
        String sql = """
                SELECT indexname
                FROM pg_catalog.pg_indexes
                WHERE schemaname = ?
                LIMIT 1
                """;
        
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                return CapabilityStatus.createAvailable();
            }
        } catch (Exception e) {
            log.warn("Indexes capability check failed: {}", e.getMessage());
            return CapabilityStatus.createUnavailable(
                    "Cannot read index metadata from pg_catalog",
                    "INDEXES_READ_FAILED"
            );
        }
    }

    private CapabilityStatus checkFlywayHistory(DataSource ds) {
        String sql = """
                SELECT version
                FROM flyway_schema_history
                LIMIT 1
                """;
        
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return CapabilityStatus.createAvailable();
        } catch (Exception e) {
            log.warn("Flyway history capability check failed: {}", e.getMessage());
            return CapabilityStatus.createUnavailable(
                    "Cannot read flyway_schema_history table",
                    "FLYWAY_READ_FAILED"
            );
        }
    }

    private CapabilityStatus checkGrants(DataSource ds, String schema) {
        String sql = """
                SELECT grantee, privilege_type
                FROM information_schema.table_privileges
                WHERE table_schema = ?
                LIMIT 1
                """;
        
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                return CapabilityStatus.createAvailable();
            }
        } catch (Exception e) {
            log.warn("Grants capability check failed: {}", e.getMessage());
            return CapabilityStatus.createUnavailable(
                    "Cannot read grant metadata from information_schema",
                    "GRANTS_READ_FAILED"
            );
        }
    }
}

