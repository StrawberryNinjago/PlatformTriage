package com.example.Triage.dao;

public class RoleQueries {
    public static final String GET_ROLE_POSTURE = """
            SELECT
              current_user AS current_user,
              r.rolsuper AS is_superuser,
              r.rolcreaterole AS can_create_role,
              r.rolcreatedb AS can_create_db,
              r.rolreplication AS can_replicate,
              r.rolbypassrls AS can_bypass_rls
            FROM pg_roles r
            WHERE r.rolname = current_user
            """;

    public static final String GET_MEMBER_OF_ROLES = """
            SELECT r.rolname AS role_name
            FROM pg_auth_members m
            JOIN pg_roles r ON r.oid = m.roleid
            JOIN pg_roles u ON u.oid = m.member
            WHERE u.rolname = current_user
            ORDER BY r.rolname
            """;

    public static final String GET_SCHEMA_PRIVILEGES = """
            SELECT
              has_schema_privilege(current_user, ?, 'USAGE')  AS can_usage,
              has_schema_privilege(current_user, ?, 'CREATE') AS can_create
            """;

    public static final String GET_TABLE_PRIVILEGES = """
            WITH objs AS (
              SELECT unnest(?) AS obj_name  -- pass text[] like {"public.cart","public.item"}
            )
            SELECT
              obj_name,
              has_table_privilege(current_user, obj_name, 'SELECT') AS can_select
            FROM objs
            ORDER BY obj_name
            """;

    public static final String GET_INSERT_UPDATE_CHECK = """
            SELECT
              has_table_privilege(current_user, ?, 'INSERT') AS can_insert,
              has_table_privilege(current_user, ?, 'UPDATE') AS can_update
            """;

    public static final String GET_FLYWAY_MIGRATION_EXECUTION = """
            SELECT has_function_privilege(current_user, 'public.flyway_migrate', 'EXECUTE') AS can_execute_flyway;
            """;
}
