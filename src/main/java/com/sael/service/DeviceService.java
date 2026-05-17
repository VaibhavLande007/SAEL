package com.sael.service;
import com.sael.domain.entity.*;
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
public class DeviceService {
    private final DeviceRepository deviceRepo;
    private final LabRepository labRepo;

    public Page<Map<String,Object>> listDevices(UUID labId,UUID networkId,String status,Pageable p){
        UUID tid=TenantContext.requireTenantId();
        Boolean onlineStatus=status!=null?(status.equals("online")?true:false):null;
        return deviceRepo.findByTenantAndStatus(tid,onlineStatus,p).map(this::toMap);
    }

    @Transactional
    public Map<String,Object> register(String deviceUid,UUID labId,String name,String firmware){
        UUID tid=TenantContext.requireTenantId();
        if(deviceRepo.findByDeviceUid(deviceUid).isPresent())
            throw new ConflictException("Device UID already registered: "+deviceUid);
        // Find incubator to attach — for now use a placeholder approach
        // In production: require incubatorId in request body
        var device=Device.builder()
            .tenantId(tid)
            .incubatorId(UUID.fromString("00000000-0000-0000-0000-000000000000")) // placeholder
            .deviceUid(deviceUid)
            .firmwareVersion(firmware)
            .onlineStatus(false)
            .registeredAt(OffsetDateTime.now())
            .build();
        return toMap(deviceRepo.save(device));
    }

    private Map<String,Object> toMap(Device d){
        var m=new LinkedHashMap<String,Object>();
        m.put("id",d.getId());m.put("deviceUid",d.getDeviceUid());
        m.put("labId",null);m.put("labName","");
        m.put("name",d.getMqttClientId());m.put("firmwareVersion",d.getFirmwareVersion());
        m.put("status",Boolean.TRUE.equals(d.getOnlineStatus())?"online":"offline");
        m.put("battery",d.getBatteryLevel());m.put("signalStrength",d.getSignalStrength());
        m.put("lastSeenAt",d.getLastSeenAt());
        return m;
    }
}
