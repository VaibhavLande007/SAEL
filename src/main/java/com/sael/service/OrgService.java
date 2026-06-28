package com.sael.service;
import com.sael.domain.entity.*;
import com.sael.domain.enums.*;
import com.sael.domain.repository.*;
import com.sael.exception.*;
import com.sael.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class OrgService {
    private final NetworkRepository networkRepo;
    private final HospitalRepository hospitalRepo;
    private final LabRepository labRepo;
    private final SystemConfigRepository systemConfigRepo;
    private final TelemetryRepository telemetryRepo;
    private final AlertRepository alertRepo;
    private final UserScopeRepository userScopeRepo;
    private final TenantRepository tenantRepo;

    // ── Networks ──────────────────────────────────────────────────────────────
    @Transactional(readOnly=true)
    public Page<Map<String,Object>> listNetworks(Pageable p){
        return networkRepo.findAllByTenant_IdAndDeletedAtIsNull(TenantContext.requireTenantId(),p)
            .map(this::networkMap);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> getNetwork(UUID id){
        return networkMap(networkRepo.findByIdAndTenant_IdAndDeletedAtIsNull(id,TenantContext.requireTenantId())
            .orElseThrow(()->new ResourceNotFoundException("Network not found")));
    }
    @Transactional
    public Map<String,Object> createNetwork(String name){
        var t=new com.sael.domain.entity.Tenant();
        t.setId(TenantContext.requireTenantId());
        var n=Network.builder().tenant(t).name(name).build();
        return networkMap(networkRepo.save(n));
    }
    @Transactional
    public Map<String,Object> updateNetwork(UUID id,String name,String timezone){
        var n=networkRepo.findByIdAndTenant_IdAndDeletedAtIsNull(id,TenantContext.requireTenantId())
            .orElseThrow(()->new ResourceNotFoundException("Network not found"));
        if(name!=null)n.setName(name);
        if(timezone!=null)n.setTimezone(timezone);
        return networkMap(networkRepo.save(n));
    }
    @Transactional
    public void deleteNetwork(UUID id){
        var n=networkRepo.findByIdAndTenant_IdAndDeletedAtIsNull(id,TenantContext.requireTenantId())
            .orElseThrow(()->new ResourceNotFoundException("Network not found"));
        n.setDeletedAt(OffsetDateTime.now());
        networkRepo.save(n);
    }

    // ── Hospitals ─────────────────────────────────────────────────────────────
    @Transactional(readOnly=true)
    public Page<Map<String,Object>> listHospitals(UUID networkId,Pageable p){
        return hospitalRepo.findAllByNetwork_IdAndDeletedAtIsNull(networkId,p).map(this::hospitalMap);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> getHospital(UUID id){
        var h = hospitalRepo.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(()->new ResourceNotFoundException("Hospital not found"));
        checkTenant(h.getTenant());
        return hospitalMap(h);
    }
    @Transactional
    public Map<String,Object> createHospital(UUID networkId,String name,String city,String address){
        var network=networkRepo.findByIdAndTenant_IdAndDeletedAtIsNull(networkId,TenantContext.requireTenantId())
            .orElseThrow(()->new ResourceNotFoundException("Network not found"));
        var t=new com.sael.domain.entity.Tenant();t.setId(TenantContext.requireTenantId());
        var h=Hospital.builder().tenant(t).network(network).name(name).city(city).address(address).build();
        return hospitalMap(hospitalRepo.save(h));
    }
    @Transactional
    public Map<String,Object> updateHospital(UUID id,String name,String city,String address){
        var h=hospitalRepo.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(()->new ResourceNotFoundException("Hospital not found"));
        checkTenant(h.getTenant());
        if(name!=null)h.setName(name);
        if(city!=null)h.setCity(city);
        if(address!=null)h.setAddress(address);
        return hospitalMap(hospitalRepo.save(h));
    }
    @Transactional
    public void deleteHospital(UUID id){
        var h=hospitalRepo.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(()->new ResourceNotFoundException("Hospital not found"));
        checkTenant(h.getTenant());
        if (h.getLabs().stream().anyMatch(l -> l.getDeletedAt() == null)) {
            throw new ConflictException("Remove all labs before deleting this hospital");
        }
        h.setDeletedAt(OffsetDateTime.now());
        hospitalRepo.save(h);
    }

    // ── Labs ──────────────────────────────────────────────────────────────────
    @Transactional(readOnly=true)
    public Page<Map<String,Object>> listLabsByHospital(UUID hospitalId,Pageable p){
        return labRepo.findAllByHospital_IdAndDeletedAtIsNull(hospitalId,p).map(this::labMap);
    }
    @Transactional(readOnly=true)
    public Page<Map<String,Object>> listAllLabs(UUID networkId,String status,Pageable p){
        return labRepo.findAllScoped(TenantContext.requireTenantId(),networkId,p).map(this::labMap);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> getLab(UUID id){
        var l = labRepo.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(()->new ResourceNotFoundException("Lab not found"));
        checkTenant(l.getTenant());
        return labMap(l);
    }
    @Transactional
    public Map<String,Object> createLab(UUID hospitalId,String name){
        var hospital=hospitalRepo.findByIdAndDeletedAtIsNull(hospitalId)
            .orElseThrow(()->new ResourceNotFoundException("Hospital not found"));
        checkTenant(hospital.getTenant());
        var t=new com.sael.domain.entity.Tenant();t.setId(TenantContext.requireTenantId());
        var lab=Lab.builder()
                .tenant(t)
                .hospital(hospital)
                .labType(LabType.IVF)
                .name(name)
                .build();
        return labMap(labRepo.save(lab));
    }
    @Transactional
    public Map<String,Object> updateLab(UUID id,String name){
        var lab=labRepo.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(()->new ResourceNotFoundException("Lab not found"));
        checkTenant(lab.getTenant());
        if(name!=null)lab.setName(name);
        return labMap(labRepo.save(lab));
    }
    @Transactional(readOnly=true)
    public Map<String,Object> getNetworkSnapshot(){
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.getUserId();

        // 1. Get network name
        var networkConfig = systemConfigRepo.findByTenantIdAndKey(tenantId, "network_info").orElse(null);
        String networkName = "Unknown Network";
        if (networkConfig != null && networkConfig.getValue() != null) {
            var nameVal = networkConfig.getValue().get("name");
            if (nameVal != null) {
                networkName = nameVal.toString();
            }
        }
        // Fall back to tenant name
        if (networkName.equals("Unknown Network")) {
            var tenant = tenantRepo.findById(tenantId).orElse(null);
            if (tenant != null) {
                networkName = tenant.getName();
            }
        }

        // 2. Fetch allowed lab IDs for user scopes
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

        // 3. Fetch all hospitals under the tenant
        List<Hospital> hospitals = hospitalRepo.findAllByTenant_IdAndDeletedAtIsNull(tenantId);

        int totalLabs = 0;
        int onlineLabs = 0;
        int nominalLabs = 0;
        int warningLabs = 0;
        int criticalLabs = 0;
        int unackedAlertCount = 0;

        var hospitalList = new ArrayList<Map<String, Object>>();

        for (var h : hospitals) {
            var labsList = new ArrayList<Map<String, Object>>();
            
            // Filter and sort labs by name
            var sortedLabs = h.getLabs().stream()
                .filter(l -> l.getDeletedAt() == null && l.getTenant().getId().equals(tenantId))
                .filter(l -> allowedLabIds == null || allowedLabIds.contains(l.getId()))
                .sorted(Comparator.comparing(Lab::getName))
                .toList();

            for (var l : sortedLabs) {
                totalLabs++;

                // Get first active device for the lab
                var devices = l.getDevices().stream()
                    .filter(d -> d.getTenantId().equals(tenantId))
                    .toList();
                
                Device dev = devices.isEmpty() ? null : devices.get(0);
                boolean isOnline = dev != null && Boolean.TRUE.equals(dev.getOnlineStatus());
                if (isOnline) {
                    onlineLabs++;
                }

                // Get active alerts (status == ACTIVE)
                var activeAlerts = l.getAlerts().stream()
                    .filter(a -> a.getStatus() == AlertStatus.ACTIVE && tenantId.equals(a.getTenantId()))
                    .toList();
                
                int crits = (int) activeAlerts.stream().filter(a -> a.getSeverity() == AlertSeverity.CRIT).count();
                int warns = (int) activeAlerts.stream().filter(a -> a.getSeverity() == AlertSeverity.WARN).count();
                unackedAlertCount += activeAlerts.size();

                String status;
                if (!isOnline) {
                    status = "OFFLINE";
                } else if (crits > 0) {
                    status = "CRITICAL";
                    criticalLabs++;
                } else if (warns > 0) {
                    status = "WARNING";
                    warningLabs++;
                } else {
                    status = "NOMINAL";
                    nominalLabs++;
                }

                // Get latest telemetry reading
                var latestTelOpt = telemetryRepo.findLatestByLabId(l.getId());
                Map<String, Object> deviceMap = null;

                if (dev != null) {
                    var latestTelemetryMap = new LinkedHashMap<String, Object>();
                    if (latestTelOpt.isPresent()) {
                        var tel = latestTelOpt.get();
                        latestTelemetryMap.put("temperature", tel.getTemperatureC() != null ? tel.getTemperatureC().doubleValue() : null);
                        latestTelemetryMap.put("co2", tel.getCo2Ppm() != null ? tel.getCo2Ppm().doubleValue() : null);
                        latestTelemetryMap.put("humidity", tel.getHumidityPct() != null ? tel.getHumidityPct().doubleValue() : null);
                        latestTelemetryMap.put("pm25", tel.getPm25UgM3() != null ? tel.getPm25UgM3().doubleValue() : null);
                        latestTelemetryMap.put("voc", tel.getTvocPpb() != null ? tel.getTvocPpb().intValue() : null);
                        latestTelemetryMap.put("isDoorOpen", tel.getDoorIsOpen());
                        latestTelemetryMap.put("recordedAt", tel.getRecordedAt().toString());
                    }

                    deviceMap = new LinkedHashMap<>();
                    deviceMap.put("id", dev.getId());
                    deviceMap.put("deviceUid", dev.getDeviceUid());
                    deviceMap.put("onlineStatus", dev.getOnlineStatus());
                    deviceMap.put("lastSeenAt", dev.getLastSeenAt() != null ? dev.getLastSeenAt().toString() : null);
                    deviceMap.put("latestTelemetry", latestTelOpt.isPresent() ? latestTelemetryMap : null);
                }

                // Calculate labCode: last 6 characters of deviceUid or lab id in uppercase
                String codeSource = dev != null ? dev.getDeviceUid() : l.getId().toString();
                String labCode = codeSource.length() >= 6 
                    ? codeSource.substring(codeSource.length() - 6).toUpperCase() 
                    : codeSource.toUpperCase();

                var labMap = new LinkedHashMap<String, Object>();
                labMap.put("id", l.getId());
                labMap.put("name", l.getName());
                labMap.put("labCode", labCode);
                labMap.put("city", h.getCity());
                labMap.put("status", status);
                labMap.put("device", deviceMap);

                labsList.add(labMap);
            }

            // Only add hospital if it has labs matching allowed scope
            if (!labsList.isEmpty()) {
                var hospitalMap = new LinkedHashMap<String, Object>();
                hospitalMap.put("id", h.getId());
                hospitalMap.put("name", h.getName());
                hospitalMap.put("city", h.getCity() != null ? h.getCity() : "");
                hospitalMap.put("labs", labsList);
                hospitalList.add(hospitalMap);
            }
        }

        // Sort hospitals by name
        hospitalList.sort(Comparator.comparing(m -> m.get("name").toString()));

        List<Network> nets = networkRepo.findAllByTenant_IdAndDeletedAtIsNull(tenantId);
        String networkId = nets.isEmpty() ? null : nets.get(0).getId().toString();

        var result = new LinkedHashMap<String, Object>();
        result.put("networkId", networkId);
        result.put("networkName", networkName);
        result.put("totalLabs", totalLabs);
        result.put("onlineLabs", onlineLabs);
        result.put("nominalLabs", nominalLabs);
        result.put("warningLabs", warningLabs);
        result.put("criticalLabs", criticalLabs);
        result.put("unackedAlertCount", unackedAlertCount);
        result.put("hospitals", hospitalList);

        return result;
    }

    @Transactional
    public void deleteLab(UUID id){
        var lab=labRepo.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(()->new ResourceNotFoundException("Lab not found"));
        checkTenant(lab.getTenant());
        if (!lab.getDevices().isEmpty()) {
            throw new ConflictException("Remove the device before deleting this lab");
        }
        lab.setDeletedAt(OffsetDateTime.now());
        labRepo.save(lab);
    }

    // ── Mappers ───────────────────────────────────────────────────────────────
    private Map<String,Object> networkMap(Network n){
        long hospitals=hospitalRepo.countByNetwork_IdAndDeletedAtIsNull(n.getId());
        long labs=labRepo.countByHospital_Network_IdAndDeletedAtIsNull(n.getId());
        var m=new java.util.LinkedHashMap<String,Object>();
        m.put("id",n.getId());
        m.put("name",n.getName());
        m.put("tenantId",n.getTenant().getId());
        m.put("tenantName",n.getTenant().getName());
        m.put("hospitalsCount",hospitals);
        m.put("labsCount",labs);
        m.put("onlineLabsCount",labs);
        m.put("createdAt",n.getCreatedAt());
        return m;
    }
    private Map<String,Object> hospitalMap(Hospital h){
        long labs=labRepo.countByHospital_IdAndDeletedAtIsNull(h.getId());
        var m=new LinkedHashMap<String,Object>();
        m.put("id",h.getId());m.put("name",h.getName());m.put("networkId",h.getNetwork().getId());
        m.put("city",h.getCity());m.put("address",h.getAddress());
        m.put("labsCount",labs);m.put("onlineLabsCount",labs);m.put("createdAt",h.getCreatedAt());
        return m;
    }
    private Map<String,Object> labMap(Lab l){
        var m=new LinkedHashMap<String,Object>();
        m.put("id",l.getId());m.put("name",l.getName());
        m.put("hospitalId",l.getHospital().getId());
        m.put("hospitalName",l.getHospital().getName());
        m.put("networkId",l.getHospital().getNetwork().getId());
        m.put("status","nominal");m.put("deviceCount",0);
        m.put("lastReadingAt",null);m.put("createdAt",l.getCreatedAt());
        return m;
    }

    private void checkTenant(Tenant tenant) {
        UUID tid = TenantContext.getTenantId();
        if (tid != null && tenant != null && !tenant.getId().equals(tid)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }
    }
}
