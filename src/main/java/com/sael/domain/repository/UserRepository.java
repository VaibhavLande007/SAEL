package com.sael.domain.repository;
import com.sael.domain.entity.User;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.*;
public interface UserRepository extends JpaRepository<User,UUID> {
    boolean existsByEmailAndDeletedAtIsNull(String email);
    Page<User> findAllByTenant_IdAndDeletedAtIsNull(UUID tenantId, Pageable p);
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.userRoles ur LEFT JOIN FETCH ur.role WHERE u.email=:email AND u.deletedAt IS NULL")
    Optional<User> findByEmailAndDeletedAtIsNull(@Param("email") String email);

    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.userRoles ur LEFT JOIN FETCH ur.role WHERE u.id=:id AND u.tenant.id=:tenantId AND u.deletedAt IS NULL")
    Optional<User> findByIdAndTenant_IdAndDeletedAtIsNull(@Param("id") UUID id, @Param("tenantId") UUID tenantId);@Query("SELECT u FROM User u WHERE u.tenant.id=:tid AND u.deletedAt IS NULL AND (:role IS NULL OR EXISTS(SELECT ur FROM UserRole ur WHERE ur.user=u AND ur.role.name=:role))")
    Page<User> findByTenantAndRole(@Param("tid") UUID tenantId, @Param("role") String role, Pageable p);
    @Modifying @Query("UPDATE User u SET u.deletedAt=:now, u.isActive=false WHERE u.id=:id")
    void softDelete(@Param("id") UUID id, @Param("now") OffsetDateTime now);
    @Modifying @Query("UPDATE User u SET u.lastLoginAt=:now WHERE u.id=:id")
    void updateLastLogin(@Param("id") UUID id, @Param("now") OffsetDateTime now);
    @Query("SELECT COUNT(u) FROM User u WHERE u.tenant.id=:tid AND u.deletedAt IS NULL AND u.lastLoginAt >= :since")
    long countActiveLastDays(@Param("tid") UUID tenantId, @Param("since") OffsetDateTime since);
}
