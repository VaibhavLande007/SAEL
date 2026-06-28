package com.sael.service;
import com.sael.domain.entity.*;
import com.sael.domain.enums.ScopeEntityType;
import com.sael.domain.repository.*;
import com.sael.exception.*;
import com.sael.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class UserManagementService {
    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final UserRoleRepository userRoleRepo;
    private final UserScopeRepository userScopeRepo;
    private final TenantRepository tenantRepo;
    private final PasswordEncoder encoder;

    public Page<Map<String,Object>> listUsers(String role,Pageable p){
        UUID tid=TenantContext.requireTenantId();
        return userRepo.findByTenantAndRole(tid,role,p).map(this::toMap);
    }

    @Transactional
    public Map<String,Object> createUser(String name,String email,String password,String role,List<Map<String,Object>> scopes){
        UUID tid=TenantContext.requireTenantId();
        if(userRepo.existsByEmailAndDeletedAtIsNull(email))
            throw new ConflictException("Email already exists: "+email);
        var tenant=tenantRepo.findById(tid).orElseThrow(()->new ResourceNotFoundException("Tenant not found"));
        var user=User.builder().tenant(tenant).fullName(name).email(email.toLowerCase().trim())
            .passwordHash(encoder.encode(password)).isActive(true).build();
        user=userRepo.save(user);
        // Assign role
        var roleEntity=roleRepo.findByNameAndTenantIdIsNull(role.toUpperCase())
            .or(() -> roleRepo.findByNameAndTenantIdIsNull(role.toLowerCase()))
            .orElseThrow(()->new ResourceNotFoundException("Role not found: "+role));
        userRoleRepo.save(UserRole.builder().userId(user.getId()).roleId(roleEntity.getId()).grantedBy(TenantContext.getUserId()).build());
        // Assign scopes
        if(scopes!=null){
            var savedUser=user;
            for(var s:scopes){
                var scope=UserScope.builder().user(savedUser)
                    .entityType(ScopeEntityType.valueOf(s.get("type").toString()))
                    .entityId(UUID.fromString(s.get("entityId").toString()))
                    .grantedBy(TenantContext.getUserId()).build();
                userScopeRepo.save(scope);
            }
        }
        return toMap(userRepo.findById(user.getId()).orElseThrow());
    }

    @Transactional
    public Map<String,Object> updateUser(UUID userId,String name,String role,Boolean isActive,List<Map<String,Object>> scopes){
        UUID tid=TenantContext.requireTenantId();
        var user=userRepo.findByIdAndTenant_IdAndDeletedAtIsNull(userId,tid)
            .orElseThrow(()->new ResourceNotFoundException("User not found"));
        if(name!=null)user.setFullName(name);
        if(isActive!=null)user.setIsActive(isActive);
        userRepo.save(user);
        if(role!=null){
            userRoleRepo.deleteAll(userRoleRepo.findAllByUserId(userId));
            var roleEntity=roleRepo.findByNameAndTenantIdIsNull(role.toUpperCase())
                .or(() -> roleRepo.findByNameAndTenantIdIsNull(role.toLowerCase()))
                .orElseThrow(()->new ResourceNotFoundException("Role not found: "+role));
            userRoleRepo.save(UserRole.builder().userId(userId).roleId(roleEntity.getId()).build());
        }
        if(scopes!=null){
            userScopeRepo.deleteAll(userScopeRepo.findAllByUser_Id(userId));
            for(var s:scopes){
                var scope=UserScope.builder().user(user)
                    .entityType(ScopeEntityType.valueOf(s.get("type").toString()))
                    .entityId(UUID.fromString(s.get("entityId").toString())).build();
                userScopeRepo.save(scope);
            }
        }
        return toMap(userRepo.findById(userId).orElseThrow());
    }

    @Transactional
    public void deleteUser(UUID userId){
        UUID tid=TenantContext.requireTenantId();
        userRepo.findByIdAndTenant_IdAndDeletedAtIsNull(userId,tid)
            .orElseThrow(()->new ResourceNotFoundException("User not found"));
        userRepo.softDelete(userId,OffsetDateTime.now());
    }

    private Map<String,Object> toMap(User u){
        var roles=u.getUserRoles().stream().map(ur->ur.getRole().getName().toUpperCase()).toList();
        String primaryRole=roles.isEmpty()?"EMBRYOLOGIST":roles.get(0);
        var scopes=u.getUserScopes().stream().map(s->{
            var m=new LinkedHashMap<String,Object>();
            m.put("type",s.getEntityType().name());m.put("entityId",s.getEntityId());m.put("entityName","");
            return (Map<String,Object>)m;
        }).toList();
        var m=new LinkedHashMap<String,Object>();
        m.put("id",u.getId());m.put("name",u.getFullName());m.put("email",u.getEmail());
        m.put("role",primaryRole);m.put("tenantId",u.getTenant().getId());
        m.put("isActive",u.getIsActive());m.put("scopes",scopes);
        m.put("lastLoginAt",u.getLastLoginAt());m.put("createdAt",u.getCreatedAt());
        return m;
    }
}
