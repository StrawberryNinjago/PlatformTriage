package com.example.Triage.service.db;

import lombok.RequiredArgsConstructor;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.stereotype.Component;

import com.example.Triage.model.dto.DbConnectContext;

import javax.sql.DataSource;

@Component
@RequiredArgsConstructor
public class DbDataSourceFactory {

    public DataSource build(DbConnectContext ctx) {
        PGSimpleDataSource ds = new PGSimpleDataSource();

        String sslMode = (ctx.sslMode() == null || ctx.sslMode().isBlank())
                ? "require"
                : ctx.sslMode().trim();

        String url = String.format(
                "jdbc:postgresql://%s:%d/%s?sslmode=%s",
                ctx.host(), ctx.port(), ctx.database(), sslMode);

        ds.setUrl(url);
        ds.setUser(ctx.username());

        // IMPORTANT: keep read-only tool behavior. Still allow passwordless by letting
        // password be null/blank.
        if (ctx.password() != null && !ctx.password().isBlank()) {
            ds.setPassword(ctx.password());
        }

        ds.setConnectTimeout(5);
        ds.setLoginTimeout(5);

        return ds;
    }
}
