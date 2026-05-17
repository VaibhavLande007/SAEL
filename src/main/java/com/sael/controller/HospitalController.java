package com.sael.controller;
import com.sael.service.OrgService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/hospitals") @RequiredArgsConstructor
public class HospitalController {
    private final OrgService orgService;

    @GetMapping("/{id}")
    public ResponseEntity<Map<String,Object>> get(@PathVariable UUID id){
        return ResponseEntity.ok(orgService.getHospital(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('NETWORK_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<Map<String,Object>> update(@PathVariable UUID id,@RequestBody Map<String,String> body){
        return ResponseEntity.ok(orgService.updateHospital(id,body.get("name"),body.get("city"),body.get("address")));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id){
        orgService.deleteHospital(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{hospitalId}/labs")
    public ResponseEntity<Map<String,Object>> listLabs(
            @PathVariable UUID hospitalId,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int perPage){
        var result=orgService.listLabsByHospital(hospitalId,PageRequest.of(page,perPage));
        return ResponseEntity.ok(Map.of("data",result.getContent(),
            "meta",Map.of("total",result.getTotalElements(),"page",result.getNumber()+1,
                "perPage",result.getSize(),"totalPages",result.getTotalPages())));
    }

    @PostMapping("/{hospitalId}/labs")
    @PreAuthorize("hasAnyRole('NETWORK_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<Map<String,Object>> createLab(
            @PathVariable UUID hospitalId,@RequestBody Map<String,String> body){
        return ResponseEntity.status(201).body(orgService.createLab(hospitalId,body.get("name")));
    }
}
