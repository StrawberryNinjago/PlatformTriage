package com.example.Triage.handler;

import org.springframework.stereotype.Component;

import com.example.Triage.exception.ConnectionNotFoundException;
import com.example.Triage.model.response.DbIndexResponse;
import com.example.Triage.model.response.DbTableIntrospectResponse;
import com.example.Triage.model.response.DbTableSearchResponse;
import com.example.Triage.model.response.DbTablesResponse;
import com.example.Triage.service.db.DbConnectionRegistry;
import com.example.Triage.service.db.DbIndexService;
import com.example.Triage.service.db.DbIntrospectService;
import com.example.Triage.service.db.DbTablesService;
import com.example.Triage.util.DbConnectionUtils;

import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class DbTablesHandler {

    private final DbConnectionRegistry registry;
    private final DbTablesService tablesService;
    private final DbIndexService indexService;
    private final DbIntrospectService introspectService;

    public DbTablesResponse listTables(
            String connectionId,
            String schema) throws Exception {
        log.info("#listTables: Listing tables for connectionId: {}, schema: {}", connectionId, schema);
        var ctx = DbConnectionUtils.getCtx(registry, connectionId);

        try {
            var resp = tablesService.listTables(ctx, schema);
            log.info("#listTables: Tables listed successfully for connectionId: {}, schema: {}", connectionId, schema);
            return resp;
        } catch (Exception e) {
            log.error("#listTables: Tables listing failed for connectionId: {}, schema: {}", connectionId, schema, e);
            throw new ConnectionNotFoundException("LIST_TABLES_FAILED.");
        }
    }

    public DbTableSearchResponse searchTables(
            String connectionId, String schema,
            String queryString) throws Exception {
        log.info("#searchTables: Searching tables for connectionId: {}, schema: {}, queryString: {}", connectionId,
                schema, queryString);
        var ctx = DbConnectionUtils.getCtx(registry, connectionId);

        try {
            var resp = tablesService.searchTables(ctx, schema, queryString);
            log.info("#searchTables: Tables searched successfully for connectionId: {}, schema: {}, queryString: {}",
                    connectionId, schema, queryString);
            return resp;
        } catch (Exception e) {
            log.error("#searchTables: Tables searching failed for connectionId: {}, schema: {}, queryString: {}",
                    connectionId, schema, queryString, e);
            throw new ConnectionNotFoundException("SEARCH_TABLES_FAILED.");
        }
    }

    public DbIndexResponse listIndexes(
            String connectionId,
            String schema,
            String table) throws Exception {
        log.info("#listIndexes: Listing indexes for connectionId: {}, schema: {}, table: {}", connectionId, schema,
                table);
        var ctx = DbConnectionUtils.getCtx(registry, connectionId);

        try {
            var resp = indexService.listIndexes(ctx, schema, table);
            log.info("#listIndexes: Indexes listed successfully for connectionId: {}, schema: {}, table: {}",
                    connectionId, schema, table);
            return resp;
        } catch (Exception e) {
            log.error("#listIndexes: Indexes listing failed for connectionId: {}, schema: {}, table: {}", connectionId,
                    schema, table, e);
            throw new ConnectionNotFoundException("LIST_INDEXES_FAILED.");
        }
    }

    public DbTableIntrospectResponse introspectTable(
            String connectionId,
            String schema,
            String table) throws Exception {
        log.info("#introspectTable: Introspecting table for connectionId: {}, schema: {}, table: {}", connectionId,
                schema, table);
        var ctx = DbConnectionUtils.getCtx(registry, connectionId);

        try {
            var resp = introspectService.introspectTable(ctx, schema, table);
            log.info("#introspectTable: Table introspected successfully for connectionId: {}, schema: {}, table: {}",
                    connectionId, schema, table);
            return resp;
        } catch (Exception e) {
            log.error("#introspectTable: Table introspection failed for connectionId: {}, schema: {}, table: {}",
                    connectionId, schema, table, e);
            throw new ConnectionNotFoundException("TABLE_INTROSPECT_FAILED.");
        }
    }
}
