package com.sael.controller;
import com.sael.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/devices") @RequiredArgsConstructor
public class DeviceController {
    private final DeviceService deviceService;

    @GetMapping
    public ResponseEntity<Map<String,Object>> list(
            @RequestParam(required=false) UUID labId,
            @RequestParam(required=false) UUID networkId,
            @RequestParam(required=false) String status,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int perPage){
        var result=deviceService.listDevices(labId,networkId,status,PageRequest.of(page,perPage));
        return ResponseEntity.ok(Map.of("data",result.getContent(),
            "meta",Map.of("total",result.getTotalElements(),"page",result.getNumber()+1,
                "perPage",result.getSize(),"totalPages",result.getTotalPages())));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('NETWORK_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<Map<String,Object>> register(@RequestBody Map<String,Object> body){
        return ResponseEntity.status(201).body(deviceService.register(
            (String)body.get("deviceUid"),
            UUID.fromString(body.get("labId").toString()),
            (String)body.get("name"),
            (String)body.get("firmwareVersion")));
    }
}
