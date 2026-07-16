package com.sael.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sael.domain.entity.*;
import com.sael.domain.enums.*;
import com.sael.domain.repository.AlertRepository;
import com.sael.domain.repository.AlertRuleRepository;
import com.sael.domain.repository.LabRepository;
import com.sael.domain.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEvaluatorService {

    private final AlertRuleRepository ruleRepo;
    private final AlertRepository alertRepo;
    private final LabRepository labRepo;
    private final SystemConfigRepository systemConfigRepo;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<MetricMetadata> EVALUABLE_METRICS = List.of(
        new MetricMetadata("temperatureC", "Temperature", SensorType.TEMPERATURE, "temp_warn", "temp_crit"),
        new MetricMetadata("co2Ppm", "CO2", SensorType.CO2, "co2_warn", "co2_crit"),
        new MetricMetadata("humidityPct", "Humidity", SensorType.HUMIDITY, "hum_warn", "hum_crit"),
        new MetricMetadata("pm25UgM3", "PM2.5", SensorType.PM25, "pm_warn", "pm_crit"),
        new MetricMetadata("tvocPpb", "VOC", SensorType.VOC, "voc_warn", "voc_crit")
    );

    @Transactional
    public void evaluateAlerts(TelemetryReading reading) {
        UUID labId = reading.getLabId();
        UUID tenantId = reading.getTenantId();
        UUID deviceId = reading.getDeviceId();

        Lab lab = labRepo.findById(labId).orElse(null);
        if (lab == null) {
            log.warn("Lab {} not found for alert evaluation", labId);
            return;
        }

        UUID hospitalId = lab.getHospital().getId();
        UUID networkId = lab.getHospital().getNetwork().getId();

        // 1. Fetch rules applicable to this lab/hospital/network
        List<AlertRule> rules = ruleRepo.findApplicableRules(tenantId, networkId, hospitalId, labId);

        // 2. Resolve rules hierarchically (Lab > Hospital > Network)
        Map<SensorType, AlertRule> resolvedRules = new HashMap<>();
        for (AlertRule r : rules) {
            AlertRule existing = resolvedRules.get(r.getSensorType());
            if (existing == null) {
                resolvedRules.put(r.getSensorType(), r);
            } else {
                if (r.getScopeType() == AlertScopeType.LAB) {
                    resolvedRules.put(r.getSensorType(), r);
                } else if (r.getScopeType() == AlertScopeType.HOSPITAL && existing.getScopeType() != AlertScopeType.LAB) {
                    resolvedRules.put(r.getSensorType(), r);
                }
            }
        }

        // 3. Fallback tenant config thresholds
        Optional<SystemConfig> thresholdsCfg = systemConfigRepo.findByTenantIdAndKey(tenantId, "thresholds");
        Map<String, Object> fallbackThresholds = thresholdsCfg.map(SystemConfig::getValue).orElse(Map.of());

        // 4. Check each metric
        for (MetricMetadata metric : EVALUABLE_METRICS) {
            BigDecimal value = getMetricValue(reading, metric.column);
            if (value == null) {
                continue;
            }

            AlertRule rule = resolvedRules.get(metric.sensorType);

            BigDecimal warnMin = null;
            BigDecimal warnMax = null;
            BigDecimal critMin = null;
            BigDecimal critMax = null;
            int cooldownMin = 5;
            UUID ruleId = null;

            if (rule != null) {
                warnMin = rule.getWarnMin();
                warnMax = rule.getWarnMax();
                critMin = rule.getCritMin();
                critMax = rule.getCritMax();
                cooldownMin = rule.getCooldownMin() != null ? rule.getCooldownMin() : 5;
                ruleId = rule.getId();
            } else {
                // Fall back to tenant global config (only upper bounds warn/crit are seeded as default keys)
                Object warnVal = fallbackThresholds.get(metric.fallbackWarnKey);
                Object critVal = fallbackThresholds.get(metric.fallbackCritKey);
                if (warnVal != null) {
                    warnMax = new BigDecimal(warnVal.toString());
                }
                if (critVal != null) {
                    critMax = new BigDecimal(critVal.toString());
                }
            }

            // Determine breach severity
            AlertSeverity breachSeverity = null;
            BigDecimal breachThreshold = null;

            if (critMax != null && value.compareTo(critMax) > 0) {
                breachSeverity = AlertSeverity.CRIT;
                breachThreshold = critMax;
            } else if (critMin != null && value.compareTo(critMin) < 0) {
                breachSeverity = AlertSeverity.CRIT;
                breachThreshold = critMin;
            } else if (warnMax != null && value.compareTo(warnMax) > 0) {
                breachSeverity = AlertSeverity.WARN;
                breachThreshold = warnMax;
            } else if (warnMin != null && value.compareTo(warnMin) < 0) {
                breachSeverity = AlertSeverity.WARN;
                breachThreshold = warnMin;
            }

            Optional<Alert> openAlertOpt = alertRepo.findFirstByLab_IdAndMetricColumnAndStatus(labId, metric.column, AlertStatus.ACTIVE);

            // ── A. Recovery Case: Alert is open but value is now within thresholds ──
            if (openAlertOpt.isPresent() && breachSeverity == null) {
                Alert openAlert = openAlertOpt.get();
                openAlert.setStatus(AlertStatus.RESOLVED);
                openAlert.setResolvedAt(OffsetDateTime.now());
                alertRepo.save(openAlert);

                log.info("Alert RESOLVED: {} in lab {} (value: {} is in normal range)", metric.label, lab.getName(), value);
                broadcastResolved(openAlert, metric.label);
                continue;
            }

            // ── B. Upgrade Severity Case: WARN alert exists but has deteriorated to CRIT ──
            if (openAlertOpt.isPresent() && breachSeverity == AlertSeverity.CRIT && openAlertOpt.get().getSeverity() == AlertSeverity.WARN) {
                Alert openAlert = openAlertOpt.get();
                openAlert.setSeverity(AlertSeverity.CRIT);
                openAlert.setTriggerValue(value);
                openAlert.setThresholdValue(breachThreshold);
                openAlert.setTriggeredAt(OffsetDateTime.now()); // reset trigger time for escalation
                alertRepo.save(openAlert);

                log.warn("Alert UPGRADED to CRIT: {} in lab {} (value: {})", metric.label, lab.getName(), value);
                broadcastFired(openAlert, metric.label, lab.getName());
                continue;
            }

            // ── C. New Alert Breach Case ──
            if (breachSeverity != null && breachThreshold != null) {
                if (openAlertOpt.isPresent()) {
                    continue; // alert already active
                }

                // Cooldown Check
                OffsetDateTime cooldownCutoff = OffsetDateTime.now().minusMinutes(cooldownMin);
                Optional<Alert> recentAlertOpt = alertRepo.findFirstByLab_IdAndMetricColumnAndTriggeredAtAfterOrderByTriggeredAtDesc(
                    labId, metric.column, cooldownCutoff
                );
                if (recentAlertOpt.isPresent()) {
                    log.debug("Skipping new alert trigger for {} in lab {} due to active cooldown", metric.label, lab.getName());
                    continue;
                }

                String direction = (critMax != null && value.compareTo(critMax) > 0) || (warnMax != null && value.compareTo(warnMax) > 0)
                    ? "above" : "below";
                String message = String.format("%s is %s threshold: %s (limit %s)", metric.label, direction, value, breachThreshold);

                Alert newAlert = Alert.builder()
                    .id(UUID.randomUUID())
                    .tenantId(tenantId)
                    .lab(lab)
                    .deviceId(deviceId)
                    .alertRuleId(ruleId)
                    .metricColumn(metric.column)
                    .parameter(metric.label)
                    .severity(breachSeverity)
                    .message(message)
                    .triggerValue(value)
                    .thresholdValue(breachThreshold)
                    .status(AlertStatus.ACTIVE)
                    .triggeredAt(OffsetDateTime.now())
                    .isPredicted(false)
                    .build();

                alertRepo.save(newAlert);
                log.warn("Alert FIRED [{}]: {} in lab {}", breachSeverity, message, lab.getName());
                broadcastFired(newAlert, metric.label, lab.getName());
            }
        }
    }

    @Transactional
    public void resolveDeviceOfflineAlert(UUID deviceId, UUID labId) {
        try {
            Optional<Alert> openAlertOpt = alertRepo.findFirstByLab_IdAndMetricColumnAndStatus(labId, "deviceOffline", AlertStatus.ACTIVE);
            if (openAlertOpt.isPresent()) {
                Alert alert = openAlertOpt.get();
                alert.setStatus(AlertStatus.RESOLVED);
                alert.setResolvedAt(OffsetDateTime.now());
                alertRepo.save(alert);

                log.info("Device offline alert RESOLVED for deviceId {}", deviceId);
                broadcastResolved(alert, "Device Offline");
            }
        } catch (Exception e) {
            log.error("Failed to resolve device offline alert for device {}", deviceId, e);
        }
    }

    private BigDecimal getMetricValue(TelemetryReading r, String column) {
        return switch (column) {
            case "temperatureC" -> r.getTemperatureC();
            case "co2Ppm" -> r.getCo2Ppm();
            case "humidityPct" -> r.getHumidityPct();
            case "pm25UgM3" -> r.getPm25UgM3();
            case "tvocPpb" -> r.getTvocPpb();
            default -> null;
        };
    }

    private void broadcastFired(Alert alert, String label, String labName) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("alertId", alert.getId().toString());
            payload.put("labId", alert.getLab().getId().toString());
            payload.put("tenantId", alert.getTenantId().toString());
            payload.put("deviceId", alert.getDeviceId().toString());
            payload.put("metricColumn", alert.getMetricColumn());
            payload.put("label", label);
            payload.put("severity", alert.getSeverity().name());
            payload.put("message", alert.getMessage());
            payload.put("triggerValue", alert.getTriggerValue().doubleValue());
            payload.put("thresholdValue", alert.getThresholdValue().doubleValue());
            payload.put("triggeredAt", alert.getTriggeredAt().toString());

            String json = objectMapper.writeValueAsString(payload);
            // Escape single quotes for SQL safety
            String escapedJson = json.replace("'", "''");
            jdbcTemplate.execute("SELECT pg_notify('alert_fired', '" + escapedJson + "')");
        } catch (Exception e) {
            log.error("Failed to broadcast alert_fired notification", e);
        }
    }

    private void broadcastResolved(Alert alert, String label) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("alertId", alert.getId().toString());
            payload.put("labId", alert.getLab().getId().toString());
            payload.put("metricColumn", alert.getMetricColumn());
            payload.put("label", label);
            payload.put("resolvedAt", alert.getResolvedAt().toString());

            String json = objectMapper.writeValueAsString(payload);
            String escapedJson = json.replace("'", "''");
            jdbcTemplate.execute("SELECT pg_notify('alert_resolved', '" + escapedJson + "')");
        } catch (Exception e) {
            log.error("Failed to broadcast alert_resolved notification", e);
        }
    }

    private static class MetricMetadata {
        final String column;
        final String label;
        final SensorType sensorType;
        final String fallbackWarnKey;
        final String fallbackCritKey;

        MetricMetadata(String column, String label, SensorType sensorType, String fallbackWarnKey, String fallbackCritKey) {
            this.column = column;
            this.label = label;
            this.sensorType = sensorType;
            this.fallbackWarnKey = fallbackWarnKey;
            this.fallbackCritKey = fallbackCritKey;
        }
    }
}
