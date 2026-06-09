package com.sael.domain.repository;
import com.sael.domain.entity.Device;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface DeviceRepository extends JpaRepository<Device,UUID> {
    Optional<Device> findByDeviceUid(String deviceUid);
    Page<Device> findAllByTenantId(UUID tenantId, Pageable p);
    @Query(value="SELECT d FROM Device d WHERE d.tenantId=:tid AND (:status IS NULL OR d.onlineStatus=:status)")
    Page<Device> findByTenantAndStatus(@Param("tid") UUID tenantId, @Param("status") Boolean status, Pageable p);
    long countByTenantIdAndOnlineStatus(UUID tenantId, boolean status);
    long countByOnlineStatus(boolean status);
    @Modifying @Query("UPDATE Device d SET d.onlineStatus=:s, d.lastSeenAt=CURRENT_TIMESTAMP WHERE d.id=:id")
    void updateStatus(@Param("id") UUID id, @Param("s") boolean status);
}
