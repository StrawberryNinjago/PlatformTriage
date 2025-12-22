package com.example.Triage.core;

import com.example.Triage.core.DbConnectContext;
import com.example.Triage.model.response.DbIndexResponse;
import com.example.Triage.model.response.DbConstraintsResponse;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import lombok.RequiredArgsConstructor;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DbIndexService {
    private final DbConnectionRegistry registry;

    public DbConstraintsResponse listConstraints(DbConnectContext ctx, String schema, String table)
            throws java.sql.SQLException {
        var ds = buildDataSource(ctx);

        try (var conn = ds.getConnection()) {
            String sql = """
                    select
                      c.conname as constraint_name,
                      c.contype as contype,
                      pg_get_constraintdef(c.oid, true) as definition,
                      array_remove(array_agg(a.attname order by u.ord), null) as columns
                    from pg_constraint c
                    join pg_class t on t.oid = c.conrelid
                    join pg_namespace ns on ns.oid = t.relnamespace
                    left join lateral unnest(c.conkey) with ordinality as u(attnum, ord) on true
                    left join pg_attribute a on a.attrelid = t.oid and a.attnum = u.attnum
                    where ns.nspname = ?
                      and t.relname = ?
                    group by c.conname, c.contype, c.oid
                    order by c.conname
                    """;

            java.util.List<DbConstraintsResponse.DbConstraint> out = new java.util.ArrayList<>();

            try (var ps = conn.prepareStatement(sql)) {
                ps.setString(1, schema);
                ps.setString(2, table);

                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString("constraint_name");
                        String contype = rs.getString("contype"); // one-char
                        String type = mapConstraintType(contype);

                        String def = rs.getString("definition");

                        java.sql.Array arr = rs.getArray("columns");
                        java.util.List<String> cols = new java.util.ArrayList<>();
                        if (arr != null) {
                            String[] raw = (String[]) arr.getArray();
                            if (raw != null) {
                                for (String s : raw)
                                    if (s != null)
                                        cols.add(s);
                            }
                        }

                        out.add(new DbConstraintsResponse.DbConstraint(name, type, cols, def));
                    }
                }
            }

            return new DbConstraintsResponse(schema, table, out);
        }
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
