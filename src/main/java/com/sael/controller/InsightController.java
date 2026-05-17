package com.sael.controller;
import com.sael.service.InsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/insights") @RequiredArgsConstructor
public class InsightController {
    private final InsightService insightService;

    @GetMapping
    public ResponseEntity<Map<String,Object>> list(
            @RequestParam(required=false) UUID networkId,
            @RequestParam(required=false) UUID labId,
            @RequestParam(required=false) String category,
            @RequestParam(required=false) String priority,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int perPage){
        var result=insightService.listInsights(labId,category,priority,PageRequest.of(page,perPage));
        return ResponseEntity.ok(Map.of("data",result.getContent(),
            "meta",Map.of("total",result.getTotalElements(),"page",result.getNumber()+1,
                "perPage",result.getSize(),"totalPages",result.getTotalPages())));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id){
        insightService.markRead(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','NETWORK_ADMIN')")
    public ResponseEntity<Map<String,Object>> generate(@RequestBody Map<String,Object> body){
        return ResponseEntity.status(202).body(insightService.triggerGeneration(
            (String)body.get("scope"),
            UUID.fromString(body.get("entityId").toString())));
    }
}
