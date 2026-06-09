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
public class AlertService {
    private final AlertRepository alertRepo;
    private final AlertRuleRepository ruleRepo;
    private final UserRepository userRepo;

    @Transactional(readOnly = true)
    public Page<Map<String,Object>> listAlerts(UUID labId,String status,String severity,Pageable p){
        UUID tid=TenantContext.requireTenantId();
        AlertStatus st=status!=null?AlertStatus.valueOf(status.toUpperCase()):null;
        AlertSeverity sv=severity!=null?AlertSeverity.valueOf(severity.toUpperCase()):null;
        return alertRepo.findFiltered(tid,labId,st,sv,p).map(this::toMap);
    }

    @Transactional
    public Map<String,Object> acknowledge(UUID alertId){
        UUID tid=TenantContext.requireTenantId();
        UUID userId=TenantContext.getUserId();
        var alert=alertRepo.findById(alertId)
            .filter(a->a.getTenantId().equals(tid))
            .orElseThrow(()->new ResourceNotFoundException("Alert not found"));
        if(alert.getStatus()!=AlertStatus.ACTIVE)
            throw new BusinessException("Alert is already "+alert.getStatus().name().toLowerCase());
        alert.setStatus(AlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedBy(userId);
        alert.setAcknowledgedAt(OffsetDateTime.now());
        var saved=alertRepo.save(alert);
        var user=userRepo.findById(userId).orElse(null);
        var resp=new LinkedHashMap<String,Object>();
        resp.put("id",saved.getId());resp.put("status","acknowledged");
        resp.put("acknowledgedAt",saved.getAcknowledgedAt());
        resp.put("acknowledgedBy",user==null?null:Map.of("id",userId,"name",user.getFullName()));
        return resp;
    }

    public Map<String,Object> getThresholds(UUID networkId){
        // Return network-level alert rules as thresholds
        var rules=ruleRepo.findByTenantIdAndScopeIdAndIsActiveTrue(TenantContext.requireTenantId(),networkId);
        var thresholds=new LinkedHashMap<String,Object>();
        for(var r:rules){
            var t=new LinkedHashMap<String,Object>();
            if(r.getWarnMin()!=null)t.put("warnMin",r.getWarnMin());
            if(r.getWarnMax()!=null)t.put("warnMax",r.getWarnMax());
            if(r.getCritMin()!=null)t.put("critMin",r.getCritMin());
            if(r.getCritMax()!=null)t.put("critMax",r.getCritMax());
            t.put("unit",unitFor(r.getSensorType()));
            thresholds.put(r.getSensorType().name().toLowerCase(),t);
        }
        return Map.of("networkId",networkId,"thresholds",thresholds,"updatedAt",OffsetDateTime.now(),"updatedBy","");
    }

    @Transactional
    public Map<String,Object> updateThresholds(UUID networkId,Map<String,Object> body){
        UUID tid=TenantContext.requireTenantId();
        body.forEach((param,val)->{
            SensorType st=SensorType.valueOf(param.toUpperCase());
            @SuppressWarnings("unchecked") var vals=(Map<String,Object>)val;
            var existing=ruleRepo.findByTenantIdAndScopeIdAndSensorType(tid,networkId,st);
            var rule=existing.orElseGet(()->AlertRule.builder().tenantId(tid)
                .scopeType(AlertScopeType.NETWORK).scopeId(networkId).sensorType(st)
                .ruleName(param+" network rule").build());
            if(vals.containsKey("warnMin"))rule.setWarnMin(new java.math.BigDecimal(vals.get("warnMin").toString()));
            if(vals.containsKey("warnMax"))rule.setWarnMax(new java.math.BigDecimal(vals.get("warnMax").toString()));
            if(vals.containsKey("critMin"))rule.setCritMin(new java.math.BigDecimal(vals.get("critMin").toString()));
            if(vals.containsKey("critMax"))rule.setCritMax(new java.math.BigDecimal(vals.get("critMax").toString()));
            ruleRepo.save(rule);
        });
        return getThresholds(networkId);
    }

    private Map<String,Object> toMap(Alert a){
        var m=new LinkedHashMap<String,Object>();
        m.put("id",a.getId());m.put("labId",a.getLab().getId());m.put("labName",a.getLab().getName());
        m.put("hospitalName",a.getLab().getHospital().getName());
        m.put("parameter",a.getParameter());m.put("severity",a.getSeverity().name().toLowerCase());
        m.put("message",a.getMessage());m.put("valueAtTrigger",a.getTriggerValue());
        m.put("thresholdBreached",a.getThresholdValue());m.put("status",a.getStatus().name().toLowerCase());
        m.put("isPredicted",a.getIsPredicted());m.put("triggeredAt",a.getTriggeredAt());
        m.put("acknowledgedAt",a.getAcknowledgedAt());
        m.put("acknowledgedBy",a.getAcknowledgedByUser()==null?null:Map.of("id",a.getAcknowledgedBy(),"name",a.getAcknowledgedByUser().getFullName()));
        m.put("whatsappSent",false);
        return m;
    }
    private String unitFor(SensorType t){return switch(t){case TEMPERATURE->"°C";case CO2->"%";case HUMIDITY->"%";case PM25->"µg/m³";case VOC->"ppb";default->"";};};
}
