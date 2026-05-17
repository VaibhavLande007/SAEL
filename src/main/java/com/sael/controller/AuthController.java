package com.sael.controller;
import com.sael.security.TenantContext;
import com.sael.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/v1/auth") @RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<Map<String,Object>> login(@Valid @RequestBody LoginReq req){
        return ResponseEntity.ok(authService.login(req.getEmail(),req.getPassword(),req.getTenantSlug()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String,Object>> refresh(@RequestBody Map<String,String> body){
        // For now just confirm token validity — full refresh token storage can be added
        return ResponseEntity.ok(Map.of("token","","expiresIn",86400));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required=false) Map<String,String> body){
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String,Object>> me(){
        return ResponseEntity.ok(authService.getMe(TenantContext.getUserId(),TenantContext.requireTenantId()));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePwdReq req){
        authService.changePassword(TenantContext.getUserId(),TenantContext.requireTenantId(),
            req.getCurrentPassword(),req.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @Data static class LoginReq {
        @NotBlank @Email private String email;
        @NotBlank private String password;
        @NotBlank private String tenantSlug;
    }
    @Data static class ChangePwdReq {
        @NotBlank private String currentPassword;
        @NotBlank @Size(min=8) private String newPassword;
    }
}
