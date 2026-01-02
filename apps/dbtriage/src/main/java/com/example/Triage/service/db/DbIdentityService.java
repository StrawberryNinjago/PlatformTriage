package com.example.Triage.service.db;

import com.example.Triage.dao.DbQueries;
import com.example.Triage.model.dto.DbConnectContextDto;
import com.example.Triage.model.response.DbIdentityResponse;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;

@Service
public class DbIdentityService {

    public DbIdentityResponse getIdentity(DbConnectContextDto ctx) throws SQLException {
        DataSource ds = buildDataSource(ctx);

        try (Connection c = ds.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    DbQueries.GET_CONNECTION_CONTEXT);
                    ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    throw new SQLException("No identity data returned from database");
                }
                
                String db = rs.getString("db");
                String currentUser = rs.getString("curr_user");
                String sessionUser = rs.getString("sess_user");
                String addr = rs.getString("server_addr");
                Integer port = rs.getInt("server_port");
                String ver = rs.getString("server_version");

                Timestamp ts = rs.getTimestamp("server_time");
                OffsetDateTime serverTime = ts != null
                        ? ts.toInstant().atOffset(OffsetDateTime.now().getOffset())
                        : null;

                return new DbIdentityResponse(
                        db != null ? db : "unknown",
                        currentUser != null ? currentUser : "unknown",
                        sessionUser != null ? sessionUser : "unknown",
                        addr != null ? addr : "localhost",
                        port != null ? port : 5432,
                        ver != null ? ver : "unknown",
                        serverTime,
                        ctx.schema() != null ? ctx.schema() : "public");
            }
        }
    }

    private DataSource buildDataSource(DbConnectContextDto ctx) {
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
