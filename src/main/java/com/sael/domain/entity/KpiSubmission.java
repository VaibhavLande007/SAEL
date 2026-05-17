package com.sael.domain.entity;
import com.sael.domain.enums.KpiSource;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name="kpi_submissions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class KpiSubmission {
    @Id @Column(columnDefinition="uuid") private UUID id;
    @Column(name="tenantId",nullable=false) private UUID tenantId;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="labId",nullable=false) private Lab lab;
    @Column(name="submittedBy") private UUID submittedBy;
    @Column(name="weekNumber",nullable=false) private Integer weekNumber;
    @Column(nullable=false) private Integer year;
    @Column(name="fertilisationRate",precision=5,scale=2) private BigDecimal fertilisationRate;
    @Column(name="blastocystRate",precision=5,scale=2) private BigDecimal blastocystRate;
    @Column(name="implantationRate",precision=5,scale=2) private BigDecimal implantationRate;
    @Column(name="m2Rate",precision=5,scale=2) private BigDecimal m2Rate;
    @Column(columnDefinition="TEXT") private String notes;
    @Enumerated(EnumType.STRING) @Builder.Default private KpiSource source=KpiSource.MANUAL;
    @Column(name="submittedAt") private OffsetDateTime submittedAt;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="submittedBy",insertable=false,updatable=false) private User submittedByUser;
    @PrePersist void prePersist(){if(id==null)id=UUID.randomUUID();if(submittedAt==null)submittedAt=OffsetDateTime.now();}
}
