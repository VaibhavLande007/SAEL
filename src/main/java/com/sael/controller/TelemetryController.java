package com.sael.controller;

import com.sael.domain.entity.UserScope;
import com.sael.domain.enums.ScopeEntityType;
import com.sael.domain.repository.UserScopeRepository;
import com.sael.security.TenantContext;
import com.sael.service.TelemetryStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/telemetry")
@RequiredArgsConstructor
public class TelemetryController {

    private final TelemetryStreamService telemetryStreamService;
    private final UserScopeRepository userScopeRepo;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.getUserId();

        final List<UUID> allowedLabIds;
        if (userId != null) {
            var scopes = userScopeRepo.findAllByUser_Id(userId);
            var labScopes = scopes.stream()
                .filter(s -> s.getEntityType() == ScopeEntityType.LAB)
                .map(UserScope::getEntityId)
                .toList();
            if (!scopes.isEmpty() && !labScopes.isEmpty()) {
                allowedLabIds = labScopes;
            } else {
                allowedLabIds = null;
            }
        } else {
            allowedLabIds = null;
        }

        return telemetryStreamService.createEmitter(tenantId, allowedLabIds);
    }
}
