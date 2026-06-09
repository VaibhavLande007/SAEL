package com.sael.controller;

import com.sael.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class ConfigController {
    private final ConfigService configService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(configService.getConfig());
    }

    @PutMapping("/thresholds")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Void> updateThresholds(@RequestBody Map<String, Object> body) {
        List<Map<String, Object>> list = (List<Map<String, Object>>) body.get("thresholds");
        if (list != null) {
            configService.updateThresholds(list);
        }
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/toggles", method = {RequestMethod.POST, RequestMethod.PUT})
    @SuppressWarnings("unchecked")
    public ResponseEntity<Void> updateToggles(@RequestBody Map<String, Object> body) {
        Map<String, Object> toggles = (Map<String, Object>) body.get("toggles");
        String networkName = (String) body.get("networkName");
        if (toggles != null) {
            configService.updateToggles(toggles, networkName);
        }
        return ResponseEntity.ok().build();
    }

    @PutMapping("/network-name")
    public ResponseEntity<Void> updateNetworkName(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name != null) {
            configService.updateToggles(Map.of(), name);
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/centres")
    public ResponseEntity<Map<String, Object>> getCentres() {
        return ResponseEntity.ok(Map.of("hospitals", configService.getCentres()));
    }

    @GetMapping("/whatsapp")
    public ResponseEntity<List<Map<String, Object>>> getWhatsAppRecipients() {
        return ResponseEntity.ok(configService.getRecipients());
    }

    @PostMapping("/whatsapp")
    public ResponseEntity<Map<String, Object>> addWhatsAppRecipient(@RequestBody Map<String, String> body) {
        String address = body.get("address");
        if (address == null) {
            address = body.get("phone");
        }
        String label = body.get("label");
        return ResponseEntity.status(201).body(configService.addRecipient(address, label));
    }

    @PatchMapping("/whatsapp/{id}")
    public ResponseEntity<Map<String, Object>> toggleWhatsAppRecipient(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        Boolean isActive = (Boolean) body.get("isActive");
        if (isActive == null) {
            isActive = true;
        }
        return ResponseEntity.ok(configService.toggleRecipient(id, isActive));
    }

    @DeleteMapping("/whatsapp/{id}")
    public ResponseEntity<Map<String, Object>> deleteWhatsAppRecipient(@PathVariable UUID id) {
        configService.deleteRecipient(id);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
