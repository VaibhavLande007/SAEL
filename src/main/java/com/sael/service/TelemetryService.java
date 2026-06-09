package com.sael.service;
import com.sael.domain.entity.*;
import com.sael.domain.enums.*;
import com.sael.domain.repository.*;
import com.sael.exception.*;
import com.sael.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TelemetryService {
    private final LabRepository labRepo;
    private final DeviceRepository deviceRepo;
    private final SensorRepository sensorRepo;
    private final TelemetryRepository telemetryRepo;
    private final AlertRuleRepository ruleRepo;
    private final AlertRepository alertRepo;

    /** Powers GET /labs/:id/telemetry/live */
    public Map<String,Object> getLive(UUID labId){
        UUID tid=TenantContext.requireTenantId();
        var lab=labRepo.findByIdAndTenant_IdAndDeletedAtIsNull(labId,tid)
                .orElseThrow(()->new ResourceNotFoundException("Lab not found"));

        var latest=telemetryRepo.findLatestByLabId(labId);
        var rules=ruleRepo.findApplicableRules(tid,
                lab.getHospital().getNetwork().getId(),
                lab.getHospital().getId(),labId);
        var ruleMap=new LinkedHashMap<String,AlertRule>();
        for(var r:rules) ruleMap.put(r.getSensorType().name(),r);

        var readings=new LinkedHashMap<String,Object>();
        if(latest.isPresent()){
            var t=latest.get();
            readings.put("temperature",buildBlock(t.getTemperatureC(),"°C",ruleMap.get("TEMPERATURE")));
            readings.put("co2",buildBlock(t.getCo2Ppm(),"%",ruleMap.get("CO2")));
            readings.put("humidity",buildBlock(t.getHumidityPct(),"%",ruleMap.get("HUMIDITY")));
            readings.put("pm25",buildBlock(t.getPm25UgM3(),"µg/m³",ruleMap.get("PM25")));
            readings.put("voc",buildBlock(t.getTvocPpb(),"ppb",ruleMap.get("VOC")));
            var doorBlock=new LinkedHashMap<String,Object>();
            doorBlock.put("value",t.getDoorIsOpen());
            doorBlock.put("unit","open");
            doorBlock.put("status",Boolean.TRUE.equals(t.getDoorIsOpen())?"warning":"nominal");
            readings.put("door",doorBlock);
        } else {
            for (String p : new String[]{"temperature","co2","humidity","pm25","voc","door"}) {

                Map<String, Object> offlineBlock = new LinkedHashMap<>();

                offlineBlock.put("value", null);
                offlineBlock.put("unit", unitFor(p.toUpperCase()));
                offlineBlock.put("status", "offline");

                readings.put(p, offlineBlock);
            }
        }

        var resp=new LinkedHashMap<String,Object>();
        resp.put("labId",labId);
        resp.put("labName",lab.getName());
        resp.put("deviceId",latest.map(t->t.getDeviceId()).orElse(null));
        resp.put("deviceStatus",latest.isPresent()?"online":"offline");
        resp.put("lastReadingAt",latest.map(t->t.getRecordedAt()).orElse(null));
        resp.put("readings",readings);
        return resp;
    }

    /** Powers GET /labs/:id/telemetry/history */
    public Map<String,Object> getHistory(UUID labId,String parameter,String range){
        UUID tid=TenantContext.requireTenantId();
        var lab=labRepo.findByIdAndTenant_IdAndDeletedAtIsNull(labId,tid)
                .orElseThrow(()->new ResourceNotFoundException("Lab not found"));

        // Whitelist of valid column names — prevents SQL injection
        var VALID_PARAMS=Set.of("temperature","co2","humidity","pm25","pm10","pm1","voc","door");
        if(!VALID_PARAMS.contains(parameter.toLowerCase()))
            throw new ResourceNotFoundException("Unknown parameter: "+parameter);

        OffsetDateTime now=OffsetDateTime.now();
        OffsetDateTime from=switch(range.toUpperCase()){
            case "7D"->now.minusDays(7);
            case "12W"->now.minusWeeks(12);
            default->now.minusHours(24);
        };

        // col indices in query: 0=id, 1=recordedAt, 2=co2Ppm, 3=tvocPpb, 4=temperatureC,
        //                       5=humidityPct, 6=pm25UgM3, 7=pm10UgM3, 8=pm1UgM3,
        //                       9=doorIsOpen, 10=qualityFlag
        var colIndex=switch(parameter.toLowerCase()){
            case "temperature"->4;
            case "co2"->2;
            case "humidity"->5;
            case "pm25"->6;
            case "pm10"->7;
            case "pm1"->8;
            case "voc"->3;
            case "door"->9;
            default->4;
        };

        var rows=telemetryRepo.findHistory(labId,from,now);
        var data=new ArrayList<Map<String,Object>>();
        for(var row:rows){
            if(row instanceof Object[] arr&&arr.length>colIndex){
                var pt=new LinkedHashMap<String,Object>();
                pt.put("timestamp",arr[1]);
                pt.put("value",arr[colIndex]!=null?new BigDecimal(arr[colIndex].toString()):null);
                data.add(pt);
            }
        }

        var rules=ruleRepo.findApplicableRules(tid,
                lab.getHospital().getNetwork().getId(),lab.getHospital().getId(),labId);
        var rule=rules.stream().filter(r->r.getSensorType().name().equalsIgnoreCase(parameter)).findFirst().orElse(null);
        var thr=new LinkedHashMap<String,Object>();
        if(rule!=null){
            if(rule.getWarnMin()!=null)thr.put("warnMin",rule.getWarnMin());
            if(rule.getWarnMax()!=null)thr.put("warnMax",rule.getWarnMax());
            if(rule.getCritMin()!=null)thr.put("critMin",rule.getCritMin());
            if(rule.getCritMax()!=null)thr.put("critMax",rule.getCritMax());
        }

        var resp=new LinkedHashMap<String,Object>();
        resp.put("labId",labId);
        resp.put("parameter",parameter.toLowerCase());
        resp.put("range",range.toUpperCase());
        resp.put("unit",unitFor(parameter.toUpperCase()));
        resp.put("data",data);
        resp.put("thresholds",thr);
        return resp;
    }

    /** Powers GET /networks/:id/overview */
    public Map<String,Object> getNetworkOverview(UUID networkId){
        UUID tid=TenantContext.requireTenantId();
        var labs=labRepo.findAllScoped(tid,networkId,
            org.springframework.data.domain.PageRequest.of(0,1000)).getContent();

        long total=labs.size();
        var labSummaries=new ArrayList<Map<String,Object>>();
        long nominal=0,warning=0,critical=0,offline=0,unackedTotal=0;

        for(var lab:labs){
            var live=getLiveQuick(lab);
            String status=(String)live.get("status");
            switch(status){
                case "nominal"->nominal++;
                case "warning"->warning++;
                case "critical"->critical++;
                default->offline++;
            }
            long unacked=alertRepo(lab.getId());
            unackedTotal+=unacked;
            var ls=new LinkedHashMap<String,Object>();
            ls.put("id",lab.getId());ls.put("name",lab.getName());
            ls.put("hospitalName",lab.getHospital().getName());
            ls.put("status",status);ls.put("unacknowledgedAlerts",unacked);
            ls.put("latestReadings",live.get("readings"));
            ls.put("lastReadingAt",live.get("lastReadingAt"));
            labSummaries.add(ls);
        }

        var summary=new LinkedHashMap<String,Object>();
        summary.put("totalLabs",total);summary.put("online",total-offline);
        summary.put("nominal",nominal);summary.put("warning",warning);
        summary.put("critical",critical);summary.put("offline",offline);
        summary.put("unacknowledgedAlerts",unackedTotal);

        var resp=new LinkedHashMap<String,Object>();
        resp.put("networkId",networkId);resp.put("networkName","");
        resp.put("summary",summary);resp.put("labs",labSummaries);
        return resp;
    }

    // Lightweight live read for network overview (no threshold fetch)
    private Map<String,Object> getLiveQuick(Lab lab){
        var latest=telemetryRepo.findLatestByLabId(lab.getId());
        var readings=new LinkedHashMap<String,Object>();
        if(latest.isPresent()){
            var t=latest.get();
            readings.put("temperature",Map.of("value",t.getTemperatureC()!=null?t.getTemperatureC():"","unit","°C","status",t.getTemperatureC()!=null?"nominal":"offline"));
            readings.put("co2",Map.of("value",t.getCo2Ppm()!=null?t.getCo2Ppm():"","unit","%","status",t.getCo2Ppm()!=null?"nominal":"offline"));
            readings.put("humidity",Map.of("value",t.getHumidityPct()!=null?t.getHumidityPct():"","unit","%","status",t.getHumidityPct()!=null?"nominal":"offline"));
            readings.put("pm25",Map.of("value",t.getPm25UgM3()!=null?t.getPm25UgM3():"","unit","µg/m³","status",t.getPm25UgM3()!=null?"nominal":"offline"));
            readings.put("voc",Map.of("value",t.getTvocPpb()!=null?t.getTvocPpb():"","unit","ppb","status",t.getTvocPpb()!=null?"nominal":"offline"));
            readings.put("door",Map.of("value",t.getDoorIsOpen(),"unit","open","status",Boolean.TRUE.equals(t.getDoorIsOpen())?"warning":"nominal"));
        } else {
            for(String p:new String[]{"temperature","co2","humidity","pm25","voc","door"})
                readings.put(p,Map.of("value",null,"unit",unitFor(p.toUpperCase()),"status","offline"));
        }
        String overallStatus=latest.isEmpty()?"offline":"nominal";
        return Map.of("status",overallStatus,"readings",readings,"lastReadingAt",latest.map(t->t.getRecordedAt()).orElse(OffsetDateTime.now()));
    }

    private long alertRepo(UUID labId){try{return alertRepo.countByLab_IdAndStatus(labId,com.sael.domain.enums.AlertStatus.ACTIVE);}catch(Exception e){return 0;}}

    private String evaluateStatus(BigDecimal val,AlertRule rule){
        if(rule==null)return "nominal";
        if((rule.getCritMax()!=null&&val.compareTo(rule.getCritMax())>0)||
           (rule.getCritMin()!=null&&val.compareTo(rule.getCritMin())<0))return "critical";
        if((rule.getWarnMax()!=null&&val.compareTo(rule.getWarnMax())>0)||
           (rule.getWarnMin()!=null&&val.compareTo(rule.getWarnMin())<0))return "warning";
        return "nominal";
    }

    private Map<String,Object> buildBlock(BigDecimal val,String unit,AlertRule rule){
        var block=new LinkedHashMap<String,Object>();
        block.put("value",val);
        block.put("unit",unit);
        block.put("status",val==null?"offline":evaluateStatus(val,rule));
        if(val!=null&&rule!=null){
            var thr=new LinkedHashMap<String,Object>();
            if(rule.getWarnMin()!=null)thr.put("warnMin",rule.getWarnMin());
            if(rule.getWarnMax()!=null)thr.put("warnMax",rule.getWarnMax());
            if(rule.getCritMin()!=null)thr.put("critMin",rule.getCritMin());
            if(rule.getCritMax()!=null)thr.put("critMax",rule.getCritMax());
            block.put("threshold",thr);
        }
        return block;
    }

    private String unitFor(String t){return switch(t){case "TEMPERATURE"->"°C";case "CO2"->"%";case "HUMIDITY"->"%";case "PM25","PM10","PM1"->"µg/m³";case "VOC"->"ppb";case "DOOR"->"open";default->"";};}

    public Map<String,Object> getTrends(UUID tenantId, UUID labId, Integer days) {
        int d = days != null ? days : 84;
        var since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(Math.min(d, 90));
        List<Object[]> rows;
        if (labId != null) {
            rows = telemetryRepo.findRollupsByTenantAndLab(tenantId, labId, since);
        } else {
            rows = telemetryRepo.findRollupsByTenant(tenantId, since);
        }
        var byWeek = new LinkedHashMap<String, Map<String, List<Double>>>();
        for (var row : rows) {
            OffsetDateTime hourBucket;
            if (row[0] instanceof OffsetDateTime) {
                hourBucket = (OffsetDateTime) row[0];
            } else if (row[0] instanceof java.sql.Timestamp) {
                hourBucket = ((java.sql.Timestamp) row[0]).toInstant().atOffset(ZoneOffset.UTC);
            } else {
                hourBucket = OffsetDateTime.parse(row[0].toString());
            }
            String key = weekLabel(hourBucket);
            byWeek.computeIfAbsent(key, k -> {
                var m = new HashMap<String, List<Double>>();
                m.put("temp", new ArrayList<>());
                m.put("co2", new ArrayList<>());
                m.put("hum", new ArrayList<>());
                m.put("pm25", new ArrayList<>());
                m.put("tvoc", new ArrayList<>());
                return m;
            });
            var lists = byWeek.get(key);
            if (row[1] != null) lists.get("temp").add(((Number) row[1]).doubleValue());
            if (row[2] != null) lists.get("co2").add(((Number) row[2]).doubleValue());
            if (row[3] != null) lists.get("hum").add(((Number) row[3]).doubleValue());
            if (row[4] != null) lists.get("pm25").add(((Number) row[4]).doubleValue());
            if (row[5] != null) lists.get("tvoc").add(((Number) row[5]).doubleValue());
        }
        List<Map<String,Object>> dataList = new ArrayList<>();
        for (var entry : byWeek.entrySet()) {
            String week = entry.getKey();
            var lists = entry.getValue();
            var m = new LinkedHashMap<String,Object>();
            m.put("week", week);
            m.put("tempAvg", avg(lists.get("temp")));
            m.put("co2Avg", avg(lists.get("co2")));
            m.put("humidityAvg", avg(lists.get("hum")));
            m.put("pm25Avg", avg(lists.get("pm25")));
            m.put("tvocAvg", avg(lists.get("tvoc")));
            dataList.add(m);
        }
        return Map.of("data", dataList);
    }

    private String weekLabel(OffsetDateTime odt) {
        var localDate = odt.atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
        var monday = localDate.with(DayOfWeek.MONDAY);
        var formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d", Locale.US);
        return monday.format(formatter);
    }

    private Double avg(List<Double> vals) {
        if (vals.isEmpty()) return null;
        double sum = 0;
        for (var v : vals) sum += v;
        double result = sum / vals.size();
        return Math.round(result * 100.0) / 100.0;
    }
}
