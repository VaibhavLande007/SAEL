package com.sael.domain.repository;
import com.sael.domain.entity.Sensor;
import com.sael.domain.enums.SensorType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SensorRepository extends JpaRepository<Sensor,UUID> {
    List<Sensor> findAllByDevice_Id(UUID deviceId);
    Optional<Sensor> findByDevice_IdAndSensorType(UUID deviceId, SensorType type);
}
