package com.sael.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sael.domain.entity.*;
import com.sael.domain.enums.*;
import com.sael.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
@Transactional
public class BackgroundJobsScheduler {

    private final DeviceRepository deviceRepo;
    private final AlertRepository alertRepo;
    private final NotificationRecipientRepository recipientRepo;
    private final NotificationLogRepository logRepo;
    private final TwilioNotificationService twilioNotificationService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private boolean watchdogRunning = false;
    private boolean offlineAlertChecking = false;

    @Scheduled(fixedDelay = 5000)
    public void runOfflineWatchdog() {
        if (watchdogRunning) return;
        watchdogRunning = true;
        try {
            OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds(15);
            List<Device> devices = deviceRepo.findAll();
            for (Device device : devices) {
                if (Boolean.TRUE.equals(device.getOnlineStatus()) && device.getLastSeenAt() != null && device.getLastSeenAt().isBefore(cutoff)) {
                    device.setOnlineStatus(false);
                    deviceRepo.save(device);
                    
                    log.info("Watchdog: Device {} (UID: {}) marked offline due to inactivity", device.getId(), device.getDeviceUid());

                    UUID labId = device.getLab() != null ? device.getLab().getId() : null;
                    if (labId != null) {
                        Map<String, Object> transition = new LinkedHashMap<>();
                        transition.put("deviceUid", device.getDeviceUid());
                        transition.put("labId", labId.toString());
                        transition.put("onlineStatus", false);
                        String transitionJson = objectMapper.writeValueAsString(transition);
                        jdbcTemplate.execute("SELECT pg_notify('device_status', '" + transitionJson + "')");
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error in runOfflineWatchdog execution", e);
        } finally {
            watchdogRunning = false;
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void runDeviceOfflineAlertsCheck() {
        if (offlineAlertChecking) return;
        offlineAlertChecking = true;
        try {
            OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(2);
            List<Device> devices = deviceRepo.findAll();
            for (Device device : devices) {
                if (!Boolean.TRUE.equals(device.getOnlineStatus()) && device.getLastSeenAt() != null && device.getLastSeenAt().isBefore(cutoff)) {
                    UUID labId = device.getLab() != null ? device.getLab().getId() : null;
                    if (labId == null) continue;

                    Optional<Alert> existingAlert = alertRepo.findFirstByLab_IdAndMetricColumnAndStatus(
                        labId, "deviceOffline", AlertStatus.ACTIVE
                    );
                    if (existingAlert.isPresent()) {
                        continue;
                    }

                    long minutesOffline = (System.currentTimeMillis() - device.getLastSeenAt().toInstant().toEpochMilli()) / 60000;
                    String message = String.format("Device %s has been offline for >= %d minutes", device.getDeviceUid(), minutesOffline);

                    Alert offlineAlert = Alert.builder()
                        .id(UUID.randomUUID())
                        .tenantId(device.getTenantId())
                        .lab(device.getLab())
                        .deviceId(device.getId())
                        .metricColumn("deviceOffline")
                        .parameter("Device Offline")
                        .severity(AlertSeverity.CRIT)
                        .message(message)
                        .triggerValue(BigDecimal.valueOf(minutesOffline))
                        .thresholdValue(BigDecimal.valueOf(2))
                        .status(AlertStatus.ACTIVE)
                        .triggeredAt(OffsetDateTime.now())
                        .isPredicted(false)
                        .build();

                    alertRepo.save(offlineAlert);
                    log.warn("Offline alert fired for device {}", device.getDeviceUid());

                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("alertId", offlineAlert.getId().toString());
                    payload.put("labId", labId.toString());
                    payload.put("tenantId", device.getTenantId().toString());
                    payload.put("deviceId", device.getId().toString());
                    payload.put("metricColumn", "deviceOffline");
                    payload.put("label", "Device Offline");
                    payload.put("severity", "CRIT");
                    payload.put("message", message);
                    payload.put("triggerValue", (double) minutesOffline);
                    payload.put("thresholdValue", 2.0);
                    payload.put("triggeredAt", offlineAlert.getTriggeredAt().toString());

                    String json = objectMapper.writeValueAsString(payload);
                    jdbcTemplate.execute("SELECT pg_notify('alert_fired', '" + json + "')");
                }
            }
        } catch (Exception e) {
            log.error("Error in runDeviceOfflineAlertsCheck execution", e);
        } finally {
            offlineAlertChecking = false;
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void runAlertEscalations() {
        try {
            OffsetDateTime before = OffsetDateTime.now().minusMinutes(10);
            List<Alert> oldCritAlerts = alertRepo.findAllByStatusAndSeverityAndTriggeredAtBefore(
                AlertStatus.ACTIVE, AlertSeverity.CRIT, before
            );

            for (Alert alert : oldCritAlerts) {
                boolean alreadyEscalated = logRepo.existsByAlertIdAndMessagePreviewStartingWith(
                    alert.getId(), "ESCALATION:"
                );
                if (alreadyEscalated) {
                    continue;
                }

                UUID networkId = alert.getLab().getHospital().getNetwork().getId();
                List<NotificationRecipient> recipients = recipientRepo.findAllByNetworkIdAndChannelAndIsActiveTrue(
                    networkId, NotificationChannel.WHATSAPP
                );
                if (recipients.isEmpty()) {
                    recipients = recipientRepo.findAllByTenantIdAndChannelOrderByCreatedAtAsc(
                        alert.getTenantId(), NotificationChannel.WHATSAPP
                    );
                }

                for (NotificationRecipient recipient : recipients) {
                    twilioNotificationService.dispatchEscalationCall(
                        alert.getTenantId(),
                        alert.getId(),
                        recipient,
                        alert.getParameter(),
                        alert.getTriggerValue().doubleValue(),
                        alert.getLab().getName()
                    );
                }
            }
        } catch (Exception e) {
            log.error("Error in runAlertEscalations execution", e);
        }
    }
}
