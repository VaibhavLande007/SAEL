package com.sael.controller;
import com.sael.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor
public class AlertController {
    private final AlertService alertService;

    @GetMapping("/alerts")
    public ResponseEntity<Map<String,Object>> listAlerts(
            @RequestParam(required=false) UUID networkId,
            @RequestParam(required=false) UUID labId,
            @RequestParam(required=false) String status,
            @RequestParam(required=false) String severity,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int perPage){
        var result=alertService.listAlerts(labId,status,severity,PageRequest.of(page,perPage,Sort.by("triggeredAt").descending()));
        return ResponseEntity.ok(Map.of("data",result.getContent(),
            "meta",Map.of("total",result.getTotalElements(),"page",result.getNumber()+1,
                "perPage",result.getSize(),"totalPages",result.getTotalPages())));
    }

    @PostMapping("/alerts/{alertId}/acknowledge")
    public ResponseEntity<Map<String,Object>> acknowledge(@PathVariable UUID alertId){
        return ResponseEntity.ok(alertService.acknowledge(alertId));
    }

    @PostMapping("/alerts/{alertId}/resolve")
    public ResponseEntity<Map<String,Object>> resolve(@PathVariable UUID alertId){
        return ResponseEntity.ok(alertService.resolve(alertId));
    }
}
