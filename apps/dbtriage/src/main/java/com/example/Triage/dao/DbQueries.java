package com.example.Triage.dao;

public class DbQueries {
    public static final String GET_CONNECTION_CONTEXT = """
            SELECT
              current_database()  AS db,
              current_user        AS curr_user,
              session_user        AS sess_user,
              COALESCE(host(inet_server_addr()), 'localhost') AS server_addr,
              COALESCE(inet_server_port(), 5432) AS server_port,
              version()           AS server_version,
              now()               AS server_time
            """;

    public static final String GET_LATEST_APPLIED = """
            SELECT installed_rank, version, description, script, installed_on
            FROM public.flyway_schema_history
            WHERE success = true
            ORDER BY installed_rank DESC
            LIMIT 1
            """;

    public static final String GET_FLYWAY_FAILED_COUNT = """
            SELECT count(*) AS cnt FROM public.flyway_schema_history WHERE success = false
            """;

    public static final String GET_INDEXES = """
            SELECT
              i.relname AS index_name,
              ix.indisunique AS is_unique,
              ix.indisprimary AS is_primary,
              am.amname AS access_method,
              pg_get_indexdef(ix.indexrelid) AS index_def,
              COALESCE((
                SELECT array_agg(a.attname ORDER BY x.ord)
                FROM unnest(ix.indkey) WITH ORDINALITY AS x(attnum, ord)
                JOIN pg_attribute a
                  ON a.attrelid = t.oid AND a.attnum = x.attnum
                WHERE x.attnum > 0
              ), ARRAY[]::text[]) AS columns_arr
            FROM pg_class t
            JOIN pg_namespace n ON n.oid = t.relnamespace
            JOIN pg_index ix ON ix.indrelid = t.oid
            JOIN pg_class i ON i.oid = ix.indexrelid
            JOIN pg_am am ON am.oid = i.relam
            WHERE n.nspname = ?
              AND t.relname = ?
            ORDER BY i.relname
            """;

    public static final String GET_CONSTRAINTS = """
            SELECT
              c.conname AS constraint_name,
              c.contype AS contype,
              pg_get_constraintdef(c.oid, true) AS definition,
              array_remove(array_agg(a.attname ORDER BY u.ord), null) AS columns
            FROM pg_constraint c
            JOIN pg_class t on t.oid = c.conrelid
            JOIN pg_namespace ns on ns.oid = t.relnamespace
            LEFT JOIN lateral unnest(c.conkey) with ordinality AS u(attnum, ord) on true
            LEFT JOIN pg_attribute a on a.attrelid = t.oid and a.attnum = u.attnum
            WHERE ns.nspname = ?
              and t.relname = ?
            GROUP BY c.conname, c.contype, c.oid
            ORDER BY c.conname
            """;

    public static final String GET_GRANTED_PRIVILEGES = """
            SELECT privilege_type
            FROM information_schema.table_privileges
            WHERE table_schema = ?
              AND table_name = ?
              AND grantee IN (?, 'PUBLIC')
            """;

    public static final String GET_TABLES = """
            SELECT
              c.relname AS table_name,
              COALESCE(c.reltuples::bigint, 0) AS estimated_row_count,
              r.rolname AS table_owner
            FROM pg_catalog.pg_class c
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            JOIN pg_catalog.pg_roles r ON r.oid = c.relowner
            WHERE n.nspname = ?
              AND c.relkind = 'r'
              AND c.relname NOT LIKE 'pg_%'
              AND c.relname NOT LIKE 'sql_%'
            ORDER BY c.relname
            """;

    public static final String GET_TABLES_BY_NAME = """
            SELECT
              t.table_name,
              COALESCE(pg_class.reltuples::bigint, 0) AS estimated_row_count,
              pg_tables.tableowner
            FROM information_schema.tables t
            LEFT JOIN pg_catalog.pg_class ON pg_class.relname = t.table_name
            LEFT JOIN pg_catalog.pg_tables ON pg_tables.schemaname = t.table_schema
                AND pg_tables.tablename = t.table_name
            WHERE t.table_schema = ?
              AND t.table_type = 'BASE TABLE'
              AND t.table_name NOT LIKE 'pg_%'
              AND t.table_name NOT LIKE 'sql_%'
              AND t.table_schema != 'information_schema'
              AND t.table_name ILIKE ?
            ORDER BY t.table_name
            """;

    public static final String GET_PUBLIC_TABLE_COUNT = """
            SELECT count(*) AS cnt
            FROM information_schema.tables
            WHERE table_schema = 'public'
              and table_type = 'BASE TABLE'
            """;

    public static final String IS_TABLE_EXIST = """
            SELECT exists(
              SELECT 1
              FROM information_schema.tables
              WHERE table_schema = 'public'
                and table_name = ?
            ) AS present
            """;

    public static final String GET_IDENTITY = """
            SELECT
              current_database()  AS db,
              current_user        AS usr,
              inet_server_addr()  AS server_addr,
              inet_server_port()  AS server_port,
              version()           AS server_version,
              now()               AS server_time
            """;

    public static final String GET_FLYWAY_HISTORY = """
            SELECT to_regclass('public.flyway_schema_history') AS flyway_table
            """;

    public static final String GET_TABLE_OWNER = """
            SELECT tableowner
            FROM pg_tables
            WHERE schemaname = :schema AND tablename = :table
            """;

    public static final String GET_CURRENT_USER = """
            SELECT current_user
            """;

}
