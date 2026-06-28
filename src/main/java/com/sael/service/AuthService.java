package com.sael.service;
import com.sael.domain.entity.*;
import com.sael.domain.repository.*;
import com.sael.exception.*;
import com.sael.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepo;
    private final TenantRepository tenantRepo;
    private final RoleRepository roleRepo;
    private final UserRoleRepository userRoleRepo;
    private final UserScopeRepository userScopeRepo;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    @Transactional
    public Map<String,Object> login(String email, String password, String tenantSlug){
        // Find user first
        var user=userRepo.findByEmailAndDeletedAtIsNull(email)
            .orElseThrow(()->new AuthException("Invalid email or password"));

        Tenant tenant;
        if (tenantSlug == null || tenantSlug.trim().isEmpty()) {
            tenant = user.getTenant();
        } else {
            // Validate tenant matching
            var foundTenant = tenantRepo.findBySlugAndDeletedAtIsNull(tenantSlug)
                .orElseThrow(()->new ResourceNotFoundException("Tenant '"+tenantSlug+"' not found"));
            if(!user.getTenant().getId().equals(foundTenant.getId()))
                throw new AuthException("Invalid email or password");
            tenant = foundTenant;
        }

        if(!encoder.matches(password,user.getPasswordHash()))
            throw new AuthException("Invalid email or password");
        if(Boolean.FALSE.equals(user.getIsActive()))
            throw new AuthException("Account is deactivated. Contact your administrator.");
        // Build roles/permissions
        var roles=user.getUserRoles().stream().map(ur->ur.getRole().getName().toUpperCase()).toList();
        // Pick primary role for the JWT (first one)
        String primaryRole=roles.isEmpty()?"EMBRYOLOGIST":roles.get(0).toUpperCase();
        // Build scopes
        var scopes=buildScopes(user);
        // Issue token
        String token=jwt.generate(user.getId(),tenant.getId(),roles,user.getFullName());
        // Update last login
        userRepo.updateLastLogin(user.getId(),OffsetDateTime.now());
        // Build response exactly as per API spec
        var userMap=new LinkedHashMap<String,Object>();
        userMap.put("id",user.getId());
        userMap.put("name",user.getFullName());
        userMap.put("fullName",user.getFullName());
        userMap.put("email",user.getEmail());
        userMap.put("role",primaryRole);
        userMap.put("tenantId",tenant.getId());
        userMap.put("tenantSlug",tenant.getSlug());
        userMap.put("tenantName",tenant.getName());
        userMap.put("scopes",scopes);
        var resp=new LinkedHashMap<String,Object>();
        resp.put("token",token);
        resp.put("accessToken",token);
        resp.put("refreshToken",UUID.randomUUID().toString()+UUID.randomUUID().toString().replace("-",""));
        resp.put("expiresIn",jwt.getExpiryMs()/1000);
        resp.put("user",userMap);
        return resp;
    }

    @Transactional(readOnly=true)
    public Map<String,Object> getMe(UUID userId,UUID tenantId){
        var user=userRepo.findByIdAndTenant_IdAndDeletedAtIsNull(userId,tenantId)
            .orElseThrow(()->new ResourceNotFoundException("User not found"));
        var tenant=tenantRepo.findById(tenantId).orElseThrow();
        var roles=user.getUserRoles().stream().map(ur->ur.getRole().getName().toUpperCase()).toList();
        String primaryRole=roles.isEmpty()?"EMBRYOLOGIST":roles.get(0).toUpperCase();
        var resp=new LinkedHashMap<String,Object>();
        resp.put("id",user.getId());
        resp.put("name",user.getFullName());
        resp.put("email",user.getEmail());
        resp.put("role",primaryRole);
        resp.put("tenantId",tenantId);
        resp.put("tenantSlug",tenant.getSlug());
        resp.put("tenantName",tenant.getName());
        resp.put("scopes",buildScopes(user));
        resp.put("lastLoginAt",user.getLastLoginAt());
        return resp;
    }

    @Transactional
    public void changePassword(UUID userId,UUID tenantId,String currentPwd,String newPwd){
        var user=userRepo.findByIdAndTenant_IdAndDeletedAtIsNull(userId,tenantId)
            .orElseThrow(()->new ResourceNotFoundException("User not found"));
        if(!encoder.matches(currentPwd,user.getPasswordHash()))
            throw new AuthException("Current password is incorrect");
        user.setPasswordHash(encoder.encode(newPwd));
        userRepo.save(user);
    }

    private List<Map<String,Object>> buildScopes(User user){
        return user.getUserScopes().stream().map(s->{
            var m=new LinkedHashMap<String,Object>();
            m.put("type",s.getEntityType().name());
            m.put("entityId",s.getEntityId());
            m.put("entityName",""); // resolve name if needed
            return (Map<String,Object>)m;
        }).toList();
    }
}
