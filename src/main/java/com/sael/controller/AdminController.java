package com.sael.controller;

import com.sael.domain.repository.LabRepository;
import com.sael.exception.ResourceNotFoundException;
import com.sael.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;
    private final TelemetryStreamService telemetryStreamService;
    private final LabRepository labRepo;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(adminService.getPlatformStats());
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview() {
        return ResponseEntity.ok(adminService.platformOverview());
    }

    @GetMapping("/tenants")
    public ResponseEntity<Map<String, Object>> listTenants(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int perPage) {
        var result = adminService.listTenants(PageRequest.of(page, perPage));
        return ResponseEntity.ok(Map.of("data", result.getContent(),
                "meta", Map.of("total", result.getTotalElements(), "page", result.getNumber() + 1,
                        "perPage", result.getSize(), "totalPages", result.getTotalPages())));
    }

    @PostMapping("/tenants")
    public ResponseEntity<Map<String, Object>> createTenant(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        var adminUser = (Map<String, String>) body.get("adminUser");
        return ResponseEntity.status(201).body(adminService.createTenant(
                (String) body.get("name"), (String) body.get("slug"),
                adminUser.get("name"), adminUser.get("email"), adminUser.get("password")));
    }

    @PatchMapping("/tenants/{id}")
    public ResponseEntity<Map<String, Object>> updateTenant(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Boolean isActive = body.containsKey("isActive") ? (Boolean) body.get("isActive") : null;
        return ResponseEntity.ok(adminService.updateTenant(id, (String) body.get("name"), isActive));
    }

    @GetMapping("/networks")
    public ResponseEntity<List<Map<String, Object>>> listNetworks() {
        return ResponseEntity.ok(adminService.listAllNetworks());
    }

    @PostMapping("/networks")
    public ResponseEntity<Map<String, Object>> createNetwork(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(adminService.createNetwork(body));
    }

    @GetMapping("/networks/{id}")
    public ResponseEntity<Map<String, Object>> getNetwork(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.getNetworkDetail(id));
    }

    @PatchMapping("/networks/{id}")
    public ResponseEntity<Map<String, Object>> updateNetwork(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(adminService.updateNetwork(id, body));
    }

    @PostMapping("/networks/{networkId}/users")
    public ResponseEntity<Map<String, Object>> createUser(
            @PathVariable UUID networkId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(adminService.createUser(networkId, body));
    }

    @PostMapping("/networks/{networkId}/hospitals")
    public ResponseEntity<Map<String, Object>> createHospital(
            @PathVariable UUID networkId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(adminService.createHospital(networkId, body));
    }

    @PatchMapping("/hospitals/{id}")
    public ResponseEntity<Map<String, Object>> updateHospital(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(adminService.updateHospital(id, body));
    }

    @DeleteMapping("/hospitals/{id}")
    public ResponseEntity<Void> deleteHospital(@PathVariable UUID id) {
        adminService.deleteHospital(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/labs/{id}")
    public ResponseEntity<Map<String, Object>> getLab(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.getLabDetail(id));
    }

    @PostMapping("/hospitals/{hospitalId}/labs")
    public ResponseEntity<Map<String, Object>> createLab(
            @PathVariable UUID hospitalId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(adminService.createLab(hospitalId, body));
    }

    @PatchMapping("/labs/{id}")
    public ResponseEntity<Map<String, Object>> updateLab(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(adminService.updateLab(id, body));
    }

    @DeleteMapping("/labs/{id}")
    public ResponseEntity<Void> deleteLab(@PathVariable UUID id) {
        adminService.deleteLab(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/labs/{labId}/devices")
    public ResponseEntity<Map<String, Object>> registerDevice(
            @PathVariable UUID labId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(adminService.registerDevice(labId, body));
    }

    @PatchMapping("/devices/{id}")
    public ResponseEntity<Map<String, Object>> updateDevice(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(adminService.updateDevice(id, body));
    }

    @DeleteMapping("/devices/{id}")
    public ResponseEntity<Void> removeDevice(@PathVariable UUID id) {
        adminService.deleteDevice(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/networks/{networkId}/recipients")
    public ResponseEntity<List<Map<String, Object>>> listRecipients(@PathVariable UUID networkId) {
        return ResponseEntity.ok(adminService.listRecipients(networkId));
    }

    @PostMapping("/networks/{networkId}/recipients")
    public ResponseEntity<Map<String, Object>> createRecipient(
            @PathVariable UUID networkId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(adminService.createRecipient(networkId, body));
    }

    @PatchMapping("/recipients/{id}")
    public ResponseEntity<Map<String, Object>> updateRecipient(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(adminService.updateRecipient(id, body));
    }

    @DeleteMapping("/recipients/{id}")
    public ResponseEntity<Map<String, Object>> deleteRecipient(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.deleteRecipient(id));
    }

    @GetMapping("/labs/{labId}/alerts")
    public ResponseEntity<Map<String, Object>> getLabAlerts(
            @PathVariable UUID labId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int perPage) {
        return ResponseEntity.ok(adminService.getLabAlerts(labId, status, page, perPage));
    }

    @GetMapping("/labs/{labId}/history")
    public ResponseEntity<List<Map<String, Object>>> getLabHistory(
            @PathVariable UUID labId,
            @RequestParam(defaultValue = "30") int minutes) {
        return ResponseEntity.ok(adminService.getLabHistory(labId, minutes));
    }

    @GetMapping(value = "/labs/{labId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getLabStream(@PathVariable UUID labId) {
        UUID tenantId = labRepo.findByIdAndDeletedAtIsNull(labId)
                .map(lab -> lab.getTenant().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Lab not found"));
        return telemetryStreamService.createEmitter(tenantId, List.of(labId));
    }

    @GetMapping("/alerts")
    public ResponseEntity<Map<String, Object>> platformAlerts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        return ResponseEntity.ok(adminService.getGlobalAlerts(status, severity, limit, offset));
    }

    @GetMapping("/activity")
    public ResponseEntity<Map<String, Object>> activity() {
        return ResponseEntity.ok(Map.of("data", List.of(),
                "meta", Map.of("total", 0, "page", 1, "perPage", 20, "totalPages", 0)));
    }

    @GetMapping("/kpis")
    public ResponseEntity<Map<String, Object>> platformKpis() {
        return ResponseEntity.ok(Map.of("data", List.of(),
                "meta", Map.of("total", 0, "page", 1, "perPage", 20, "totalPages", 0)));
    }
}
