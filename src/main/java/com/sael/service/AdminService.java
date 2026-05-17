package com.sael.service;
import com.sael.domain.entity.*;
import com.sael.domain.enums.*;
import com.sael.domain.repository.*;
import com.sael.exception.*;
import com.sael.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class AdminService {
    private final TenantRepository tenantRepo;
    private final UserRepository userRepo;
    private final LabRepository labRepo;
    private final DeviceRepository deviceRepo;
    private final AlertRepository alertRepo;
    private final RoleRepository roleRepo;
    private final UserRoleRepository userRoleRepo;
    private final PasswordEncoder encoder;

    public Map<String,Object> platformOverview(){
        long totalTenants=tenantRepo.count();
        long activeTenants=tenantRepo.findAll().stream()
            .filter(t->t.getStatus()==TenantStatus.ACTIVE&&t.getDeletedAt()==null).count();
        long totalLabs=labRepo.count();
        long totalDevices=deviceRepo.count();
        long onlineDevices=deviceRepo.countByTenantIdAndOnlineStatus(UUID.randomUUID(),true); // platform-wide
        OffsetDateTime since=OffsetDateTime.now().minusHours(24);

        var resp=new LinkedHashMap<String,Object>();
        resp.put("tenants",Map.of("total",totalTenants,"active",activeTenants));
        resp.put("networks",Map.of("total",0));
        resp.put("hospitals",Map.of("total",0));
        resp.put("labs",Map.of("total",totalLabs,"online",totalLabs,"offline",0));
        resp.put("devices",Map.of("total",totalDevices,"online",onlineDevices,"offline",totalDevices-onlineDevices));
        resp.put("users",Map.of("total",userRepo.count(),"activeLastDays",0));
        resp.put("alertsLast24h",Map.of("total",0,"critical",0,"warning",0,"acknowledged",0,"unacknowledged",0));
        return resp;
    }

    public boolean tenantsExist(){
        return tenantRepo.count()>0;
    }

    public Page<Map<String,Object>> listTenants(Pageable p){
        return tenantRepo.findAll(p).map(this::tenantMap);
    }

    @Transactional
    public Map<String,Object> createTenant(String name,String slug,String adminName,String adminEmail,String adminPassword){
        if(tenantRepo.existsBySlug(slug))
            throw new ConflictException("Slug already exists: "+slug);
        var tenant=Tenant.builder().name(name).slug(slug).status(TenantStatus.ACTIVE).plan(TenantPlan.STARTER).build();
        tenant=tenantRepo.save(tenant);
        // Create NETWORK_ADMIN user
        var adminUser=User.builder().tenant(tenant).fullName(adminName)
            .email(adminEmail.toLowerCase()).passwordHash(encoder.encode(adminPassword)).isActive(true).build();
        adminUser=userRepo.save(adminUser);
        // Assign network_admin role
        var role=roleRepo.findByNameAndTenantIdIsNull("NETWORK_ADMIN")
            .orElseThrow(()->new ResourceNotFoundException("Role NETWORK_ADMIN not found in roles table — ensure you ran docs/run_once_on_db.sql first"));
        userRoleRepo.save(UserRole.builder().userId(adminUser.getId()).roleId(role.getId()).build());
        var resp=tenantMap(tenant);
        resp.put("adminUser",Map.of("id",adminUser.getId(),"name",adminUser.getFullName(),"email",adminUser.getEmail()));
        return resp;
    }

    @Transactional
    public Map<String,Object> updateTenant(UUID tenantId,String name,Boolean isActive){
        var tenant=tenantRepo.findById(tenantId)
            .orElseThrow(()->new ResourceNotFoundException("Tenant not found"));
        if(name!=null)tenant.setName(name);
        if(isActive!=null)tenant.setStatus(isActive?TenantStatus.ACTIVE:TenantStatus.SUSPENDED);
        return tenantMap(tenantRepo.save(tenant));
    }

    private LinkedHashMap<String,Object> tenantMap(Tenant t){
        var m=new LinkedHashMap<String,Object>();
        m.put("id",t.getId());m.put("name",t.getName());m.put("slug",t.getSlug());
        m.put("isActive",t.getStatus()==TenantStatus.ACTIVE);
        m.put("networksCount",0);m.put("labsCount",0);m.put("usersCount",0);
        m.put("createdAt",t.getCreatedAt());m.put("lastActivityAt",t.getUpdatedAt());
        return m;
    }
}
