package com.sael.controller;
import com.sael.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;
    private final AlertService alertService;
    private final KpiService kpiService;
    private final DeviceService deviceService;

    @GetMapping("/overview")
    public ResponseEntity<Map<String,Object>> overview(){
        return ResponseEntity.ok(adminService.platformOverview());
    }

    @GetMapping("/tenants")
    public ResponseEntity<Map<String,Object>> listTenants(
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int perPage){
        var result=adminService.listTenants(PageRequest.of(page,perPage));
        return ResponseEntity.ok(Map.of("data",result.getContent(),
            "meta",Map.of("total",result.getTotalElements(),"page",result.getNumber()+1,
                "perPage",result.getSize(),"totalPages",result.getTotalPages())));
    }

    @PostMapping("/tenants")
    public ResponseEntity<Map<String,Object>> createTenant(@RequestBody Map<String,Object> body){
        @SuppressWarnings("unchecked")
        var adminUser=(Map<String,String>)body.get("adminUser");
        return ResponseEntity.status(201).body(adminService.createTenant(
            (String)body.get("name"),(String)body.get("slug"),
            adminUser.get("name"),adminUser.get("email"),adminUser.get("password")));
    }

    @PatchMapping("/tenants/{id}")
    public ResponseEntity<Map<String,Object>> updateTenant(
            @PathVariable UUID id,@RequestBody Map<String,Object> body){
        Boolean isActive=body.containsKey("isActive")?(Boolean)body.get("isActive"):null;
        return ResponseEntity.ok(adminService.updateTenant(id,(String)body.get("name"),isActive));
    }

    @GetMapping("/alerts")
    public ResponseEntity<Map<String,Object>> platformAlerts(
            @RequestParam(required=false) UUID tenantId,
            @RequestParam(required=false) UUID networkId,
            @RequestParam(required=false) String status,
            @RequestParam(required=false) String severity,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int perPage){
        // Platform-wide alert list — SYSTEM_ADMIN sees all
        var result=alertService.listAlerts(null,status,severity,PageRequest.of(page,perPage));
        return ResponseEntity.ok(Map.of("data",result.getContent(),
            "meta",Map.of("total",result.getTotalElements(),"page",result.getNumber()+1,
                "perPage",result.getSize(),"totalPages",result.getTotalPages())));
    }

    @GetMapping("/devices")
    public ResponseEntity<Map<String,Object>> platformDevices(
            @RequestParam(required=false) UUID tenantId,
            @RequestParam(required=false) String status,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int perPage){
        var result=deviceService.listDevices(null,null,status,PageRequest.of(page,perPage));
        return ResponseEntity.ok(Map.of("data",result.getContent(),
            "meta",Map.of("total",result.getTotalElements(),"page",result.getNumber()+1,
                "perPage",result.getSize(),"totalPages",result.getTotalPages())));
    }

    @GetMapping("/activity")
    public ResponseEntity<Map<String,Object>> activity(){
        // Audit log endpoint — return empty for now, connect to AuditLog entity
        return ResponseEntity.ok(Map.of("data",List.of(),
            "meta",Map.of("total",0,"page",1,"perPage",20,"totalPages",0)));
    }

    @GetMapping("/kpis")
    public ResponseEntity<Map<String,Object>> platformKpis(){
        return ResponseEntity.ok(Map.of("data",List.of(),
            "meta",Map.of("total",0,"page",1,"perPage",20,"totalPages",0)));
    }
}
