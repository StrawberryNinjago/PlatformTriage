package com.example.Triage.service.db;

import org.springframework.stereotype.Component;

import com.example.Triage.model.dto.DbConnectContextDto;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DbConnectionRegistry {

    private static final Duration TTL = Duration.ofMinutes(15);

    private final Map<String, DbConnectContextDto> store = new ConcurrentHashMap<>();

    public DbConnectContextDto create(String host, int port, String database, String username, String password,
            String sslMode, String schema) {
        String id = "pt-" + UUID.randomUUID();
        var ctx = new DbConnectContextDto(
                id, host, port, database, username, password,
                (sslMode == null || sslMode.isBlank()) ? "require" : sslMode,
                (schema == null || schema.isBlank()) ? "public" : schema,
                Instant.now());
        store.put(id, ctx);
        return ctx;
    }

    public Optional<DbConnectContextDto> get(String id) {
        var ctx = store.get(id);
        if (ctx == null)
            return Optional.empty();

        if (Instant.now().isAfter(ctx.createdAt().plus(TTL))) {
            store.remove(id);
            return Optional.empty();
        }
        return Optional.of(ctx);
    }

    public void delete(String id) {
        store.remove(id);
    }

    public void purgeExpired() {
        Instant now = Instant.now();
        store.entrySet().removeIf(e -> now.isAfter(e.getValue().createdAt().plus(TTL)));
    }
}
