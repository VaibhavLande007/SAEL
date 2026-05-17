package com.sael.domain.repository;
import com.sael.domain.entity.TelemetryReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface TelemetryRepository extends JpaRepository<TelemetryReading, Long> {

    // Returns the single latest flat row for a lab (all metrics in one row)
    @Query(value = """
        SELECT t.*
        FROM   telemetry_readings t
        WHERE  t."labId" = :labId
          AND  t."qualityFlag" != 'BAD'
        ORDER  BY t."recordedAt" DESC
        LIMIT  1
        """, nativeQuery = true)
    Optional<TelemetryReading> findLatestByLabId(@Param("labId") UUID labId);

    // Returns flat rows for history — caller filters by column name in Java
    // col param is validated in service before use (whitelist check)
    @Query(value = """
        SELECT t.id, t."recordedAt",
               t."co2Ppm", t."tvocPpb", t."temperatureC", t."humidityPct",
               t."pm25UgM3", t."pm10UgM3", t."pm1UgM3",
               t."doorIsOpen", t."qualityFlag"
        FROM   telemetry_readings t
        WHERE  t."labId"      = :labId
          AND  t."recordedAt" BETWEEN :from AND :to
          AND  t."qualityFlag" != 'BAD'
        ORDER  BY t."recordedAt" ASC
        LIMIT  500
        """, nativeQuery = true)
    List<Object[]> findHistory(
            @Param("labId") UUID labId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);
}
