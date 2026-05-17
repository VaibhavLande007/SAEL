package com.sael.domain.repository;
import com.sael.domain.entity.UserRole;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface UserRoleRepository extends JpaRepository<UserRole,UserRole.UserRoleId> {
    List<UserRole> findAllByUserId(UUID userId);
    boolean existsByUserIdAndRoleId(UUID userId, UUID roleId);
    @Modifying @Query("DELETE FROM UserRole ur WHERE ur.userId=:uid AND ur.roleId=:rid")
    void deleteByUserIdAndRoleId(@Param("uid") UUID userId, @Param("rid") UUID roleId);
}
