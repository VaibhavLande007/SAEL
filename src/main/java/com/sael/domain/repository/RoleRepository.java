package com.sael.domain.repository;
import com.sael.domain.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RoleRepository extends JpaRepository<Role,UUID> {
    Optional<Role> findByNameAndTenantIdIsNull(String name);
    List<Role> findAllByTenantIdIsNull();
}
