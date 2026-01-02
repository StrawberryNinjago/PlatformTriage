package com.example.Triage.service.db.preivilege;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.Triage.dao.DbQueries;
import com.example.Triage.model.dto.DbConnectContextDto;
import com.example.Triage.model.enums.ValidationStatus;
import com.example.Triage.model.response.DbPrivilegesResponse;
import com.example.Triage.service.db.util.DataSourceUtils;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;

@Service
@Slf4j
public class DbTablePrivilegesService {

    private static final List<String> EXPECTED_PRIVILEGES = List.of("SELECT", "INSERT", "UPDATE", "DELETE");

    public DbPrivilegesResponse checkTablePrivileges(DbConnectContextDto ctx, String schema, String table)
            throws SQLException {

        try (var conn = DataSourceUtils.buildDataSource(ctx).getConnection()) {

            String owner = queryTableOwner(conn, schema, table);
            if (owner == null) {
                return new DbPrivilegesResponse(
                        schema, table, ValidationStatus.FAIL,
                        null, queryCurrentUser(conn), List.of(), EXPECTED_PRIVILEGES,
                        String.format("Table %s.%s not found", schema, table));
            }

            String currentUser = queryCurrentUser(conn);
            List<String> granted = queryGrantedPrivileges(conn, schema, table, currentUser);

            List<String> missing = EXPECTED_PRIVILEGES.stream()
                    .filter(p -> !granted.contains(p))
                    .toList();

            var status = determineStatus(granted, missing);
            var message = buildMessage(status, currentUser, schema, table, missing);

            return new DbPrivilegesResponse(schema, table, status, owner, currentUser, granted, missing, message);
        }
    }

    private ValidationStatus determineStatus(List<String> granted, List<String> missing) {
        if (missing.isEmpty())
            return ValidationStatus.PASS;
        if (granted.isEmpty())
            return ValidationStatus.FAIL;
        return ValidationStatus.WARNING;
    }

    private String buildMessage(ValidationStatus status, String user, String schema, String table,
            List<String> missing) {
        return switch (status) {
            case PASS -> String.format("User '%s' has all expected privileges on %s.%s", user, schema, table);
            case FAIL -> String.format("User '%s' has NO privileges on %s.%s", user, schema, table);
            case WARNING -> String.format("User '%s' is missing %d privilege(s): %s",
                    user, missing.size(), String.join(", ", missing));
        };
    }

    private String queryTableOwner(Connection conn, String schema, String table) throws SQLException {
        try (var ps = conn.prepareStatement(DbQueries.GET_TABLE_OWNER)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("tableowner") : null;
            }
        }
    }

    private String queryCurrentUser(Connection conn) throws SQLException {
        try (var ps = conn.prepareStatement(DbQueries.GET_CURRENT_USER);
                var rs = ps.executeQuery()) {
            rs.next();
            return rs.getString(1);
        }
    }

    private List<String> queryGrantedPrivileges(Connection conn, String schema, String table, String user)
            throws SQLException {
        try (var ps = conn.prepareStatement(DbQueries.GET_GRANTED_PRIVILEGES)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            ps.setString(3, user);
            try (var rs = ps.executeQuery()) {
                List<String> out = new ArrayList<>();
                while (rs.next())
                    out.add(rs.getString("privilege_type"));
                return out;
            }
        }
    }
}
