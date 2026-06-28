package com.sael.service;

import com.sael.domain.entity.*;
import com.sael.domain.enums.NotificationChannel;
import com.sael.domain.enums.ScopeEntityType;
import com.sael.domain.repository.*;
import com.sael.exception.ResourceNotFoundException;
import com.sael.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ConfigService {
    private final SystemConfigRepository systemConfigRepo;
    private final HospitalRepository hospitalRepo;
    private final NotificationRecipientRepository recipientRepo;
    private final UserScopeRepository userScopeRepo;
    private final TenantRepository tenantRepo;
    private final NetworkRepository networkRepo;

    @Transactional(readOnly = true)
    public Map<String, Object> getConfig() {
        UUID tenantId = TenantContext.requireTenantId();

        var thresholdsConfig = systemConfigRepo.findByTenantIdAndKey(tenantId, "thresholds").orElse(null);
        Map<String, Object> dbThresholds = thresholdsConfig != null ? thresholdsConfig.getValue() : Map.of();

        var togglesConfig = systemConfigRepo.findByTenantIdAndKey(tenantId, "alertToggles").orElse(null);
        if (togglesConfig == null) {
            togglesConfig = systemConfigRepo.findByTenantIdAndKey(tenantId, "alert_toggles").orElse(null);
        }
        Map<String, Object> dbToggles = togglesConfig != null ? togglesConfig.getValue() : Map.of();

        var networkConfig = systemConfigRepo.findByTenantIdAndKey(tenantId, "network_info").orElse(null);
        String networkName = "Unknown Network";
        if (networkConfig != null && networkConfig.getValue() != null) {
            var nameVal = networkConfig.getValue().get("name");
            if (nameVal != null) {
                networkName = nameVal.toString();
            }
        }
        if (networkName.equals("Unknown Network")) {
            var tenant = tenantRepo.findById(tenantId).orElse(null);
            if (tenant != null) {
                networkName = tenant.getName();
            }
        }

        List<Network> nets = networkRepo.findAllByTenant_IdAndDeletedAtIsNull(tenantId);
        String networkId = nets.isEmpty() ? null : nets.get(0).getId().toString();
        String timezone = nets.isEmpty() ? "Asia/Kolkata" : nets.get(0).getTimezone();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("networkId", networkId);
        result.put("networkName", networkName);
        result.put("timezone", timezone);
        result.put("thresholds", mapToFrontendThresholds(dbThresholds));
        result.put("toggles", mapToFrontendToggles(dbToggles));
        return result;
    }

    @Transactional
    public void updateThresholds(List<Map<String, Object>> thresholdsList) {
        UUID tenantId = TenantContext.requireTenantId();
        
        var cfg = systemConfigRepo.findByTenantIdAndKey(tenantId, "thresholds")
            .orElseGet(() -> SystemConfig.builder().tenantId(tenantId).key("thresholds").value(new LinkedHashMap<>()).build());

        Map<String, Object> dbThresholds = new LinkedHashMap<>(cfg.getValue());

        for (var t : thresholdsList) {
            String param = (String) t.get("parameter");
            if (param == null) continue;
            Object warn = t.get("warnValue");
            Object crit = t.get("critValue");
            switch (param.toLowerCase()) {
                case "temperature":
                    if (warn != null) dbThresholds.put("temp_warn", Double.parseDouble(warn.toString()));
                    if (crit != null) dbThresholds.put("temp_crit", Double.parseDouble(crit.toString()));
                    break;
                case "co2":
                    if (warn != null) dbThresholds.put("co2_warn", Double.parseDouble(warn.toString()));
                    if (crit != null) dbThresholds.put("co2_crit", Double.parseDouble(crit.toString()));
                    break;
                case "humidity":
                    if (warn != null) dbThresholds.put("hum_warn", Double.parseDouble(warn.toString()));
                    if (crit != null) dbThresholds.put("hum_crit", Double.parseDouble(crit.toString()));
                    break;
                case "pm25":
                    if (warn != null) dbThresholds.put("pm_warn", Double.parseDouble(warn.toString()));
                    if (crit != null) dbThresholds.put("pm_crit", Double.parseDouble(crit.toString()));
                    break;
                case "voc":
                    if (warn != null) dbThresholds.put("voc_warn", Double.parseDouble(warn.toString()));
                    if (crit != null) dbThresholds.put("voc_crit", Double.parseDouble(crit.toString()));
                    break;
            }
        }

        cfg.setValue(dbThresholds);
        systemConfigRepo.save(cfg);
    }

    @Transactional
    public void updateToggles(Map<String, Object> togglesMap, String networkName) {
        UUID tenantId = TenantContext.requireTenantId();

        for (String key : new String[]{"alertToggles", "alert_toggles"}) {
            var cfg = systemConfigRepo.findByTenantIdAndKey(tenantId, key)
                .orElseGet(() -> SystemConfig.builder().tenantId(tenantId).key(key).value(new LinkedHashMap<>()).build());
            
            Map<String, Object> val = new LinkedHashMap<>(cfg.getValue());
            val.put("wa", togglesMap.getOrDefault("wa", true));
            val.put("predict", togglesMap.getOrDefault("predictive", true));
            val.put("daily", togglesMap.getOrDefault("daily", false));
            val.put("wearables", togglesMap.getOrDefault("wearable", true));
            val.put("llm", togglesMap.getOrDefault("siaInMessages", true));
            
            cfg.setValue(val);
            systemConfigRepo.save(cfg);
        }

        if (networkName != null) {
            var cfg = systemConfigRepo.findByTenantIdAndKey(tenantId, "network_info")
                .orElseGet(() -> SystemConfig.builder().tenantId(tenantId).key("network_info").value(new LinkedHashMap<>()).build());
            
            Map<String, Object> val = new LinkedHashMap<>(cfg.getValue());
            val.put("name", networkName);
            cfg.setValue(val);
            systemConfigRepo.save(cfg);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCentres() {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.getUserId();

        final List<UUID> allowedLabIds;
        if (userId != null) {
            var scopes = userScopeRepo.findAllByUser_Id(userId);
            var labScopes = scopes.stream()
                .filter(s -> s.getEntityType() == ScopeEntityType.LAB)
                .map(UserScope::getEntityId)
                .toList();
            if (!scopes.isEmpty() && !labScopes.isEmpty()) {
                allowedLabIds = labScopes;
            } else {
                allowedLabIds = null;
            }
        } else {
            allowedLabIds = null;
        }

        List<Hospital> hospitals = hospitalRepo.findAllByTenant_IdAndDeletedAtIsNull(tenantId);
        var sortedHospitals = hospitals.stream()
            .sorted(Comparator.comparing(Hospital::getName))
            .toList();

        var result = new ArrayList<Map<String, Object>>();

        for (var h : sortedHospitals) {
            var labsList = new ArrayList<Map<String, Object>>();
            
            var sortedLabs = h.getLabs().stream()
                .filter(l -> l.getDeletedAt() == null && l.getTenant().getId().equals(tenantId))
                .filter(l -> allowedLabIds == null || allowedLabIds.contains(l.getId()))
                .sorted(Comparator.comparing(Lab::getName))
                .toList();

            for (var l : sortedLabs) {
                var labMap = new LinkedHashMap<String, Object>();
                labMap.put("id", l.getId().toString());
                labMap.put("name", l.getName());
                labMap.put("hospitalId", h.getId().toString());

                var devices = l.getDevices().stream()
                    .filter(d -> d.getTenantId().equals(tenantId))
                    .toList();
                
                var devicesList = new ArrayList<Map<String, Object>>();
                if (!devices.isEmpty()) {
                    Device dev = devices.get(0);
                    var devMap = new LinkedHashMap<String, Object>();
                    devMap.put("id", dev.getId().toString());
                    devMap.put("deviceUid", dev.getDeviceUid());
                    devMap.put("onlineStatus", dev.getOnlineStatus());
                    devMap.put("lastSeenAt", dev.getLastSeenAt() != null ? dev.getLastSeenAt().toString() : null);
                    devicesList.add(devMap);
                }
                labMap.put("devices", devicesList);
                labsList.add(labMap);
            }

            var hospitalMap = new LinkedHashMap<String, Object>();
            hospitalMap.put("id", h.getId().toString());
            hospitalMap.put("name", h.getName());
            hospitalMap.put("city", h.getCity() != null ? h.getCity() : "");
            hospitalMap.put("labs", labsList);

            result.add(hospitalMap);
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRecipients() {
        UUID tenantId = TenantContext.requireTenantId();
        List<NotificationRecipient> list = recipientRepo.findAllByTenantIdAndChannelOrderByCreatedAtAsc(tenantId, NotificationChannel.WHATSAPP);
        List<Map<String, Object>> frontendList = new ArrayList<>();
        for (var r : list) {
            frontendList.add(mapToFrontendRecipient(r));
        }
        return frontendList;
    }

    @Transactional
    public Map<String, Object> addRecipient(String address, String label) {
        UUID tenantId = TenantContext.requireTenantId();
        
        Optional<NotificationRecipient> existing = recipientRepo.findByTenantIdAndChannelAndAddress(tenantId, NotificationChannel.WHATSAPP, address);
        NotificationRecipient r;
        if (existing.isPresent()) {
            r = existing.get();
            r.setLabel(label);
            r.setIsActive(true);
        } else {
            r = NotificationRecipient.builder()
                .tenantId(tenantId)
                .channel(NotificationChannel.WHATSAPP)
                .address(address)
                .label(label)
                .isActive(true)
                .build();
        }
        return mapToFrontendRecipient(recipientRepo.save(r));
    }

    @Transactional
    public Map<String, Object> toggleRecipient(UUID id, boolean isActive) {
        UUID tenantId = TenantContext.requireTenantId();
        NotificationRecipient r = recipientRepo.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Recipient not found"));
        r.setIsActive(isActive);
        return mapToFrontendRecipient(recipientRepo.save(r));
    }

    @Transactional
    public void deleteRecipient(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        NotificationRecipient r = recipientRepo.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Recipient not found"));
        recipientRepo.delete(r);
    }

    private List<Map<String, Object>> mapToFrontendThresholds(Map<String, Object> dbThresholds) {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(makeThreshold("temperature", "°C", dbThresholds.getOrDefault("temp_warn", 37.3), dbThresholds.getOrDefault("temp_crit", 37.5)));
        list.add(makeThreshold("co2", "ppm", dbThresholds.getOrDefault("co2_warn", 5.5), dbThresholds.getOrDefault("co2_crit", 6.0)));
        list.add(makeThreshold("humidity", "% RH", dbThresholds.getOrDefault("hum_warn", 58.0), dbThresholds.getOrDefault("hum_crit", 62.0)));
        list.add(makeThreshold("pm25", "μg/m³", dbThresholds.getOrDefault("pm_warn", 5.0), dbThresholds.getOrDefault("pm_crit", 8.0)));
        list.add(makeThreshold("voc", "ppb", dbThresholds.getOrDefault("voc_warn", 50.0), dbThresholds.getOrDefault("voc_crit", 80.0)));
        return list;
    }

    private Map<String, Object> makeThreshold(String param, String unit, Object warn, Object crit) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("parameter", param);
        m.put("unit", unit);
        m.put("warnValue", warn);
        m.put("critValue", crit);
        return m;
    }

    private Map<String, Object> mapToFrontendToggles(Map<String, Object> dbToggles) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("wa", dbToggles.getOrDefault("wa", true));
        m.put("predictive", dbToggles.getOrDefault("predict", true));
        m.put("daily", dbToggles.getOrDefault("daily", false));
        m.put("wearable", dbToggles.getOrDefault("wearables", true));
        m.put("siaInMessages", dbToggles.getOrDefault("llm", true));
        return m;
    }

    private Map<String, Object> mapToFrontendRecipient(NotificationRecipient r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId().toString());
        m.put("label", r.getLabel());
        m.put("phoneNumber", r.getAddress());
        m.put("isActive", r.getIsActive());
        return m;
    }
}
