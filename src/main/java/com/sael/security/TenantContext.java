package com.sael.security;
import java.util.UUID;
public final class TenantContext {
    private static final ThreadLocal<UUID> TENANT=new ThreadLocal<>();
    private static final ThreadLocal<UUID> USER=new ThreadLocal<>();
    private TenantContext(){}
    public static void set(UUID tenantId,UUID userId){TENANT.set(tenantId);USER.set(userId);}
    public static UUID getTenantId(){return TENANT.get();}
    public static UUID getUserId(){return USER.get();}
    public static void clear(){TENANT.remove();USER.remove();}
    public static UUID requireTenantId(){UUID id=TENANT.get();if(id==null)throw new IllegalStateException("No tenant context");return id;}
}
