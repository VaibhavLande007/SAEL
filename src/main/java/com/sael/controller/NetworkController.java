package com.sael.controller;
import com.sael.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor
public class NetworkController {
    private final OrgService orgService;
    private final TelemetryService telemetryService;
    private final AlertService alertService;
    private final KpiService kpiService;
    private final WhatsAppService whatsAppService;

    // ── Network Snapshot (Frontend Compatibility) ────────────────────────────
    @GetMapping("/network")
    public ResponseEntity<Map<String,Object>> getNetworkSnapshot(){
        return ResponseEntity.ok(orgService.getNetworkSnapshot());
    }

    // ── Networks ──────────────────────────────────────────────────────────────
    @GetMapping("/networks")
    public ResponseEntity<Map<String,Object>> listNetworks(
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int perPage){
        var result=orgService.listNetworks(PageRequest.of(page,perPage,Sort.by("createdAt").descending()));
        return ResponseEntity.ok(pagedResponse(result));
    }

    @PostMapping("/networks")
    @PreAuthorize("hasAnyRole('NETWORK_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<Map<String,Object>> createNetwork(@RequestBody Map<String,String> body){
        return ResponseEntity.status(201).body(orgService.createNetwork(body.get("name")));
    }

    @GetMapping("/networks/{id}")
    public ResponseEntity<Map<String,Object>> getNetwork(@PathVariable UUID id){
        return ResponseEntity.ok(orgService.getNetwork(id));
    }

    @PatchMapping("/networks/{id}")
    @PreAuthorize("hasAnyRole('NETWORK_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<Map<String,Object>> updateNetwork(@PathVariable UUID id,@RequestBody Map<String,String> body){
        return ResponseEntity.ok(orgService.updateNetwork(id,body.get("name"),body.get("timezone")));
    }

    @DeleteMapping("/networks/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Void> deleteNetwork(@PathVariable UUID id){
        orgService.deleteNetwork(id);
        return ResponseEntity.noContent().build();
    }

    // ── Network Overview (Dashboard) ──────────────────────────────────────────
    @GetMapping("/networks/{networkId}/overview")
    public ResponseEntity<Map<String,Object>> networkOverview(@PathVariable UUID networkId){
        return ResponseEntity.ok(telemetryService.getNetworkOverview(networkId));
    }

    // ── Network Trends ────────────────────────────────────────────────────────
    @GetMapping("/networks/{networkId}/trends")
    public ResponseEntity<Map<String,Object>> networkTrends(
            @PathVariable UUID networkId,
            @RequestParam String parameter,
            @RequestParam String range){
        // Aggregate trends — return stub shape with data from history
        return ResponseEntity.ok(Map.of(
            "networkId",networkId,"parameter",parameter,"range",range,
            "unit","","networkAverage",List.of(),"perLab",List.of()));
    }

    // ── Thresholds ────────────────────────────────────────────────────────────
    @GetMapping("/networks/{networkId}/thresholds")
    public ResponseEntity<Map<String,Object>> getThresholds(@PathVariable UUID networkId){
        return ResponseEntity.ok(alertService.getThresholds(networkId));
    }

    @PatchMapping("/networks/{networkId}/thresholds")
    @PreAuthorize("hasAnyRole('NETWORK_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<Map<String,Object>> updateThresholds(
            @PathVariable UUID networkId,@RequestBody Map<String,Object> body){
        return ResponseEntity.ok(alertService.updateThresholds(networkId,body));
    }

    // ── KPI Network Summary ───────────────────────────────────────────────────
    @GetMapping("/networks/{networkId}/kpis/summary")
    public ResponseEntity<Map<String,Object>> kpiSummary(
            @PathVariable UUID networkId,
            @RequestParam(required=false) Integer range){
        return ResponseEntity.ok(kpiService.networkSummary(networkId,range));
    }

    // ── WhatsApp Recipients ───────────────────────────────────────────────────
    @GetMapping("/networks/{networkId}/whatsapp/recipients")
    @PreAuthorize("hasAnyRole('NETWORK_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<Map<String,Object>> listRecipients(
            @PathVariable UUID networkId,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int perPage){
        var result=whatsAppService.listRecipients(networkId,PageRequest.of(page,perPage));
        return ResponseEntity.ok(pagedResponse(result));
    }

    @PostMapping("/networks/{networkId}/whatsapp/recipients")
    @PreAuthorize("hasAnyRole('NETWORK_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<Map<String,Object>> addRecipient(
            @PathVariable UUID networkId,@RequestBody Map<String,Object> body){
        return ResponseEntity.status(201).body(whatsAppService.addRecipient(
            networkId,
            (String)body.get("name"),
            (String)body.get("phone"),
            (String)body.getOrDefault("role",""),
            Boolean.parseBoolean(body.getOrDefault("receivesCritical","true").toString()),
            Boolean.parseBoolean(body.getOrDefault("receivesWarning","true").toString()),
            Boolean.parseBoolean(body.getOrDefault("receivesDailySummary","false").toString())
        ));
    }

    @PatchMapping("/networks/{networkId}/whatsapp/recipients/{recipientId}")
    @PreAuthorize("hasAnyRole('NETWORK_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<Map<String,Object>> updateRecipient(
            @PathVariable UUID networkId,@PathVariable UUID recipientId,
            @RequestBody Map<String,Object> body){
        return ResponseEntity.ok(whatsAppService.updateRecipient(networkId,recipientId,body));
    }

    @DeleteMapping("/networks/{networkId}/whatsapp/recipients/{recipientId}")
    @PreAuthorize("hasAnyRole('NETWORK_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<Void> deleteRecipient(@PathVariable UUID networkId,@PathVariable UUID recipientId){
        whatsAppService.deleteRecipient(networkId,recipientId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/networks/{networkId}/whatsapp/settings")
    public ResponseEntity<Map<String,Object>> getWaSettings(@PathVariable UUID networkId){
        return ResponseEntity.ok(whatsAppService.getSettings(networkId));
    }

    @PatchMapping("/networks/{networkId}/whatsapp/settings")
    @PreAuthorize("hasAnyRole('NETWORK_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<Map<String,Object>> updateWaSettings(
            @PathVariable UUID networkId,@RequestBody Map<String,Object> body){
        return ResponseEntity.ok(whatsAppService.updateSettings(networkId,body));
    }

    @GetMapping("/networks/{networkId}/whatsapp/logs")
    @PreAuthorize("hasAnyRole('NETWORK_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<Map<String,Object>> waLogs(
            @PathVariable UUID networkId,
            @RequestParam(required=false) String from,
            @RequestParam(required=false) String to,
            @RequestParam(required=false) String status,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int perPage){
        var result=whatsAppService.getLogs(networkId,from,to,status,PageRequest.of(page,perPage));
        return ResponseEntity.ok(pagedResponse(result));
    }

    // ── Hospitals under network ───────────────────────────────────────────────
    @GetMapping("/networks/{networkId}/hospitals")
    public ResponseEntity<Map<String,Object>> listHospitals(
            @PathVariable UUID networkId,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int perPage){
        var result=orgService.listHospitals(networkId,PageRequest.of(page,perPage));
        return ResponseEntity.ok(pagedResponse(result));
    }

    @PostMapping("/networks/{networkId}/hospitals")
    @PreAuthorize("hasAnyRole('NETWORK_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<Map<String,Object>> createHospital(
            @PathVariable UUID networkId,@RequestBody Map<String,String> body){
        return ResponseEntity.status(201).body(orgService.createHospital(
            networkId,body.get("name"),body.get("city"),body.get("address")));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private Map<String,Object> pagedResponse(org.springframework.data.domain.Page<?> page){
        return Map.of("data",page.getContent(),
            "meta",Map.of("total",page.getTotalElements(),"page",page.getNumber()+1,
                "perPage",page.getSize(),"totalPages",page.getTotalPages()));
    }
}
