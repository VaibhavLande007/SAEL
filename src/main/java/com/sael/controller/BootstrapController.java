package com.sael.controller;
import com.sael.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * Public bootstrap endpoint — NOT protected by any role.
 * Kept in its own controller so the class-level @PreAuthorize("hasRole('SYSTEM_ADMIN')")
 * on AdminController does not block it.
 * SecurityConfig explicitly permits /api/v1/admin/tenants/bootstrap at the URL level.
 */
@RestController
@RequestMapping("/api/v1/admin/tenants")
@RequiredArgsConstructor
public class BootstrapController {
    private final AdminService adminService;

    /**
     * POST /api/v1/admin/tenants/bootstrap
     * No token required. Only works when zero tenants exist (first-time setup).
     * Returns 409 once any tenant is present to prevent abuse.
     */
    @PostMapping("/bootstrap")
    public ResponseEntity<Map<String,Object>> bootstrapFirstTenant(@RequestBody Map<String,Object> body){
        if(adminService.tenantsExist()){
            return ResponseEntity.status(409).body(Map.of(
                "error","Bootstrap not allowed — tenants already exist. Use POST /admin/tenants with a SYSTEM_ADMIN token."));
        }
        @SuppressWarnings("unchecked")
        var adminUser=(Map<String,String>)body.get("adminUser");
        return ResponseEntity.status(201).body(adminService.createTenant(
            (String)body.get("name"),(String)body.get("slug"),
            adminUser.get("name"),adminUser.get("email"),adminUser.get("password")));
    }
}
