package com.sael.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sael.domain.repository.LabRepository;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
public class TelemetryStreamService {

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    private final LabRepository labRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService pingExecutor = Executors.newSingleThreadScheduledExecutor();
    private final Map<UUID, List<SseEmitterWrapper>> emitters = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> labTenantCache = new ConcurrentHashMap<>();
    private volatile boolean running = true;
    private Connection connection;

    public TelemetryStreamService(LabRepository labRepo) {
        this.labRepo = labRepo;
    }

    private static class SseEmitterWrapper {
        final SseEmitter emitter;
        final List<UUID> allowedLabIds;

        SseEmitterWrapper(SseEmitter emitter, List<UUID> allowedLabIds) {
            this.emitter = emitter;
            this.allowedLabIds = allowedLabIds;
        }
    }

    @PostConstruct
    public void startListening() {
        executor.submit(this::listenLoop);
        pingExecutor.scheduleAtFixedRate(this::sendHeartbeats, 20, 20, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stopListening() {
        running = false;
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) {
                // ignore
            }
        }
        executor.shutdownNow();
        pingExecutor.shutdownNow();
    }

    public SseEmitter createEmitter(UUID tenantId, List<UUID> allowedLabIds) {
        // Emitter timeout set to 24 hours to keep the connection long-lived
        SseEmitter emitter = new SseEmitter(24 * 60 * 60 * 1000L);
        SseEmitterWrapper wrapper = new SseEmitterWrapper(emitter, allowedLabIds);

        emitters.computeIfAbsent(tenantId, k -> new CopyOnWriteArrayList<>()).add(wrapper);

        emitter.onCompletion(() -> removeEmitter(tenantId, wrapper));
        emitter.onTimeout(() -> removeEmitter(tenantId, wrapper));
        emitter.onError((e) -> removeEmitter(tenantId, wrapper));

        try {
            // Initial connection confirmation ping
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }

    private void removeEmitter(UUID tenantId, SseEmitterWrapper wrapper) {
        List<SseEmitterWrapper> list = emitters.get(tenantId);
        if (list != null) {
            list.remove(wrapper);
            if (list.isEmpty()) {
                emitters.remove(tenantId);
            }
        }
    }

    private void listenLoop() {
        while (running) {
            try {
                log.info("Connecting to database for LISTEN telemetry_update...");
                // Remove jdbc:prefix if needed? No, DriverManager matches by URL patterns.
                // However, PG Driver expects a raw postgresql:// or jdbc:postgresql:// URL.
                // Spring jdbc:postgresql:// is correct for DriverManager.
                connection = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("LISTEN telemetry_update");
                }
                log.info("Successfully listening on PostgreSQL channel: telemetry_update");

                PGConnection pgConn = connection.unwrap(PGConnection.class);

                while (running) {
                    PGNotification[] notifications = pgConn.getNotifications(500); // block up to 500ms
                    if (notifications != null) {
                        for (PGNotification notification : notifications) {
                            if ("telemetry_update".equals(notification.getName())) {
                                handleNotification(notification.getParameter());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (running) {
                    log.error("Error in Postgres LISTEN loop. Retrying in 5 seconds...", e);
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (Exception ex) {
                        // ignore
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleNotification(String payload) {
        try {
            Map<String, Object> raw = objectMapper.readValue(payload, Map.class);
            String labIdStr = (String) raw.get("labId");
            if (labIdStr == null) return;
            UUID labId = UUID.fromString(labIdStr);

            UUID tenantId = getTenantIdForLab(labId);
            if (tenantId == null) return;

            // Shape telemetry snapshot to match exact frontend expectations
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("temperature", raw.get("temperatureC"));
            snapshot.put("co2", raw.get("co2Ppm"));
            snapshot.put("humidity", raw.get("humidityPct"));
            snapshot.put("pm25", raw.get("pm25UgM3"));
            snapshot.put("voc", raw.get("tvocPpb"));
            snapshot.put("isDoorOpen", raw.get("doorIsOpen"));
            snapshot.put("recordedAt", raw.get("recordedAt"));

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("labId", labId.toString());
            event.put("deviceId", raw.get("deviceUid"));
            event.put("snapshot", snapshot);

            List<SseEmitterWrapper> list = emitters.get(tenantId);
            if (list != null) {
                for (SseEmitterWrapper wrapper : list) {
                    if (wrapper.allowedLabIds == null || wrapper.allowedLabIds.contains(labId)) {
                        try {
                            wrapper.emitter.send(
                                SseEmitter.event()
                                    .name("telemetry")
                                    .data(event)
                            );
                        } catch (Exception e) {
                            wrapper.emitter.completeWithError(e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse or distribute telemetry update notification", e);
        }
    }

    private UUID getTenantIdForLab(UUID labId) {
        return labTenantCache.computeIfAbsent(labId, id -> {
            try {
                return labRepo.findByIdAndDeletedAtIsNull(id)
                    .map(lab -> lab.getTenant().getId())
                    .orElse(null);
            } catch (Exception e) {
                log.error("Failed to look up tenant for lab " + id, e);
                return null;
            }
        });
    }

    private void sendHeartbeats() {
        for (var tenantEmitters : emitters.values()) {
            for (var wrapper : tenantEmitters) {
                try {
                    wrapper.emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    wrapper.emitter.completeWithError(e);
                }
            }
        }
    }
}
