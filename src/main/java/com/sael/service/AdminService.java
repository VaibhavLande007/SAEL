package com.sael.service;
import com.sael.domain.entity.*;
import com.sael.domain.enums.*;
import com.sael.domain.repository.*;
import com.sael.exception.*;
import com.sael.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class AdminService {
    private final TenantRepository tenantRepo;
    private final UserRepository userRepo;
    private final LabRepository labRepo;
    private final DeviceRepository deviceRepo;
    private final AlertRepository alertRepo;
    private final RoleRepository roleRepo;
    private final UserRoleRepository userRoleRepo;
    private final PasswordEncoder encoder;

    private final NetworkRepository networkRepo;
    private final HospitalRepository hospitalRepo;
    private final SystemConfigRepository systemConfigRepo;
    private final NotificationRecipientRepository recipientRepo;
    private final AlertRuleRepository ruleRepo;
    private final SensorRepository sensorRepo;
    private final TelemetryRepository telemetryRepo;

    private static final Map<String, Object> DEFAULT_THRESHOLDS = Map.of(
        "temp_warn", 37.3, "temp_crit", 37.5,
        "co2_warn", 5.5,  "co2_crit", 6.0,
        "hum_warn", 58.0,  "hum_crit", 62.0,
        "pm_warn", 5.0,   "pm_crit", 8.0,
        "voc_warn", 50.0,  "voc_crit", 80.0
    );

    private static final Map<String, Object> DEFAULT_TOGGLES = Map.of(
        "wa", true, "predict", true, "daily", false, "wearables", true, "llm", true
    );

    public Map<String,Object> platformOverview(){
        long totalTenants=tenantRepo.count();
        long activeTenants=tenantRepo.findAll().stream()
            .filter(t->t.getStatus()==TenantStatus.ACTIVE&&t.getDeletedAt()==null).count();
        long totalLabs=labRepo.countByDeletedAtIsNull();
        long totalDevices=deviceRepo.count();
        long onlineDevices=deviceRepo.countByOnlineStatus(true); // platform-wide
        OffsetDateTime since=OffsetDateTime.now().minusHours(24);

        var resp=new LinkedHashMap<String,Object>();
        resp.put("tenants",Map.of("total",totalTenants,"active",activeTenants));
        resp.put("networks",Map.of("total",0));
        resp.put("hospitals",Map.of("total",0));
        resp.put("labs",Map.of("total",totalLabs,"online",totalLabs,"offline",0));
        resp.put("devices",Map.of("total",totalDevices,"online",onlineDevices,"offline",totalDevices-onlineDevices));
        resp.put("users",Map.of("total",userRepo.count(),"activeLastDays",0));
        resp.put("alertsLast24h",Map.of("total",0,"critical",0,"warning",0,"acknowledged",0,"unacknowledged",0));
        return resp;
    }

    public Map<String, Object> getPlatformStats() {
        long tenantCount = tenantRepo.count();
        long labCount = labRepo.countByDeletedAtIsNull();
        long onlineDevices = deviceRepo.countByOnlineStatus(true);
        long activeAlerts = alertRepo.countByStatus(AlertStatus.ACTIVE);
        return Map.of(
            "tenantCount", tenantCount,
            "labCount", labCount,
            "onlineDevices", onlineDevices,
            "activeAlerts", activeAlerts
        );
    }

    public boolean tenantsExist(){
        return tenantRepo.count()>0;
    }

    public Page<Map<String,Object>> listTenants(Pageable p){
        return tenantRepo.findAll(p).map(this::tenantMap);
    }

    @Transactional
    public Map<String,Object> createTenant(String name,String slug,String adminName,String adminEmail,String adminPassword){
        if(tenantRepo.existsBySlug(slug))
            throw new ConflictException("Slug already exists: "+slug);
        var tenant=Tenant.builder().name(name).slug(slug).status(TenantStatus.ACTIVE).plan(TenantPlan.STARTER).build();
        tenant=tenantRepo.save(tenant);
        // Create NETWORK_ADMIN user
        var adminUser=User.builder().tenant(tenant).fullName(adminName)
            .email(adminEmail.toLowerCase()).passwordHash(encoder.encode(adminPassword)).isActive(true).build();
        adminUser=userRepo.save(adminUser);
        // Assign network_admin role
        var role=roleRepo.findByNameAndTenantIdIsNull("NETWORK_ADMIN")
            .orElseThrow(()->new ResourceNotFoundException("Role NETWORK_ADMIN not found in roles table — ensure you ran docs/run_once_on_db.sql first"));
        userRoleRepo.save(UserRole.builder().userId(adminUser.getId()).roleId(role.getId()).build());
        var resp=tenantMap(tenant);
        resp.put("adminUser",Map.of("id",adminUser.getId(),"name",adminUser.getFullName(),"email",adminUser.getEmail()));
        return resp;
    }

    @Transactional
    public Map<String,Object> updateTenant(UUID tenantId,String name,Boolean isActive){
        var tenant=tenantRepo.findById(tenantId)
            .orElseThrow(()->new ResourceNotFoundException("Tenant not found"));
        if(name!=null)tenant.setName(name);
        if(isActive!=null)tenant.setStatus(isActive?TenantStatus.ACTIVE:TenantStatus.SUSPENDED);
        return tenantMap(tenantRepo.save(tenant));
    }

    private LinkedHashMap<String,Object> tenantMap(Tenant t){
        var m=new LinkedHashMap<String,Object>();
        m.put("id",t.getId());m.put("name",t.getName());m.put("slug",t.getSlug());
        m.put("isActive",t.getStatus()==TenantStatus.ACTIVE);
        m.put("networksCount",0);m.put("labsCount",0);m.put("usersCount",0);
        m.put("createdAt",t.getCreatedAt());m.put("lastActivityAt",t.getUpdatedAt());
        return m;
    }

    // ── Admin-facing Network endpoints ───────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAllNetworks() {
        var tenants = tenantRepo.findAll();
        var respList = new ArrayList<Map<String, Object>>();
        for (var t : tenants) {
            if (t.getDeletedAt() != null) continue;
            
            var network = t.getNetworks().isEmpty() ? null : t.getNetworks().get(0);
            var hospitals = network != null ? network.getHospitals() : List.<Hospital>of();
            
            long hospitalCount = hospitals.stream().filter(h -> h.getDeletedAt() == null).count();
            long labCount = 0;
            long deviceCount = 0;
            long onlineDevices = 0;
            
            for (var h : hospitals) {
                if (h.getDeletedAt() != null) continue;
                for (var l : h.getLabs()) {
                    if (l.getDeletedAt() != null) continue;
                    labCount++;
                    for (var d : l.getDevices()) {
                        deviceCount++;
                        if (Boolean.TRUE.equals(d.getOnlineStatus())) {
                            onlineDevices++;
                        }
                    }
                }
            }
            
            var m = new LinkedHashMap<String, Object>();
            m.put("tenantId", t.getId());
            m.put("tenantName", t.getName());
            m.put("tenantStatus", t.getStatus().name());
            m.put("networkId", network != null ? network.getId() : null);
            m.put("networkName", network != null ? network.getName() : t.getName());
            m.put("timezone", network != null ? network.getTimezone() : "Asia/Kolkata");
            m.put("hospitalCount", hospitalCount);
            m.put("labCount", labCount);
            m.put("deviceCount", deviceCount);
            m.put("onlineDevices", onlineDevices);
            m.put("createdAt", t.getCreatedAt());
            respList.add(m);
        }
        return respList;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getNetworkDetail(UUID networkId) {
        var network = networkRepo.findById(networkId)
            .orElseThrow(() -> new ResourceNotFoundException("Network not found"));
        
        var tenant = network.getTenant();
        
        var hospitalsList = new ArrayList<Map<String, Object>>();
        for (var h : network.getHospitals()) {
            if (h.getDeletedAt() != null) continue;
            
            var labsList = new ArrayList<Map<String, Object>>();
            for (var l : h.getLabs()) {
                if (l.getDeletedAt() != null) continue;
                
                var devicesList = new ArrayList<Map<String, Object>>();
                for (var d : l.getDevices()) {
                    var devMap = new LinkedHashMap<String, Object>();
                    devMap.put("id", d.getId());
                    devMap.put("deviceUid", d.getDeviceUid());
                    devMap.put("onlineStatus", d.getOnlineStatus());
                    devMap.put("lastSeenAt", d.getLastSeenAt());
                    devicesList.add(devMap);
                }
                
                var labMap = new LinkedHashMap<String, Object>();
                labMap.put("id", l.getId());
                labMap.put("name", l.getName());
                labMap.put("labCode", l.getId().toString().substring(0, 6).toUpperCase());
                labMap.put("labType", l.getLabType() != null ? l.getLabType().name() : "IVF");
                labMap.put("operationalStatus", l.getOperationalStatus() != null ? l.getOperationalStatus().name() : "ACTIVE");
                labMap.put("timezone", l.getTimezone());
                labMap.put("devices", devicesList);
                labsList.add(labMap);
            }
            
            var hospMap = new LinkedHashMap<String, Object>();
            hospMap.put("id", h.getId());
            hospMap.put("name", h.getName());
            hospMap.put("city", h.getCity());
            hospMap.put("state", h.getState());
            hospMap.put("country", h.getCountry());
            hospMap.put("address", h.getAddress());
            hospMap.put("phone", h.getPhone());
            hospMap.put("labs", labsList);
            hospitalsList.add(hospMap);
        }
        
        var configsList = systemConfigRepo.findAllByTenantId(tenant.getId());
        var configsMap = new LinkedHashMap<String, Object>();
        for (var c : configsList) {
            configsMap.put(c.getKey(), c.getValue());
        }
        
        var resp = new LinkedHashMap<String, Object>();
        resp.put("id", network.getId());
        resp.put("name", network.getName());
        resp.put("timezone", network.getTimezone());
        resp.put("tenantId", tenant.getId());
        resp.put("tenant", Map.of(
            "id", tenant.getId(),
            "name", tenant.getName(),
            "slug", tenant.getSlug(),
            "status", tenant.getStatus().name(),
            "plan", tenant.getPlan().name()
        ));
        resp.put("hospitals", hospitalsList);
        resp.put("configs", configsMap);
        return resp;
    }

    @Transactional
    public Map<String, Object> createNetwork(Map<String, Object> body) {
        String networkName = (String) body.get("networkName");
        String tenantName = (String) body.getOrDefault("tenantName", networkName);
        String tz = (String) body.getOrDefault("timezone", "Asia/Kolkata");

        String slug = networkName.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
        if (slug.length() > 50) slug = slug.substring(0, 50);
        slug += "-" + Long.toString(System.currentTimeMillis(), 36);

        var tenant = Tenant.builder()
            .name(tenantName)
            .slug(slug)
            .status(TenantStatus.ACTIVE)
            .plan(TenantPlan.STARTER)
            .build();
        tenant = tenantRepo.save(tenant);

        var network = Network.builder()
            .tenant(tenant)
            .name(networkName)
            .timezone(tz)
            .build();
        network = networkRepo.save(network);

        systemConfigRepo.save(SystemConfig.builder().tenantId(tenant.getId()).key("network_info").value(Map.of("name", networkName)).build());
        systemConfigRepo.save(SystemConfig.builder().tenantId(tenant.getId()).key("alert_toggles").value(DEFAULT_TOGGLES).build());
        systemConfigRepo.save(SystemConfig.builder().tenantId(tenant.getId()).key("thresholds").value(DEFAULT_THRESHOLDS).build());

        return Map.of("tenant", tenant, "network", network);
    }

    @Transactional
    public Map<String, Object> updateNetwork(UUID networkId, Map<String, Object> body) {
        var network = networkRepo.findById(networkId)
            .orElseThrow(() -> new ResourceNotFoundException("Network not found"));

        String networkName = (String) body.get("networkName");
        String timezone = (String) body.get("timezone");
        String tenantName = (String) body.get("tenantName");

        if (networkName != null) {
            network.setName(networkName);
            var infoOpt = systemConfigRepo.findByTenantIdAndKey(network.getTenant().getId(), "network_info");
            if (infoOpt.isPresent()) {
                var info = infoOpt.get();
                var val = new LinkedHashMap<>(info.getValue());
                val.put("name", networkName);
                info.setValue(val);
                systemConfigRepo.save(info);
            }
        }
        if (timezone != null) {
            network.setTimezone(timezone);
        }
        network = networkRepo.save(network);

        if (tenantName != null) {
            var tenant = network.getTenant();
            tenant.setName(tenantName);
            tenantRepo.save(tenant);
        }

        return Map.of("id", network.getId(), "name", network.getName(), "timezone", network.getTimezone());
    }

    // ── Admin-facing Hospital endpoints ──────────────────────────────────────

    @Transactional
    public Map<String, Object> createHospital(UUID networkId, Map<String, Object> body) {
        var network = networkRepo.findById(networkId)
            .orElseThrow(() -> new ResourceNotFoundException("Network not found"));
        
        String name = (String) body.get("name");
        String city = (String) body.get("city");
        String state = (String) body.get("state");
        String country = (String) body.getOrDefault("country", "IN");
        String address = (String) body.get("address");
        String phone = (String) body.get("phone");

        var hosp = Hospital.builder()
            .tenant(network.getTenant())
            .network(network)
            .name(name)
            .city(city)
            .state(state)
            .country(country)
            .address(address)
            .phone(phone)
            .build();
        hosp = hospitalRepo.save(hosp);

        return hospitalMap(hosp);
    }

    @Transactional
    public Map<String, Object> updateHospital(UUID hospitalId, Map<String, Object> body) {
        var hosp = hospitalRepo.findByIdAndDeletedAtIsNull(hospitalId)
            .orElseThrow(() -> new ResourceNotFoundException("Hospital not found"));
        
        if (body.containsKey("name")) hosp.setName((String) body.get("name"));
        if (body.containsKey("city")) hosp.setCity((String) body.get("city"));
        if (body.containsKey("state")) hosp.setState((String) body.get("state"));
        if (body.containsKey("country")) hosp.setCountry((String) body.get("country"));
        if (body.containsKey("address")) hosp.setAddress((String) body.get("address"));
        if (body.containsKey("phone")) hosp.setPhone((String) body.get("phone"));

        hosp = hospitalRepo.save(hosp);
        return hospitalMap(hosp);
    }

    @Transactional
    public void deleteHospital(UUID hospitalId) {
        var hosp = hospitalRepo.findByIdAndDeletedAtIsNull(hospitalId)
            .orElseThrow(() -> new ResourceNotFoundException("Hospital not found"));
        if (hosp.getLabs().stream().anyMatch(l -> l.getDeletedAt() == null)) {
            throw new ConflictException("Remove all labs before deleting this hospital");
        }
        hosp.setDeletedAt(OffsetDateTime.now());
        hospitalRepo.save(hosp);
    }

    private Map<String, Object> hospitalMap(Hospital h) {
        var m = new LinkedHashMap<String, Object>();
        m.put("id", h.getId());
        m.put("name", h.getName());
        m.put("city", h.getCity());
        m.put("state", h.getState());
        m.put("country", h.getCountry());
        m.put("address", h.getAddress());
        m.put("phone", h.getPhone());
        m.put("networkId", h.getNetwork().getId());
        return m;
    }

    // ── Admin-facing Lab endpoints ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getLabDetail(UUID labId) {
        var lab = labRepo.findByIdAndDeletedAtIsNull(labId)
            .orElseThrow(() -> new ResourceNotFoundException("Lab not found"));
        
        var recentList = telemetryRepo.findHistory(labId, OffsetDateTime.now().minusHours(24), OffsetDateTime.now());
        var recentReadings = new ArrayList<Map<String, Object>>();
        int count = 0;
        for (var row : recentList) {
            if (count >= 10) break;
            if (row instanceof Object[] arr && arr.length > 9) {
                var pt = new LinkedHashMap<String, Object>();
                pt.put("id", arr[0].toString());
                pt.put("temperatureC", arr[4]);
                pt.put("co2Ppm", arr[2]);
                pt.put("humidityPct", arr[5]);
                pt.put("pm25UgM3", arr[6]);
                pt.put("tvocPpb", arr[3]);
                pt.put("doorIsOpen", arr[9]);
                pt.put("recordedAt", arr[1].toString());
                recentReadings.add(pt);
                count++;
            }
        }

        var devicesList = new ArrayList<Map<String, Object>>();
        for (var d : lab.getDevices()) {
            var sensorsList = new ArrayList<Map<String, Object>>();
            for (var s : d.getSensors()) {
                sensorsList.add(Map.of(
                    "id", s.getId(),
                    "sensorType", s.getSensorType().name(),
                    "status", s.getStatus().name()
                ));
            }
            var devMap = new LinkedHashMap<String, Object>();
            devMap.put("id", d.getId());
            devMap.put("deviceUid", d.getDeviceUid());
            devMap.put("onlineStatus", d.getOnlineStatus());
            devMap.put("lastSeenAt", d.getLastSeenAt());
            devMap.put("firmwareVersion", d.getFirmwareVersion());
            devMap.put("hardwareVersion", null);
            devMap.put("sensors", sensorsList);
            devicesList.add(devMap);
        }

        var res = new LinkedHashMap<String, Object>();
        res.put("id", lab.getId());
        res.put("name", lab.getName());
        res.put("labType", lab.getLabType().name());
        res.put("operationalStatus", lab.getOperationalStatus().name());
        res.put("timezone", lab.getTimezone());
        res.put("tenantId", lab.getTenant().getId());
        res.put("hospital", Map.of(
            "id", lab.getHospital().getId(),
            "name", lab.getHospital().getName(),
            "network", Map.of(
                "id", lab.getHospital().getNetwork().getId(),
                "name", lab.getHospital().getNetwork().getName()
            )
        ));
        res.put("devices", devicesList);
        res.put("recentReadings", recentReadings);
        return res;
    }

    @Transactional
    public Map<String, Object> createLab(UUID hospitalId, Map<String, Object> body) {
        var hosp = hospitalRepo.findByIdAndDeletedAtIsNull(hospitalId)
            .orElseThrow(() -> new ResourceNotFoundException("Hospital not found"));
        
        String name = (String) body.get("name");
        String typeStr = (String) body.getOrDefault("labType", "IVF");
        String tz = (String) body.get("timezone");

        var lab = Lab.builder()
            .tenant(hosp.getTenant())
            .hospital(hosp)
            .name(name)
            .labType(LabType.valueOf(typeStr.toUpperCase()))
            .timezone(tz)
            .operationalStatus(OperationalStatus.ACTIVE)
            .build();
        lab = labRepo.save(lab);

        seedLabAlertRules(lab.getId(), hosp.getTenant().getId());
        return labMap(lab);
    }

    @Transactional
    public Map<String, Object> updateLab(UUID labId, Map<String, Object> body) {
        var lab = labRepo.findByIdAndDeletedAtIsNull(labId)
            .orElseThrow(() -> new ResourceNotFoundException("Lab not found"));

        if (body.containsKey("name")) lab.setName((String) body.get("name"));
        if (body.containsKey("labType")) lab.setLabType(LabType.valueOf(body.get("labType").toString().toUpperCase()));
        if (body.containsKey("timezone")) lab.setTimezone((String) body.get("timezone"));
        if (body.containsKey("operationalStatus")) lab.setOperationalStatus(OperationalStatus.valueOf(body.get("operationalStatus").toString().toUpperCase()));

        lab = labRepo.save(lab);
        return labMap(lab);
    }

    @Transactional
    public void deleteLab(UUID labId) {
        var lab = labRepo.findByIdAndDeletedAtIsNull(labId)
            .orElseThrow(() -> new ResourceNotFoundException("Lab not found"));
        if (!lab.getDevices().isEmpty()) {
            throw new ConflictException("Remove the device before deleting this lab");
        }
        lab.setDeletedAt(OffsetDateTime.now());
        labRepo.save(lab);
    }

    private Map<String, Object> labMap(Lab l) {
        var m = new LinkedHashMap<String, Object>();
        m.put("id", l.getId());
        m.put("name", l.getName());
        m.put("labType", l.getLabType().name());
        m.put("operationalStatus", l.getOperationalStatus().name());
        m.put("timezone", l.getTimezone());
        m.put("hospitalId", l.getHospital().getId());
        m.put("createdAt", l.getCreatedAt());
        return m;
    }

    @Transactional
    public void seedLabAlertRules(UUID labId, UUID tenantId) {
        long count = ruleRepo.findByTenantIdAndScopeIdAndIsActiveTrue(tenantId, labId).size();
        if (count > 0) return;

        var cfg = systemConfigRepo.findByTenantIdAndKey(tenantId, "thresholds").orElse(null);
        Map<String, Object> thresholds = cfg != null ? cfg.getValue() : DEFAULT_THRESHOLDS;

        var defs = List.of(
            Map.of("sensorType", SensorType.TEMPERATURE, "col", "temperatureC", "label", "Temperature", "unit", "°C", "warnKey", "temp_warn", "critKey", "temp_crit"),
            Map.of("sensorType", SensorType.CO2, "col", "co2Ppm", "label", "CO₂", "unit", "%", "warnKey", "co2_warn", "critKey", "co2_crit"),
            Map.of("sensorType", SensorType.HUMIDITY, "col", "humidityPct", "label", "Humidity", "unit", "%", "warnKey", "hum_warn", "critKey", "hum_crit"),
            Map.of("sensorType", SensorType.PM25, "col", "pm25UgM3", "label", "PM2.5", "unit", "µg/m³", "warnKey", "pm_warn", "critKey", "pm_crit"),
            Map.of("sensorType", SensorType.VOC, "col", "tvocPpb", "label", "VOC", "unit", "ppb", "warnKey", "voc_warn", "critKey", "voc_crit")
        );

        for (var def : defs) {
            SensorType st = (SensorType) def.get("sensorType");
            String col = (String) def.get("col");
            String label = (String) def.get("label");
            String unit = (String) def.get("unit");
            String warnKey = (String) def.get("warnKey");
            String critKey = (String) def.get("critKey");

            BigDecimal warnMax = thresholds.containsKey(warnKey) ? new BigDecimal(thresholds.get(warnKey).toString()) : null;
            BigDecimal critMax = thresholds.containsKey(critKey) ? new BigDecimal(thresholds.get(critKey).toString()) : null;

            AlertRule rule = AlertRule.builder()
                .tenantId(tenantId)
                .scopeType(AlertScopeType.LAB)
                .scopeId(labId)
                .sensorType(st)
                .ruleName(label + " — " + unit)
                .warnMin(null)
                .warnMax(warnMax)
                .critMin(null)
                .critMax(critMax)
                .cooldownMin(5)
                .isActive(true)
                .build();
            ruleRepo.save(rule);
        }
    }

    // ── Admin-facing Device endpoints ────────────────────────────────────────

    @Transactional
    public Map<String, Object> registerDevice(UUID labId, Map<String, Object> body) {
        var lab = labRepo.findByIdAndDeletedAtIsNull(labId)
            .orElseThrow(() -> new ResourceNotFoundException("Lab not found"));
        
        String deviceUid = (String) body.get("deviceUid");
        if (deviceRepo.findByDeviceUid(deviceUid).isPresent()) {
            throw new ConflictException("Device UID already registered: " + deviceUid);
        }

        String mqttClient = (String) body.getOrDefault("mqttClientId", "device-" + deviceUid);
        String firmware = (String) body.getOrDefault("firmwareVersion", "1.0.0");
        String hardware = (String) body.get("hardwareVersion");

        var device = Device.builder()
            .tenantId(lab.getTenant().getId())
            .lab(lab)
            .deviceUid(deviceUid)
            .mqttClientId(mqttClient)
            .firmwareVersion(firmware)
            .onlineStatus(false)
            .registeredAt(OffsetDateTime.now())
            .build();
        device = deviceRepo.save(device);

        var sensors = List.of(
            SensorType.TEMPERATURE, SensorType.CO2, SensorType.HUMIDITY, SensorType.PM25, SensorType.VOC
        );
        for (var st : sensors) {
            Sensor s = Sensor.builder()
                .tenantId(lab.getTenant().getId())
                .device(device)
                .sensorType(st)
                .status(SensorStatus.ACTIVE)
                .build();
            sensorRepo.save(s);
        }

        return deviceMap(device);
    }

    @Transactional
    public Map<String, Object> updateDevice(UUID deviceId, Map<String, Object> body) {
        var device = deviceRepo.findById(deviceId)
            .orElseThrow(() -> new ResourceNotFoundException("Device not found"));

        if (body.containsKey("mqttClientId")) device.setMqttClientId((String) body.get("mqttClientId"));
        if (body.containsKey("firmwareVersion")) device.setFirmwareVersion((String) body.get("firmwareVersion"));

        device = deviceRepo.save(device);
        return deviceMap(device);
    }

    @Transactional
    public void deleteDevice(UUID deviceId) {
        var device = deviceRepo.findById(deviceId)
            .orElseThrow(() -> new ResourceNotFoundException("Device not found"));
        
        // Remove associated sensors
        sensorRepo.deleteAll(device.getSensors());
        deviceRepo.delete(device);
    }

    private Map<String, Object> deviceMap(Device d) {
        var m = new LinkedHashMap<String, Object>();
        m.put("id", d.getId());
        m.put("deviceUid", d.getDeviceUid());
        m.put("labId", d.getLab() != null ? d.getLab().getId() : null);
        m.put("labName", d.getLab() != null ? d.getLab().getName() : "");
        m.put("name", d.getMqttClientId());
        m.put("firmwareVersion", d.getFirmwareVersion());
        m.put("status", Boolean.TRUE.equals(d.getOnlineStatus()) ? "online" : "offline");
        m.put("battery", d.getBatteryLevel());
        m.put("signalStrength", d.getSignalStrength());
        m.put("lastSeenAt", d.getLastSeenAt());
        return m;
    }

    // ── Admin-facing User endpoints ──────────────────────────────────────────

    @Transactional
    public Map<String, Object> createUser(UUID networkId, Map<String, Object> body) {
        var network = networkRepo.findById(networkId)
            .orElseThrow(() -> new ResourceNotFoundException("Network not found"));
        UUID tenantId = network.getTenant().getId();

        String email = (String) body.get("email");
        if (userRepo.existsByEmailAndDeletedAtIsNull(email)) {
            throw new ConflictException("Email already exists: " + email);
        }

        String name = (String) body.get("fullName");
        String password = (String) body.get("password");
        String roleStr = (String) body.getOrDefault("role", "viewer");

        var role = roleRepo.findByNameAndTenantIdIsNull(roleStr.toUpperCase())
            .or(() -> roleRepo.findByNameAndTenantIdIsNull(roleStr.toLowerCase()))
            .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleStr));

        var user = User.builder()
            .tenant(network.getTenant())
            .email(email.toLowerCase().trim())
            .fullName(name)
            .passwordHash(encoder.encode(password))
            .isActive(true)
            .build();
        user = userRepo.save(user);

        userRoleRepo.save(UserRole.builder().userId(user.getId()).roleId(role.getId()).build());

        var m = new LinkedHashMap<String, Object>();
        m.put("id", user.getId());
        m.put("name", user.getFullName());
        m.put("email", user.getEmail());
        m.put("role", role.getName());
        m.put("tenantId", tenantId);
        m.put("isActive", user.getIsActive());
        m.put("createdAt", user.getCreatedAt());
        return m;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listNetworkUsers(UUID networkId) {
        var network = networkRepo.findById(networkId)
            .orElseThrow(() -> new ResourceNotFoundException("Network not found"));
        var users = userRepo.findAllByTenant_IdAndDeletedAtIsNull(network.getTenant().getId(), Pageable.unpaged()).getContent();
        var resp = new ArrayList<Map<String, Object>>();
        for (var u : users) {
            var roles = u.getUserRoles().stream().map(ur -> ur.getRole().getName().toUpperCase()).toList();
            String primaryRole = roles.isEmpty() ? "EMBRYOLOGIST" : roles.get(0);
            var m = new LinkedHashMap<String, Object>();
            m.put("id", u.getId());
            m.put("name", u.getFullName());
            m.put("email", u.getEmail());
            m.put("role", primaryRole);
            m.put("isActive", u.getIsActive());
            m.put("createdAt", u.getCreatedAt());
            resp.add(m);
        }
        return resp;
    }

    @Transactional
    public Map<String, Object> updateNetworkUser(UUID userId, Map<String, Object> body) {
        var u = userRepo.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (body.containsKey("fullName")) u.setFullName((String) body.get("fullName"));
        if (body.containsKey("isActive")) u.setIsActive((Boolean) body.get("isActive"));
        if (body.containsKey("role")) {
            String roleStr = (String) body.get("role");
            var role = roleRepo.findByNameAndTenantIdIsNull(roleStr.toUpperCase())
                .or(() -> roleRepo.findByNameAndTenantIdIsNull(roleStr.toLowerCase()))
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleStr));
            userRoleRepo.deleteAll(userRoleRepo.findAllByUserId(userId));
            userRoleRepo.save(UserRole.builder().userId(userId).roleId(role.getId()).build());
        }
        userRepo.save(u);

        var roles = u.getUserRoles().stream().map(ur -> ur.getRole().getName().toUpperCase()).toList();
        String primaryRole = roles.isEmpty() ? "EMBRYOLOGIST" : roles.get(0);
        var m = new LinkedHashMap<String, Object>();
        m.put("id", u.getId());
        m.put("name", u.getFullName());
        m.put("email", u.getEmail());
        m.put("role", primaryRole);
        m.put("isActive", u.getIsActive());
        return m;
    }

    @Transactional
    public void deleteNetworkUser(UUID userId) {
        var u = userRepo.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        u.setDeletedAt(OffsetDateTime.now());
        userRepo.save(u);
    }

    // ── Admin-facing Recipient endpoints ──────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRecipients(UUID networkId) {
        var network = networkRepo.findById(networkId)
            .orElseThrow(() -> new ResourceNotFoundException("Network not found"));
        var recipients = recipientRepo.findAllByTenantIdAndChannelOrderByCreatedAtAsc(network.getTenant().getId(), NotificationChannel.WHATSAPP);
        
        var respList = new ArrayList<Map<String, Object>>();
        for (var r : recipients) {
            var m = new LinkedHashMap<String, Object>();
            m.put("id", r.getId());
            m.put("name", r.getLabel());
            m.put("phone", r.getAddress());
            m.put("role", "");
            m.put("isActive", r.getIsActive());
            m.put("receivesCritical", r.getReceivesCritical());
            m.put("receivesWarning", r.getReceivesWarning());
            m.put("receivesDailySummary", r.getReceivesDailySummary());
            m.put("createdAt", r.getCreatedAt());
            respList.add(m);
        }
        return respList;
    }

    @Transactional
    public Map<String, Object> createRecipient(UUID networkId, Map<String, Object> body) {
        var network = networkRepo.findById(networkId)
            .orElseThrow(() -> new ResourceNotFoundException("Network not found"));
        UUID tenantId = network.getTenant().getId();

        String address = (String) body.get("address");
        String label = (String) body.get("label");
        String channelStr = (String) body.getOrDefault("channel", "WHATSAPP");

        var existing = recipientRepo.findByTenantIdAndChannelAndAddress(tenantId, NotificationChannel.WHATSAPP, address);
        NotificationRecipient r;
        if (existing.isPresent()) {
            r = existing.get();
            r.setLabel(label);
            r.setIsActive(true);
        } else {
            r = NotificationRecipient.builder()
                .tenantId(tenantId)
                .networkId(networkId)
                .channel(NotificationChannel.valueOf(channelStr.toUpperCase()))
                .address(address)
                .label(label)
                .isActive(true)
                .receivesCritical(true)
                .receivesWarning(true)
                .receivesDailySummary(false)
                .build();
        }
        r = recipientRepo.save(r);

        var m = new LinkedHashMap<String, Object>();
        m.put("id", r.getId());
        m.put("name", r.getLabel());
        m.put("phone", r.getAddress());
        m.put("role", "");
        m.put("isActive", r.getIsActive());
        m.put("receivesCritical", r.getReceivesCritical());
        m.put("receivesWarning", r.getReceivesWarning());
        m.put("receivesDailySummary", r.getReceivesDailySummary());
        m.put("createdAt", r.getCreatedAt());
        return m;
    }

    @Transactional
    public Map<String, Object> updateRecipient(UUID recipientId, Map<String, Object> body) {
        var r = recipientRepo.findById(recipientId)
            .orElseThrow(() -> new ResourceNotFoundException("Recipient not found"));
        
        if (body.containsKey("label")) r.setLabel((String) body.get("label"));
        if (body.containsKey("isActive")) r.setIsActive((Boolean) body.get("isActive"));

        r = recipientRepo.save(r);
        var m = new LinkedHashMap<String, Object>();
        m.put("id", r.getId());
        m.put("name", r.getLabel());
        m.put("phone", r.getAddress());
        m.put("role", "");
        m.put("isActive", r.getIsActive());
        return m;
    }

    @Transactional
    public Map<String, Object> deleteRecipient(UUID recipientId) {
        var r = recipientRepo.findById(recipientId)
            .orElseThrow(() -> new ResourceNotFoundException("Recipient not found"));
        recipientRepo.delete(r);
        return Map.of("success", true);
    }

    // ── Admin-facing Alert and Telemetry endpoints ───────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getLabAlerts(UUID labId, String status, int page, int perPage) {
        AlertStatus st = status != null ? AlertStatus.valueOf(status.toUpperCase()) : null;
        var result = alertRepo.findByLabAndStatus(labId, st, PageRequest.of(page, perPage));
        
        var list = new ArrayList<Map<String, Object>>();
        for (var a : result.getContent()) {
            var m = new LinkedHashMap<String, Object>();
            m.put("id", a.getId());
            m.put("metricColumn", a.getMetricColumn());
            m.put("parameter", a.getParameter());
            m.put("severity", a.getSeverity().name().toUpperCase());
            m.put("message", a.getMessage());
            m.put("triggerValue", a.getTriggerValue());
            m.put("thresholdValue", a.getThresholdValue());
            m.put("status", a.getStatus().name().toUpperCase());
            m.put("deviceUid", deviceRepo.findById(a.getDeviceId()).map(Device::getDeviceUid).orElse(""));
            m.put("triggeredAt", a.getTriggeredAt().toString());
            m.put("resolvedAt", a.getResolvedAt() != null ? a.getResolvedAt().toString() : null);
            m.put("acknowledgedAt", a.getAcknowledgedAt() != null ? a.getAcknowledgedAt().toString() : null);
            list.add(m);
        }

        return Map.of(
            "total", result.getTotalElements(),
            "limit", perPage,
            "offset", page * perPage,
            "alerts", list
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getLabHistory(UUID labId, int minutes) {
        var since = OffsetDateTime.now().minusMinutes(Math.min(minutes, 1440));
        var list = telemetryRepo.findHistory(labId, since, OffsetDateTime.now());
        
        var respList = new ArrayList<Map<String, Object>>();
        for (var row : list) {
            if (row instanceof Object[] arr && arr.length > 9) {
                var recordedAt = (OffsetDateTime) arr[1];
                var m = new LinkedHashMap<String, Object>();
                m.put("ts", recordedAt.toInstant().toEpochMilli());
                m.put("recordedAt", recordedAt.toString());
                m.put("temperature", arr[4] != null ? new BigDecimal(arr[4].toString()) : null);
                m.put("co2", arr[2] != null ? new BigDecimal(arr[2].toString()) : null);
                m.put("humidity", arr[5] != null ? new BigDecimal(arr[5].toString()) : null);
                m.put("pm25", arr[6] != null ? new BigDecimal(arr[6].toString()) : null);
                m.put("voc", arr[3] != null ? new BigDecimal(arr[3].toString()) : null);
                respList.add(m);
            }
        }
        return respList;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getGlobalAlerts(String status, String severity, Integer limit, Integer offset) {
        int l = limit != null ? limit : 50;
        int o = offset != null ? offset : 0;
        int page = o / l;
        AlertStatus st = (status != null && !status.isEmpty()) ? AlertStatus.valueOf(status.toUpperCase()) : null;
        AlertSeverity sv = (severity != null && !severity.isEmpty()) ? AlertSeverity.valueOf(severity.toUpperCase()) : null;
        
        var result = alertRepo.findGlobalFiltered(st, sv, PageRequest.of(page, l));
        
        var list = new ArrayList<Map<String, Object>>();
        for (var a : result.getContent()) {
            var m = new LinkedHashMap<String, Object>();
            m.put("id", a.getId());
            m.put("labId", a.getLab().getId());
            m.put("labName", a.getLab().getName());
            m.put("tenantName", a.getLab().getTenant().getName());
            m.put("deviceUid", deviceRepo.findById(a.getDeviceId()).map(Device::getDeviceUid).orElse(""));
            m.put("metricColumn", a.getMetricColumn());
            m.put("parameter", a.getParameter());
            m.put("severity", a.getSeverity().name().toUpperCase());
            m.put("message", a.getMessage());
            m.put("triggerValue", a.getTriggerValue());
            m.put("thresholdValue", a.getThresholdValue());
            m.put("status", a.getStatus().name().toUpperCase());
            m.put("triggeredAt", a.getTriggeredAt().toString());
            m.put("resolvedAt", a.getResolvedAt() != null ? a.getResolvedAt().toString() : null);
            m.put("acknowledgedAt", a.getAcknowledgedAt() != null ? a.getAcknowledgedAt().toString() : null);
            list.add(m);
        }
        
        return Map.of(
            "total", result.getTotalElements(),
            "limit", l,
            "offset", o,
            "alerts", list
        );
    }
}
