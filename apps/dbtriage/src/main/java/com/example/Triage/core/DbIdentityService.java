package com.example.Triage.core;

import com.example.Triage.model.response.DbIdentityResponse;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.OffsetDateTime;

@Service
public class DbIdentityService {

    public DbIdentityResponse getIdentity(DbConnectContext ctx) throws SQLException {
        DataSource ds = buildDataSource(ctx);

        try (Connection c = ds.getConnection()) {
            String sql = """
                    select
                      current_database()  as db,
                      current_user        as curr_user,
                      session_user        as sess_user,
                      inet_server_addr()  as server_addr,
                      inet_server_port()  as server_port,
                      version()           as server_version,
                      now()               as server_time
                    """;

            try (PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                rs.next();
                String db = rs.getString("db");
                String currentUser = rs.getString("curr_user");
                String sessionUser = rs.getString("sess_user");
                String addr = rs.getString("server_addr");
                int port = rs.getInt("server_port");
                String ver = rs.getString("server_version");

                Timestamp ts = rs.getTimestamp("server_time");
                OffsetDateTime serverTime = ts != null 
                    ? ts.toInstant().atOffset(OffsetDateTime.now().getOffset()) 
                    : null;

                return new DbIdentityResponse(
                    db, currentUser, sessionUser, addr, port, ver, serverTime, ctx.schema()
                );
            }
        }
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

