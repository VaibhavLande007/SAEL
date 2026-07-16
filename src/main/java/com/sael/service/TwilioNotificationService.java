package com.sael.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sael.domain.entity.*;
import com.sael.domain.enums.*;
import com.sael.domain.repository.LabRepository;
import com.sael.domain.repository.NotificationLogRepository;
import com.sael.domain.repository.NotificationRecipientRepository;
import com.sael.domain.repository.SystemConfigRepository;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Call;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import com.twilio.type.Twiml;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class TwilioNotificationService {

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.whatsapp-from}")
    private String twilioWhatsappFrom;

    @Value("${twilio.voice-from}")
    private String twilioVoiceFrom;

    private final LabRepository labRepo;
    private final NotificationRecipientRepository recipientRepo;
    private final NotificationLogRepository logRepo;
    private final SystemConfigRepository systemConfigRepo;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running = true;
    private Connection connection;
    private boolean twilioInitialized = false;

    public TwilioNotificationService(LabRepository labRepo,
                                     NotificationRecipientRepository recipientRepo,
                                     NotificationLogRepository logRepo,
                                     SystemConfigRepository systemConfigRepo) {
        this.labRepo = labRepo;
        this.recipientRepo = recipientRepo;
        this.logRepo = logRepo;
        this.systemConfigRepo = systemConfigRepo;
    }

    @PostConstruct
    public void startListening() {
        if (accountSid != null && !accountSid.trim().isEmpty() && authToken != null && !authToken.trim().isEmpty()) {
            try {
                Twilio.init(accountSid, authToken);
                twilioInitialized = true;
                log.info("Twilio successfully initialized with account SID: {}", accountSid);
            } catch (Exception e) {
                log.error("Failed to initialize Twilio SDK", e);
            }
        } else {
            log.warn("Twilio credentials not fully set. Twilio notifications will be logged to database but NOT dispatched.");
        }

        executor.submit(this::listenLoop);
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
    }

    private void listenLoop() {
        while (running) {
            try {
                log.info("Connecting to database for LISTEN alert_fired...");
                connection = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("LISTEN alert_fired");
                }
                log.info("Successfully listening on PostgreSQL channel: alert_fired");

                PGConnection pgConn = connection.unwrap(PGConnection.class);

                while (running) {
                    PGNotification[] notifications = pgConn.getNotifications(500); // block up to 500ms
                    if (notifications != null) {
                        for (PGNotification notification : notifications) {
                            if ("alert_fired".equals(notification.getName())) {
                                handleAlertNotification(notification.getParameter());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (running) {
                    log.error("Error in Postgres LISTEN alert_fired loop. Retrying in 5 seconds...", e);
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
    private void handleAlertNotification(String payloadJson) {
        try {
            log.debug("Alert fired notification received: {}", payloadJson);
            Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);

            UUID alertId = UUID.fromString((String) payload.get("alertId"));
            UUID labId = UUID.fromString((String) payload.get("labId"));
            UUID tenantId = UUID.fromString((String) payload.get("tenantId"));
            String metricColumn = (String) payload.get("metricColumn");
            String label = (String) payload.get("label");
            String severity = (String) payload.get("severity");
            String message = (String) payload.get("message");
            double triggerValue = ((Number) payload.get("triggerValue")).doubleValue();
            double thresholdValue = ((Number) payload.get("thresholdValue")).doubleValue();
            String triggeredAtStr = (String) payload.get("triggeredAt");

            Lab lab = labRepo.findByIdWithHospitalAndNetwork(labId).orElse(null);
            if (lab == null) {
                log.warn("Lab {} not found for alert notification dispatch", labId);
                return;
            }

            UUID networkId = lab.getHospital().getNetwork().getId();
            String labName = lab.getName();

            // 1. Fetch active recipients for the network
            List<NotificationRecipient> recipients = recipientRepo.findAllByNetworkIdAndChannelAndIsActiveTrue(networkId, NotificationChannel.WHATSAPP);
            if (recipients.isEmpty()) {
                // Fall back to tenant-level active WhatsApp recipients
                recipients = recipientRepo.findAllByTenantIdAndChannelOrderByCreatedAtAsc(tenantId, NotificationChannel.WHATSAPP);
            }

            if (recipients.isEmpty()) {
                log.info("No active WhatsApp recipients resolved for network {} or tenant {}", networkId, tenantId);
                return;
            }

            // 2. Fetch alert toggles for the tenant
            Optional<SystemConfig> togglesCfg = systemConfigRepo.findByTenantIdAndKey(tenantId, "alertToggles");
            Map<String, Object> toggles = togglesCfg.map(SystemConfig::getValue).orElse(Map.of());
            boolean waEnabled = (Boolean) toggles.getOrDefault("wa", true);

            if (!waEnabled) {
                log.info("WhatsApp notifications are disabled by configuration toggle for tenant {}", tenantId);
                return;
            }

            boolean isCrit = "CRIT".equalsIgnoreCase(severity);

            // 3. Process each recipient
            for (NotificationRecipient recipient : recipients) {
                // Idempotency: skip if already logged for this alert
                if (logRepo.existsByAlertIdAndRecipientId(alertId, recipient.getId())) {
                    log.debug("Already processed notification for alert {} to recipient {} - skipping", alertId, recipient.getLabel());
                    continue;
                }

                // Check severity preferences
                if (isCrit && !Boolean.TRUE.equals(recipient.getReceivesCritical())) {
                    log.debug("Recipient {} does not wish to receive critical alerts", recipient.getLabel());
                    continue;
                }
                if (!isCrit && !Boolean.TRUE.equals(recipient.getReceivesWarning())) {
                    log.debug("Recipient {} does not wish to receive warning alerts", recipient.getLabel());
                    continue;
                }

                dispatchWhatsApp(tenantId, alertId, recipient, label, severity, message, triggerValue, thresholdValue, triggeredAtStr, labName);

                if (isCrit && twilioVoiceFrom != null && !twilioVoiceFrom.trim().isEmpty()) {
                    dispatchVoiceCall(tenantId, alertId, recipient, label, triggerValue, labName);
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse and process alert notification", e);
        }
    }

    private void dispatchWhatsApp(UUID tenantId, UUID alertId, NotificationRecipient recipient,
                                    String label, String severity, String message,
                                    double triggerValue, double thresholdValue, String triggeredAtStr, String labName) {

        String level = "CRIT".equalsIgnoreCase(severity) ? "CRITICAL ALERT" : "WARNING";
        String icon = "CRIT".equalsIgnoreCase(severity) ? "🚨" : "⚠️";

        String timeFormatted = triggeredAtStr;
        try {
            OffsetDateTime parsed = OffsetDateTime.parse(triggeredAtStr);
            timeFormatted = parsed.format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));
        } catch (Exception e) {
            // keep raw triggeredAtStr
        }

        String waBody = String.format(
            "%s *SAEL %s*\n\n" +
            "*Lab:* %s\n" +
            "*Sensor:* %s\n" +
            "*Reading:* %.2f (threshold: %.2f)\n" +
            "*Detail:* %s\n" +
            "*Time:* %s\n\n" +
            "Please check the lab immediately.",
            icon, level, labName, label, triggerValue, thresholdValue, message, timeFormatted
        );

        NotificationLog logRow = NotificationLog.builder()
            .tenantId(tenantId)
            .recipientId(recipient.getId())
            .alertId(alertId)
            .channel(NotificationChannel.WHATSAPP)
            .messagePreview(waBody.substring(0, Math.min(waBody.length(), 500)))
            .status(NotificationStatus.PENDING)
            .attempts(0)
            .build();

        logRow = logRepo.save(logRow);

        if (!twilioInitialized) {
            logRow.setStatus(NotificationStatus.FAILED);
            logRow.setAttempts(1);
            logRow.setErrorMessage("Twilio not initialized - credentials missing");
            logRepo.save(logRow);
            log.warn("Mock WhatsApp notification sent to recipient {} (no Twilio configured): {}", recipient.getAddress(), waBody);
            return;
        }

        try {
            Message.creator(
                new PhoneNumber("whatsapp:" + recipient.getAddress()),
                new PhoneNumber(twilioWhatsappFrom),
                waBody
            ).create();

            logRow.setStatus(NotificationStatus.SENT);
            logRow.setAttempts(1);
            logRow.setSentAt(OffsetDateTime.now());
            logRepo.save(logRow);
            log.info("WhatsApp notification successfully sent to {}", recipient.getAddress());

        } catch (Exception e) {
            log.error("Failed to send WhatsApp message to {}", recipient.getAddress(), e);
            logRow.setStatus(NotificationStatus.FAILED);
            logRow.setAttempts(1);
            logRow.setErrorMessage(e.getMessage());
            logRepo.save(logRow);
        }
    }

    private void dispatchVoiceCall(UUID tenantId, UUID alertId, NotificationRecipient recipient,
                                   String label, double triggerValue, String labName) {

        String voiceText = String.format(
            "This is a critical alert from S.A.E.L. for %s. " +
            "%s has exceeded the critical threshold. " +
            "Current reading is %.2f. " +
            "Please check the lab immediately. " +
            "This message will repeat once. " +
            "%s has exceeded the critical threshold. " +
            "Current reading is %.2f. " +
            "Please check the lab immediately.",
            labName, label, triggerValue, label, triggerValue
        );

        NotificationLog logRow = NotificationLog.builder()
            .tenantId(tenantId)
            .recipientId(recipient.getId())
            .alertId(alertId)
            .channel(NotificationChannel.SMS) // Reusing SMS for voice logs per Prisma schema mapping
            .messagePreview("Voice call: " + label + " critical")
            .status(NotificationStatus.PENDING)
            .attempts(0)
            .build();

        logRow = logRepo.save(logRow);

        if (!twilioInitialized) {
            logRow.setStatus(NotificationStatus.FAILED);
            logRow.setAttempts(1);
            logRow.setErrorMessage("Twilio not initialized - credentials missing");
            logRepo.save(logRow);
            log.warn("Mock Voice Call trigger for recipient {} (no Twilio configured): {}", recipient.getAddress(), voiceText);
            return;
        }

        try {
            Call.creator(
                new PhoneNumber(recipient.getAddress()),
                new PhoneNumber(twilioVoiceFrom),
                new Twiml("<Response><Say voice=\"alice\">" + voiceText + "</Say></Response>")
            ).create();

            logRow.setStatus(NotificationStatus.SENT);
            logRow.setAttempts(1);
            logRow.setSentAt(OffsetDateTime.now());
            logRepo.save(logRow);
            log.info("Voice call successfully placed to {}", recipient.getAddress());

        } catch (Exception e) {
            log.error("Failed to trigger voice call to {}", recipient.getAddress(), e);
            logRow.setStatus(NotificationStatus.FAILED);
            logRow.setAttempts(1);
            logRow.setErrorMessage(e.getMessage());
            logRepo.save(logRow);
        }
    }

    public void dispatchEscalationCall(UUID tenantId, UUID alertId, NotificationRecipient recipient,
                                       String label, double triggerValue, String labName) {
        String escalationText = String.format(
            "ESCALATION. This is a follow-up critical alert from SAEL for %s. " +
            "%s has been above the critical threshold for 10 minutes and has not been acknowledged. " +
            "Current reading is %.2f. " +
            "Immediate action required. I repeat: %s requires immediate attention.",
            labName, label, triggerValue, label
        );

        NotificationLog logRow = NotificationLog.builder()
            .tenantId(tenantId)
            .recipientId(recipient.getId())
            .alertId(alertId)
            .channel(NotificationChannel.SMS)
            .messagePreview("ESCALATION: " + label + " still critical — 10 min follow-up call")
            .status(NotificationStatus.PENDING)
            .attempts(0)
            .build();

        logRow = logRepo.save(logRow);

        if (!twilioInitialized) {
            logRow.setStatus(NotificationStatus.FAILED);
            logRow.setAttempts(1);
            logRow.setErrorMessage("Twilio not initialized - credentials missing");
            logRepo.save(logRow);
            log.warn("Mock Escalation Voice Call for recipient {} (no Twilio configured): {}", recipient.getAddress(), escalationText);
            return;
        }

        try {
            Call.creator(
                new PhoneNumber(recipient.getAddress()),
                new PhoneNumber(twilioVoiceFrom),
                new Twiml("<Response><Say voice=\"alice\">" + escalationText + "</Say></Response>")
            ).create();

            logRow.setStatus(NotificationStatus.SENT);
            logRow.setAttempts(1);
            logRow.setSentAt(OffsetDateTime.now());
            logRepo.save(logRow);
            log.info("Escalation voice call successfully placed to {}", recipient.getAddress());

        } catch (Exception e) {
            log.error("Failed to trigger escalation voice call to {}", recipient.getAddress(), e);
            logRow.setStatus(NotificationStatus.FAILED);
            logRow.setAttempts(1);
            logRow.setErrorMessage(e.getMessage());
            logRepo.save(logRow);
        }
    }
}
