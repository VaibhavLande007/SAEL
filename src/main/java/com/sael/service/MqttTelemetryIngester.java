package com.sael.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sael.domain.entity.Device;
import com.sael.domain.entity.TelemetryReading;
import com.sael.domain.enums.IngestionSource;
import com.sael.domain.enums.QualityFlag;
import com.sael.domain.repository.DeviceRepository;
import com.sael.domain.repository.TelemetryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttTelemetryIngester implements CommandLineRunner, MqttCallbackExtended {

    private final DeviceRepository deviceRepo;
    private final TelemetryRepository telemetryRepo;
    private final AlertEvaluatorService alertEvaluatorService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${mqtt.broker-url}")
    private String brokerUrl;

    @Value("${mqtt.username}")
    private String username;

    @Value("${mqtt.password}")
    private String password;

    @Value("${mqtt.client-id}")
    private String clientId;

    private IMqttAsyncClient mqttClient;

    @Override
    public void run(String... args) {
        log.info("Starting MQTT Telemetry Ingester background thread...");
        new Thread(this::connectToMqtt, "mqtt-ingester-thread").start();
    }

    private void connectToMqtt() {
        try {
            mqttClient = new MqttAsyncClient(brokerUrl, clientId, new MemoryPersistence());
            mqttClient.setCallback(this);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(30);
            options.setKeepAliveInterval(60);

            if (username != null && !username.trim().isEmpty()) {
                options.setUserName(username);
            }
            if (password != null && !password.trim().isEmpty()) {
                options.setPassword(password.toCharArray());
            }

            log.info("Connecting to MQTT broker: {}", brokerUrl);
            IMqttToken token = mqttClient.connect(options);
            token.waitForCompletion();
            log.info("Successfully initiated MQTT connection to {}", brokerUrl);

        } catch (MqttException e) {
            log.error("Failed to initialize or connect to MQTT broker", e);
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("MQTT Connection complete. Reconnect: {}. Server: {}", reconnect, serverURI);
        try {
            mqttClient.subscribe("sael/telemetry/#", 1);
            log.info("Subscribed to MQTT topic: sael/telemetry/#");
        } catch (MqttException e) {
            log.error("Failed to subscribe to sael/telemetry/# topic", e);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost! Automatic reconnect is enabled. Cause: {}", cause != null ? cause.getMessage() : "Unknown");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload());
            log.debug("MQTT Message received on [{}]: {}", topic, payload);

            JsonNode root = objectMapper.readTree(payload);

            // 1. Get and Validate device_id
            JsonNode deviceIdNode = root.get("device_id");
            if (deviceIdNode == null || !deviceIdNode.isTextual()) {
                log.warn("Skipping payload: missing or invalid device_id");
                return;
            }
            String deviceUid = deviceIdNode.asText();

            // 2. Validate data object
            JsonNode dataNode = root.get("data");
            if (dataNode == null || !dataNode.isObject()) {
                log.warn("Skipping payload: missing or invalid data object for device {}", deviceUid);
                return;
            }

            // 3. Resolve device -> tenant + lab
            Device device = deviceRepo.findByDeviceUid(deviceUid).orElse(null);
            if (device == null) {
                log.warn("Skipping payload: unregistered device UID {}", deviceUid);
                return;
            }

            if (device.getLab() == null) {
                log.warn("Skipping payload: device {} is not assigned to a lab", deviceUid);
                return;
            }
            UUID labId = device.getLab().getId();
            UUID tenantId = device.getTenantId();

            // 4. Resolve timestamp
            OffsetDateTime recordedAt = OffsetDateTime.now(ZoneOffset.UTC);
            JsonNode tsNode = root.get("timestamp");
            if (tsNode != null && tsNode.isTextual()) {
                try {
                    recordedAt = OffsetDateTime.parse(tsNode.asText());
                } catch (Exception e) {
                    log.warn("Invalid timestamp '{}' from device {} - using server time", tsNode.asText(), deviceUid);
                }
            }

            // 5. Parse sensor parameters from 'data' field
            TelemetryReading reading = TelemetryReading.builder()
                .tenantId(tenantId)
                .deviceId(device.getId())
                .labId(labId)
                .doorChanged(false)
                .qualityFlag(QualityFlag.GOOD)
                .ingestionSrc(IngestionSource.MQTT)
                .recordedAt(recordedAt)
                .build();

            if (root.has("firmware_version")) {
                reading.setFirmwareVersion(root.get("firmware_version").asText());
            } else {
                reading.setFirmwareVersion(device.getFirmwareVersion());
            }

            if (root.has("signal_strength")) {
                reading.setSignalStrengthDbm((short) root.get("signal_strength").asInt());
            }

            // Parse metrics
            if (dataNode.has("temp")) {
                reading.setTemperatureC(BigDecimal.valueOf(dataNode.get("temp").asDouble()));
            }
            if (dataNode.has("co2")) {
                reading.setCo2Ppm(BigDecimal.valueOf(dataNode.get("co2").asDouble()));
            }
            if (dataNode.has("humidity")) {
                reading.setHumidityPct(BigDecimal.valueOf(dataNode.get("humidity").asDouble()));
            }
            if (dataNode.has("pm25")) {
                reading.setPm25UgM3(BigDecimal.valueOf(dataNode.get("pm25").asDouble()));
            }
            if (dataNode.has("pm10")) {
                reading.setPm10UgM3(BigDecimal.valueOf(dataNode.get("pm10").asDouble()));
            }
            if (dataNode.has("pm1_0")) {
                reading.setPm1UgM3(BigDecimal.valueOf(dataNode.get("pm1_0").asDouble()));
            }
            if (dataNode.has("voc")) {
                reading.setTvocPpb(BigDecimal.valueOf(dataNode.get("voc").asDouble()));
            }

            // Parse Door status
            if (dataNode.has("isDoorOpen")) {
                JsonNode doorNode = dataNode.get("isDoorOpen");
                if (doorNode.isObject()) {
                    if (doorNode.has("value") && doorNode.get("value").isBoolean()) {
                        reading.setDoorIsOpen(doorNode.get("value").asBoolean());
                    }
                    if (doorNode.has("statusChange") && doorNode.get("statusChange").isBoolean()) {
                        reading.setDoorChanged(doorNode.get("statusChange").asBoolean());
                    }
                } else if (doorNode.isBoolean()) {
                    reading.setDoorIsOpen(doorNode.asBoolean());
                }
            }

            // 6. Save reading
            TelemetryReading saved = telemetryRepo.save(reading);
            log.info("Saved telemetry for lab {}: temp={}°C, co2={}ppm, humidity={}%",
                labId, reading.getTemperatureC(), reading.getCo2Ppm(), reading.getHumidityPct());

            // 7. Update Device health status
            boolean wasOffline = !Boolean.TRUE.equals(device.getOnlineStatus());
            device.setOnlineStatus(true);
            device.setLastSeenAt(OffsetDateTime.now());
            if (root.has("battery")) {
                device.setBatteryLevel(root.get("battery").asInt());
            }
            if (root.has("signal_strength")) {
                device.setSignalStrength(root.get("signal_strength").asInt());
            }
            if (root.has("firmware_version")) {
                device.setFirmwareVersion(root.get("firmware_version").asText());
            }
            deviceRepo.save(device);

            // Trigger pg_notify on online transition
            if (wasOffline) {
                Map<String, Object> transition = new LinkedHashMap<>();
                transition.put("deviceUid", deviceUid);
                transition.put("labId", labId.toString());
                transition.put("onlineStatus", true);
                String transitionJson = objectMapper.writeValueAsString(transition);
                jdbcTemplate.execute("SELECT pg_notify('device_status', '" + transitionJson + "')");
                alertEvaluatorService.resolveDeviceOfflineAlert(device.getId(), labId);
            }

            // 8. Broadcast telemetry update for SSE consumer
            broadcastTelemetryUpdate(reading, deviceUid);

            // 9. Evaluate thresholds & trigger alerts
            alertEvaluatorService.evaluateAlerts(saved);

        } catch (Exception e) {
            log.error("Failed to parse and process incoming MQTT telemetry message", e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Only subbing, not pubbing
    }

    private void broadcastTelemetryUpdate(TelemetryReading row, String deviceUid) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("temperatureC", row.getTemperatureC() != null ? row.getTemperatureC().doubleValue() : null);
            snapshot.put("co2Ppm", row.getCo2Ppm() != null ? row.getCo2Ppm().doubleValue() : null);
            snapshot.put("humidityPct", row.getHumidityPct() != null ? row.getHumidityPct().doubleValue() : null);
            snapshot.put("pm25UgM3", row.getPm25UgM3() != null ? row.getPm25UgM3().doubleValue() : null);
            snapshot.put("pm10UgM3", row.getPm10UgM3() != null ? row.getPm10UgM3().doubleValue() : null);
            snapshot.put("pm1UgM3", row.getPm1UgM3() != null ? row.getPm1UgM3().doubleValue() : null);
            snapshot.put("tvocPpb", row.getTvocPpb() != null ? row.getTvocPpb().doubleValue() : null);
            snapshot.put("doorIsOpen", row.getDoorIsOpen());
            snapshot.put("doorChanged", row.getDoorChanged());
            snapshot.put("signalStrengthDbm", row.getSignalStrengthDbm() != null ? row.getSignalStrengthDbm().intValue() : null);
            snapshot.put("recordedAt", row.getRecordedAt().toString());

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("deviceUid", deviceUid);
            event.put("labId", row.getLabId().toString());
            event.put("temperatureC", snapshot.get("temperatureC"));
            event.put("co2Ppm", snapshot.get("co2Ppm"));
            event.put("humidityPct", snapshot.get("humidityPct"));
            event.put("pm25UgM3", snapshot.get("pm25UgM3"));
            event.put("pm10UgM3", snapshot.get("pm10UgM3"));
            event.put("pm1UgM3", snapshot.get("pm1UgM3"));
            event.put("tvocPpb", snapshot.get("tvocPpb"));
            event.put("doorIsOpen", snapshot.get("doorIsOpen"));
            event.put("doorChanged", snapshot.get("doorChanged"));
            event.put("signalStrengthDbm", snapshot.get("signalStrengthDbm"));
            event.put("recordedAt", snapshot.get("recordedAt"));

            String json = objectMapper.writeValueAsString(event);
            String escapedJson = json.replace("'", "''");
            jdbcTemplate.execute("SELECT pg_notify('telemetry_update', '" + escapedJson + "')");
        } catch (Exception e) {
            log.error("Failed to broadcast telemetry_update notification", e);
        }
    }
}
