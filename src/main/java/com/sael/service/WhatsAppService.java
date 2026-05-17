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
public class WhatsAppService {
    private final NotificationRecipientRepository recipientRepo;
    private final NotificationLogRepository logRepo;
    private final SystemConfigRepository configRepo;

    public Page<Map<String,Object>> listRecipients(UUID networkId,Pageable p){
        return recipientRepo.findAllByNetworkId(networkId,p).map(this::recipientMap);
    }

    @Transactional
    public Map<String,Object> addRecipient(UUID networkId,String name,String phone,String role,
            boolean critical,boolean warning,boolean daily){
        if(recipientRepo.existsByNetworkIdAndAddress(networkId,phone))
            throw new ConflictException("Recipient with this phone number already exists");
        var r=NotificationRecipient.builder()
            .tenantId(TenantContext.requireTenantId()).networkId(networkId)
            .channel(NotificationChannel.WHATSAPP).address(phone).label(name)
            .isActive(true).receivesCritical(critical).receivesWarning(warning)
            .receivesDailySummary(daily).build();
        return recipientMap(recipientRepo.save(r));
    }

    @Transactional
    public Map<String,Object> updateRecipient(UUID networkId,UUID recipientId,Map<String,Object> body){
        var r=recipientRepo.findById(recipientId)
            .filter(rec->networkId.equals(rec.getNetworkId()))
            .orElseThrow(()->new ResourceNotFoundException("Recipient not found"));
        if(body.containsKey("isActive"))r.setIsActive((Boolean)body.get("isActive"));
        if(body.containsKey("receivesCritical"))r.setReceivesCritical((Boolean)body.get("receivesCritical"));
        if(body.containsKey("receivesWarning"))r.setReceivesWarning((Boolean)body.get("receivesWarning"));
        if(body.containsKey("receivesDailySummary"))r.setReceivesDailySummary((Boolean)body.get("receivesDailySummary"));
        if(body.containsKey("name"))r.setLabel((String)body.get("name"));
        return recipientMap(recipientRepo.save(r));
    }

    @Transactional
    public void deleteRecipient(UUID networkId,UUID recipientId){
        var r=recipientRepo.findById(recipientId)
            .filter(rec->networkId.equals(rec.getNetworkId()))
            .orElseThrow(()->new ResourceNotFoundException("Recipient not found"));
        recipientRepo.delete(r);
    }

    public Map<String,Object> getSettings(UUID networkId){
        UUID tid=TenantContext.requireTenantId();
        var cfg=configRepo.findByTenantIdAndKey(tid,"alertToggles");
        var toggles=cfg.map(c->c.getValue()).orElse(Map.of(
            "wa",true,"predict",true,"daily",false,"wearables",true,"llm",true));
        var m=new LinkedHashMap<String,Object>();
        m.put("networkId",networkId);
        m.put("whatsappAlertsEnabled",toggles.getOrDefault("wa",true));
        m.put("predictiveAlertsEnabled",toggles.getOrDefault("predict",true));
        m.put("dailySummaryEnabled",toggles.getOrDefault("daily",false));
        m.put("siaInsightsInMessages",toggles.getOrDefault("llm",false));
        m.put("updatedAt",OffsetDateTime.now());
        return m;
    }

    @Transactional
    public Map<String,Object> updateSettings(UUID networkId,Map<String,Object> body){
        UUID tid=TenantContext.requireTenantId();
        var cfg=configRepo.findByTenantIdAndKey(tid,"alertToggles")
            .orElseGet(()->SystemConfig.builder().tenantId(tid).key("alertToggles").value(new LinkedHashMap<>()).build());
        var val=new LinkedHashMap<>(cfg.getValue());
        if(body.containsKey("whatsappAlertsEnabled"))val.put("wa",body.get("whatsappAlertsEnabled"));
        if(body.containsKey("predictiveAlertsEnabled"))val.put("predict",body.get("predictiveAlertsEnabled"));
        if(body.containsKey("dailySummaryEnabled"))val.put("daily",body.get("dailySummaryEnabled"));
        if(body.containsKey("siaInsightsInMessages"))val.put("llm",body.get("siaInsightsInMessages"));
        cfg.setValue(val);
        configRepo.save(cfg);
        return getSettings(networkId);
    }

    public Page<Map<String,Object>> getLogs(UUID networkId,String from,String to,String status,Pageable p){
        NotificationStatus st=status!=null?NotificationStatus.valueOf(status.toUpperCase()):null;
        OffsetDateTime f=from!=null?OffsetDateTime.parse(from+"T00:00:00Z"):null;
        OffsetDateTime t=to!=null?OffsetDateTime.parse(to+"T23:59:59Z"):null;
        return logRepo.findByNetwork(networkId,st,f,t,p).map(this::logMap);
    }

    private Map<String,Object> recipientMap(NotificationRecipient r){
        var m=new LinkedHashMap<String,Object>();
        m.put("id",r.getId());m.put("name",r.getLabel());m.put("phone",r.getAddress());
        m.put("role","");m.put("isActive",r.getIsActive());
        m.put("receivesCritical",r.getReceivesCritical());
        m.put("receivesWarning",r.getReceivesWarning());
        m.put("receivesDailySummary",r.getReceivesDailySummary());
        m.put("createdAt",r.getCreatedAt());
        return m;
    }
    private Map<String,Object> logMap(NotificationLog l){
        var m=new LinkedHashMap<String,Object>();
        m.put("id",l.getId());
        m.put("recipientName",l.getRecipient()!=null?l.getRecipient().getLabel():"");
        m.put("recipientPhone",l.getRecipient()!=null?l.getRecipient().getAddress():"");
        m.put("messageType","alert");m.put("alertId",l.getAlertId());
        m.put("messagePreview",l.getMessagePreview());
        m.put("status",l.getStatus().name().toLowerCase());
        m.put("sentAt",l.getSentAt());m.put("deliveredAt",l.getDeliveredAt());
        return m;
    }
}
