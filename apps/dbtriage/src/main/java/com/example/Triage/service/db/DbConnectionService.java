package com.example.Triage.service.db;

import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.stereotype.Service;

import com.example.Triage.model.dto.DbConnectContextDto;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@Service
public class DbConnectionService {

    public void testConnection(DbConnectContextDto ctx) throws Exception {
        DataSource ds = buildDataSource(ctx);

        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement("select 1");
                ResultSet rs = ps.executeQuery()) {

            if (!rs.next() || rs.getInt(1) != 1) {
                throw new IllegalStateException("Connection test query returned unexpected result.");
            }
        }
    }

    private DataSource buildDataSource(DbConnectContextDto ctx) {
        PGSimpleDataSource ds = new PGSimpleDataSource();

        // URL with sslmode=require by default (Azure Postgres typically needs SSL)
        String url = String.format(
                "jdbc:postgresql://%s:%d/%s?sslmode=%s",
                ctx.host(), ctx.port(), ctx.database(), ctx.sslMode());

        ds.setUrl(url);
        ds.setUser(ctx.username());
        ds.setPassword(ctx.password());

        // Optional: keep it tight for diagnostics
        ds.setConnectTimeout(5); // seconds
        ds.setLoginTimeout(5); // seconds
        return ds;
    }
}
