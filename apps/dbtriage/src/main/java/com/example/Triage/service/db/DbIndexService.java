package com.example.Triage.service.db;

import com.example.Triage.dao.DbQueries;
import com.example.Triage.model.dto.DbConnectContextDto;
import com.example.Triage.model.dto.DbIndex;
import com.example.Triage.model.enums.ValidationStatus;
import com.example.Triage.model.response.DbIndexResponse;
import com.example.Triage.model.response.IndexCoverageResponse;
import com.example.Triage.util.IndexMatchers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DbIndexService {

        private final DbDataSourceFactory dataSourceFactory;

        public DbIndexResponse listIndexes(DbConnectContextDto ctx, String schema, String table) throws SQLException {
                var ds = dataSourceFactory.build(ctx);

                List<DbIndex> indexes = new ArrayList<>();
                try (Connection c = ds.getConnection();
                                PreparedStatement ps = c.prepareStatement(DbQueries.GET_INDEXES)) {

                        ps.setString(1, schema);
                        ps.setString(2, table);

                        try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) {
                                        String indexName = rs.getString("index_name");
                                        boolean unique = rs.getBoolean("is_unique");
                                        boolean primary = rs.getBoolean("is_primary");
                                        String method = rs.getString("access_method");
                                        String def = rs.getString("index_def");

                                        List<String> cols = new ArrayList<>();
                                        Array arr = rs.getArray("columns_arr");
                                        if (arr != null) {
                                                String[] raw = (String[]) arr.getArray();
                                                if (raw != null)
                                                        cols.addAll(List.of(raw));
                                        }

                                        // IMPORTANT: choose ONE IndexInfo style. Here: builder-based.
                                        indexes.add(DbIndex.builder()
                                                        .name(indexName)
                                                        .unique(unique)
                                                        .primary(primary)
                                                        .accessMethod(method)
                                                        .columns(cols)
                                                        .definition(def)
                                                        .build());
                                }
                        }
                }

                return DbIndexResponse.builder().schema(schema).table(table).indexes(indexes).build();
        }

        public IndexCoverageResponse indexCoverage(DbConnectContextDto ctx, String schema, String table,
                        List<String> columns, boolean requireUnique) throws SQLException {

                var inv = listIndexes(ctx, schema, table);
                var wanted = columns.stream().map(s -> s == null ? "" : s.toLowerCase()).toList();

                var exact = inv.indexes().stream()
                                .filter(ix -> IndexMatchers.sameSet(
                                                ix.columns().stream().map(String::toLowerCase).toList(), wanted))
                                .toList();

                var exactUnique = exact.stream().filter(DbIndex::unique).toList();

                if (requireUnique) {
                        if (!exactUnique.isEmpty()) {
                                return new IndexCoverageResponse(schema, table, columns, true,
                                                ValidationStatus.PASS,
                                                "Unique index/constraint exists for conflict target; ON CONFLICT should be valid.",
                                                exactUnique.stream().map(DbIndex::name).toList(),
                                                List.of());
                        }

                        if (!exact.isEmpty()) {
                                return new IndexCoverageResponse(schema, table, columns, true,
                                                ValidationStatus.FAIL,
                                                "Matching index exists but is not unique. ON CONFLICT requires a UNIQUE index/constraint on the conflict target.",
                                                exact.stream().map(DbIndex::name).toList(),
                                                List.of("Create a UNIQUE index/constraint on ("
                                                                + String.join(", ", columns) + ")."));
                        }

                        var prefix = inv.indexes().stream()
                                        .filter(ix -> IndexMatchers.startsWithPrefix(ix.columns(), wanted))
                                        .map(DbIndex::name)
                                        .toList();

                        return new IndexCoverageResponse(schema, table, columns, true,
                                        ValidationStatus.FAIL,
                                        "No UNIQUE index/constraint found for conflict target; ON CONFLICT will fail at runtime.",
                                        prefix,
                                        List.of("Add UNIQUE index/constraint on (" + String.join(", ", columns)
                                                        + ")."));
                }

                var coversAll = inv.indexes().stream()
                                .filter(ix -> IndexMatchers.containsAllIgnoreOrder(ix.columns(), wanted))
                                .map(DbIndex::name)
                                .toList();

                if (!coversAll.isEmpty()) {
                        return new IndexCoverageResponse(schema, table, columns, false,
                                        ValidationStatus.PASS,
                                        "Index likely exists to support filtering on these columns.",
                                        coversAll,
                                        List.of());
                }

                var prefix = inv.indexes().stream()
                                .filter(ix -> IndexMatchers.startsWithPrefix(ix.columns(), wanted))
                                .map(DbIndex::name)
                                .toList();

                if (!prefix.isEmpty()) {
                        return new IndexCoverageResponse(schema, table, columns, false,
                                        ValidationStatus.WARNING,
                                        "Partial index coverage found (prefix match). Consider composite index for hot paths.",
                                        prefix,
                                        List.of("Consider composite index on (" + String.join(", ", columns) + ")."));
                }

                return new IndexCoverageResponse(schema, table, columns, false,
                                ValidationStatus.FAIL,
                                "No relevant index found for these columns.",
                                List.of(),
                                List.of("Consider adding an index on (" + String.join(", ", columns) + ")."));
        }
}
