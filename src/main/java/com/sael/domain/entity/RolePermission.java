package com.sael.domain.entity;
import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@Entity @Table(name="role_permissions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@IdClass(RolePermission.RolePermissionId.class)
public class RolePermission {
    @Id @Column(name="roleId") private UUID roleId;
    @Id @Column(name="permissionId") private UUID permissionId;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="roleId",insertable=false,updatable=false) private Role role;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="permissionId",insertable=false,updatable=false) private Permission permission;
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class RolePermissionId implements Serializable { private UUID roleId; private UUID permissionId; }
}
