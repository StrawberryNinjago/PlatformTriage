package com.example.Triage.service.db.preivilege;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.example.Triage.dao.RoleQueries;
import com.example.Triage.model.dto.DbConnectContextDto;
import com.example.Triage.model.dto.DbPrivilegeSummaryDto;
import com.example.Triage.model.dto.ObjectPrivilegeCheckDto;
import com.example.Triage.model.dto.RolePostureDto;
import com.example.Triage.model.dto.SchemaPrivilegesDto;
import com.example.Triage.model.dto.WarningMessageDto;
import com.example.Triage.service.db.util.DataSourceUtils;

import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class DbPrivilegesService {

    private static final List<String> DEFAULT_TABLES_TO_CHECK = List.of(
            "public.flyway_schema_history",
            "public.cart",
            "public.line_of_service",
            "public.item");

    public DbPrivilegeSummaryDto getPrivileges(DbConnectContextDto ctx) throws SQLException {
        log.info("#getPrivileges: Checking privileges for connectionId={}", ctx.id());

        try (var conn = DataSourceUtils.buildDataSource(ctx).getConnection()) {

            var rolePosture = queryRolePosture(conn);
            var memberRoles = queryMemberOfRoles(conn);

            String schema = normalizeSchema(ctx.schema());
            var schemaPrivileges = querySchemaPrivileges(conn, schema);

            var objectChecks = querySelectPrivileges(conn, DEFAULT_TABLES_TO_CHECK);

            var warnings = deriveWarnings(rolePosture, schemaPrivileges, objectChecks);

            return DbPrivilegeSummaryDto.builder()
                    .rolePosture(rolePosture)
                    .schemaPrivileges(schemaPrivileges)
                    .objectChecks(objectChecks)
                    .memberOfRoles(memberRoles)
                    .warnings(warnings)
                    .build();
        }
    }

    private String normalizeSchema(String schema) {
        return (schema == null || schema.isBlank()) ? "public" : schema;
    }

    private RolePostureDto queryRolePosture(Connection conn) throws SQLException {
        try (var ps = conn.prepareStatement(RoleQueries.GET_ROLE_POSTURE);
                var rs = ps.executeQuery()) {

            if (!rs.next())
                return null;

            return RolePostureDto.builder()
                    .currentUser(rs.getString("current_user"))
                    .isSuperuser(rs.getBoolean("is_superuser"))
                    .canCreateRole(rs.getBoolean("can_create_role"))
                    .canCreateDb(rs.getBoolean("can_create_db"))
                    .canReplicate(rs.getBoolean("can_replicate"))
                    .canBypassRls(rs.getBoolean("can_bypass_rls"))
                    .build();
        }
    }

    private List<String> queryMemberOfRoles(Connection conn) throws SQLException {
        try (var ps = conn.prepareStatement(RoleQueries.GET_MEMBER_OF_ROLES);
                var rs = ps.executeQuery()) {

            List<String> roles = new ArrayList<>();
            while (rs.next()) {
                roles.add(rs.getString("role_name"));
            }
            return roles;
        }
    }

    private SchemaPrivilegesDto querySchemaPrivileges(Connection conn, String schema) throws SQLException {
        try (var ps = conn.prepareStatement(RoleQueries.GET_SCHEMA_PRIVILEGES)) {
            ps.setString(1, schema);
            ps.setString(2, schema);

            try (var rs = ps.executeQuery()) {
                rs.next();
                return SchemaPrivilegesDto.builder()
                        .schema(schema)
                        .canUsage(rs.getBoolean("can_usage"))
                        .canCreate(rs.getBoolean("can_create"))
                        .build();
            }
        }
    }

    private List<ObjectPrivilegeCheckDto> querySelectPrivileges(Connection conn, List<String> objects)
            throws SQLException {
        try (var ps = conn.prepareStatement(RoleQueries.GET_TABLE_PRIVILEGES)) {

            var arr = conn.createArrayOf("text", objects.toArray(new String[0]));
            ps.setArray(1, arr);

            try (var rs = ps.executeQuery()) {
                List<ObjectPrivilegeCheckDto> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(ObjectPrivilegeCheckDto.builder()
                            .objectName(rs.getString("obj_name"))
                            .privilege("SELECT")
                            .allowed(rs.getBoolean("can_select"))
                            .build());
                }
                return out;
            }
        }
    }

    private List<WarningMessageDto> deriveWarnings(
            RolePostureDto rolePosture,
            SchemaPrivilegesDto schemaPrivileges,
            List<ObjectPrivilegeCheckDto> checks) {
        List<WarningMessageDto> warnings = new ArrayList<>();

        if (schemaPrivileges != null && !schemaPrivileges.canUsage()) {
            warnings.add(WarningMessageDto.builder()
                    .code("MISSING_SCHEMA_USAGE")
                    .message("Connected user lacks USAGE on schema '" + schemaPrivileges.schema() + "'.")
                    .build());
        }

        // Consider only missing SELECT warnings (avoid spamming)
        for (var c : checks) {
            if (!c.allowed()) {
                warnings.add(WarningMessageDto.builder()
                        .code("MISSING_SELECT")
                        .message("Missing SELECT on " + c.objectName() + ".")
                        .build());
            }
        }

        if (rolePosture != null && rolePosture.isSuperuser()) {
            warnings.add(WarningMessageDto.builder()
                    .code("HIGH_PRIVILEGE_USER")
                    .message("Connected user is superuser. Be cautious when using this credential.")
                    .build());
        }

        return warnings;
    }
}
