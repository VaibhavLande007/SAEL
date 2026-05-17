package com.sael.service;
import com.sael.domain.entity.*;
import com.sael.domain.enums.KpiSource;
import com.sael.domain.repository.*;
import com.sael.exception.*;
import com.sael.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.IsoFields;
import java.util.*;

@Service @RequiredArgsConstructor
public class KpiService {
    private final KpiRepository kpiRepo;
    private final LabRepository labRepo;
    private final UserRepository userRepo;

    public Page<Map<String,Object>> listKpis(UUID labId,String from,String to,Pageable p){
        verifyLabAccess(labId);
        if(from!=null&&to!=null){
            var f=OffsetDateTime.parse(from+"T00:00:00Z");
            var t=OffsetDateTime.parse(to+"T23:59:59Z");
            var list=kpiRepo.findByLabAndDateRange(labId,f,t);
            return new PageImpl<>(list.stream().map(this::toMap).toList(),p,list.size());
        }
        return kpiRepo.findAllByLab_IdOrderByYearDescWeekNumberDesc(labId,p).map(this::toMap);
    }

    @Transactional
    public Map<String,Object> submit(UUID labId,String weekStart,BigDecimal fert,BigDecimal blast,BigDecimal impl,BigDecimal m2,String notes){
        UUID tid=TenantContext.requireTenantId();
        var lab=verifyLabAccess(labId);
        var ws=LocalDate.parse(weekStart);
        int weekNum=ws.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int year=ws.get(IsoFields.WEEK_BASED_YEAR);
        // Upsert — one row per lab per week per year
        var existing=kpiRepo.findByLab_IdAndYearAndWeekNumber(labId,year,weekNum);
        var kpi=existing.orElseGet(KpiSubmission::new);
        kpi.setLab(lab);kpi.setTenantId(tid);
        kpi.setWeekNumber(weekNum);kpi.setYear(year);
        kpi.setFertilisationRate(fert);kpi.setBlastocystRate(blast);
        kpi.setImplantationRate(impl);kpi.setM2Rate(m2);
        kpi.setNotes(notes);kpi.setSource(KpiSource.MANUAL);
        kpi.setSubmittedBy(TenantContext.getUserId());
        kpi.setSubmittedAt(OffsetDateTime.now());
        return toMap(kpiRepo.save(kpi));
    }

    @Transactional
    public void delete(UUID labId,UUID kpiId){
        verifyLabAccess(labId);
        var kpi=kpiRepo.findById(kpiId)
            .filter(k->k.getLab().getId().equals(labId))
            .orElseThrow(()->new ResourceNotFoundException("KPI entry not found"));
        kpiRepo.delete(kpi);
    }

    public Map<String,Object> networkSummary(UUID networkId,Integer rangeWeeks){
        UUID tid=TenantContext.requireTenantId();
        int weeks=rangeWeeks!=null?rangeWeeks:12;
        var allKpis=kpiRepo.findByNetworkId(networkId);

        // Compute network averages
        BigDecimal avgFert=avg(allKpis.stream().map(KpiSubmission::getFertilisationRate).toList());
        BigDecimal avgBlast=avg(allKpis.stream().map(KpiSubmission::getBlastocystRate).toList());
        BigDecimal avgImpl=avg(allKpis.stream().map(KpiSubmission::getImplantationRate).toList());
        BigDecimal avgM2=avg(allKpis.stream().map(KpiSubmission::getM2Rate).toList());

        var networkAvg=new LinkedHashMap<String,Object>();
        networkAvg.put("fertilisationRate",avgFert);networkAvg.put("blastocystRate",avgBlast);
        networkAvg.put("implantationRate",avgImpl);networkAvg.put("m2Rate",avgM2);

        var resp=new LinkedHashMap<String,Object>();
        resp.put("networkId",networkId);resp.put("rangeWeeks",weeks);
        resp.put("networkAverages",networkAvg);
        resp.put("weeklyTrend",List.of());resp.put("perLab",List.of());
        return resp;
    }

    private Lab verifyLabAccess(UUID labId){
        return labRepo.findByIdAndTenant_IdAndDeletedAtIsNull(labId,TenantContext.requireTenantId())
            .orElseThrow(()->new ResourceNotFoundException("Lab not found"));
    }

    private BigDecimal avg(List<BigDecimal> vals){
        var nonNull=vals.stream().filter(Objects::nonNull).toList();
        if(nonNull.isEmpty())return null;
        return nonNull.stream().reduce(BigDecimal.ZERO,BigDecimal::add)
            .divide(BigDecimal.valueOf(nonNull.size()),2,java.math.RoundingMode.HALF_UP);
    }

    private Map<String,Object> toMap(KpiSubmission k){
        var m=new LinkedHashMap<String,Object>();
        m.put("id",k.getId());m.put("labId",k.getLab().getId());
        // Reconstruct week_start from year+weekNumber
        var ws=LocalDate.ofYearDay(k.getYear(),1)
            .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR,k.getWeekNumber())
            .with(java.time.DayOfWeek.MONDAY);
        m.put("weekStart",ws.toString());
        m.put("fertilisationRate",k.getFertilisationRate());
        m.put("blastocystRate",k.getBlastocystRate());
        m.put("implantationRate",k.getImplantationRate());
        m.put("m2Rate",k.getM2Rate());
        m.put("notes",k.getNotes());
        m.put("submittedBy",k.getSubmittedByUser()==null?null:
            Map.of("id",k.getSubmittedBy(),"name",k.getSubmittedByUser().getFullName()));
        m.put("submittedAt",k.getSubmittedAt());
        return m;
    }
}
