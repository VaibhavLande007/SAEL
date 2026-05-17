package com.sael.controller;
import com.sael.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor
public class LabController {
    private final OrgService orgService;
    private final TelemetryService telemetryService;
    private final KpiService kpiService;
    private final InsightService insightService;

    @GetMapping("/labs")
    public ResponseEntity<Map<String,Object>> listAll(
            @RequestParam(required=false) UUID networkId,
            @RequestParam(required=false) String status,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int perPage){
        var result=orgService.listAllLabs(networkId,status,PageRequest.of(page,perPage));
        return ResponseEntity.ok(Map.of("data",result.getContent(),
            "meta",Map.of("total",result.getTotalElements(),"page",result.getNumber()+1,
                "perPage",result.getSize(),"totalPages",result.getTotalPages())));
    }

    @GetMapping("/labs/{id}")
    public ResponseEntity<Map<String,Object>> get(@PathVariable UUID id){
        return ResponseEntity.ok(orgService.getLab(id));
    }

    @PatchMapping("/labs/{id}")
    @PreAuthorize("hasAnyRole('NETWORK_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<Map<String,Object>> update(@PathVariable UUID id,@RequestBody Map<String,String> body){
        return ResponseEntity.ok(orgService.updateLab(id,body.get("name")));
    }

    @DeleteMapping("/labs/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id){
        orgService.deleteLab(id);
        return ResponseEntity.noContent().build();
    }

    // ── Telemetry ─────────────────────────────────────────────────────────────
    @GetMapping("/labs/{labId}/telemetry/live")
    public ResponseEntity<Map<String,Object>> live(@PathVariable UUID labId){
        return ResponseEntity.ok(telemetryService.getLive(labId));
    }

    @GetMapping("/labs/{labId}/telemetry/history")
    public ResponseEntity<Map<String,Object>> history(
            @PathVariable UUID labId,
            @RequestParam String parameter,
            @RequestParam String range){
        return ResponseEntity.ok(telemetryService.getHistory(labId,parameter,range));
    }

    // ── KPIs ──────────────────────────────────────────────────────────────────
    @GetMapping("/labs/{labId}/kpis")
    public ResponseEntity<Map<String,Object>> listKpis(
            @PathVariable UUID labId,
            @RequestParam(required=false) String from,
            @RequestParam(required=false) String to,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int perPage){
        var result=kpiService.listKpis(labId,from,to,PageRequest.of(page,perPage));
        return ResponseEntity.ok(Map.of("data",result.getContent(),
            "meta",Map.of("total",result.getTotalElements(),"page",result.getNumber()+1,
                "perPage",result.getSize(),"totalPages",result.getTotalPages())));
    }

    @PostMapping("/labs/{labId}/kpis")
    @PreAuthorize("hasAnyRole('LAB_MANAGER','NETWORK_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<Map<String,Object>> submitKpi(
            @PathVariable UUID labId,@RequestBody Map<String,Object> body){
        return ResponseEntity.status(201).body(kpiService.submit(
            labId,
            (String)body.get("weekStart"),
            body.get("fertilisationRate")!=null?new java.math.BigDecimal(body.get("fertilisationRate").toString()):null,
            body.get("blastocystRate")!=null?new java.math.BigDecimal(body.get("blastocystRate").toString()):null,
            body.get("implantationRate")!=null?new java.math.BigDecimal(body.get("implantationRate").toString()):null,
            body.get("m2Rate")!=null?new java.math.BigDecimal(body.get("m2Rate").toString()):null,
            (String)body.get("notes")
        ));
    }

    @DeleteMapping("/labs/{labId}/kpis/{kpiId}")
    @PreAuthorize("hasAnyRole('LAB_MANAGER','NETWORK_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<Void> deleteKpi(@PathVariable UUID labId,@PathVariable UUID kpiId){
        kpiService.delete(labId,kpiId);
        return ResponseEntity.noContent().build();
    }
}
