package com.example.Triage.service.db;

import com.example.Triage.dao.DbQueries;
import com.example.Triage.model.dto.DbConnectContextDto;
import com.example.Triage.model.dto.DbConstraint;
import com.example.Triage.model.response.DbConstraintsResponse;
import com.example.Triage.model.response.DbTableIntrospectResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DbIntrospectService {

    private final DbDataSourceFactory dataSourceFactory;
    private final DbIndexService indexService;

    public DbConstraintsResponse listConstraints(DbConnectContextDto ctx, String schema, String table)
            throws SQLException {
        DataSource ds = dataSourceFactory.build(ctx);
        try (var conn = ds.getConnection()) {
            var constraints = queryConstraints(conn, schema, table);
            List<DbConstraint> converted = new ArrayList<>();
            for (var c : constraints) {
                converted.add(new DbConstraint(table, c.name(), c.type(), c.columns(), c.definition()));
            }
            return new DbConstraintsResponse(schema, table, converted);
        }
    }

    public DbTableIntrospectResponse introspectTable(DbConnectContextDto ctx, String schema, String table)
            throws SQLException {
        DataSource ds = dataSourceFactory.build(ctx);
        try (var conn = ds.getConnection()) {
            var indexes = indexService.listIndexes(ctx, schema, table).indexes();
            var constraints = queryConstraints(conn, schema, table);
            return new DbTableIntrospectResponse(schema, table, indexes, constraints);
        }
    }

    private List<DbConstraint> queryConstraints(Connection conn, String schema, String table)
            throws SQLException {

        List<DbConstraint> out = new ArrayList<>();

        try (var ps = conn.prepareStatement(DbQueries.GET_CONSTRAINTS)) {
            ps.setString(1, schema);
            ps.setString(2, table);

            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("constraint_name");
                    String contype = rs.getString("contype");
                    String type = mapConstraintType(contype);
                    String definition = rs.getString("definition");

                    List<String> cols = new ArrayList<>();
                    var arr = rs.getArray("columns");
                    if (arr != null) {
                        String[] raw = (String[]) arr.getArray();
                        if (raw != null) {
                            for (String s : raw)
                                if (s != null)
                                    cols.add(s);
                        }
                    }

                    out.add(new DbConstraint(table, name, type, cols, definition));
                }
            }
        }
        return out;
    }

    private String mapConstraintType(String contype) {
        if (contype == null)
            return "UNKNOWN";
        return switch (contype) {
            case "p" -> "PRIMARY KEY";
            case "u" -> "UNIQUE";
            case "f" -> "FOREIGN KEY";
            case "c" -> "CHECK";
            case "x" -> "EXCLUSION";
            default -> "OTHER(" + contype + ")";
        };
    }
}
