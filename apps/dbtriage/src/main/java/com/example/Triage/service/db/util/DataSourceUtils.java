package com.example.Triage.service.db.util;

import javax.sql.DataSource;

import org.postgresql.ds.PGSimpleDataSource;

import com.example.Triage.model.dto.DbConnectContextDto;

import lombok.experimental.UtilityClass;

@UtilityClass
public class DataSourceUtils {

    public static DataSource buildDataSource(DbConnectContextDto ctx) {
        var ds = new PGSimpleDataSource();
        String sslMode = (ctx.sslMode() == null || ctx.sslMode().isBlank()) ? "require" : ctx.sslMode();
        ds.setUrl(String.format("jdbc:postgresql://%s:%d/%s?sslmode=%s",
                ctx.host(), ctx.port(), ctx.database(), sslMode));
        ds.setUser(ctx.username());
        ds.setPassword(ctx.password());
        ds.setConnectTimeout(5);
        ds.setLoginTimeout(5);
        return ds;
    }
}
