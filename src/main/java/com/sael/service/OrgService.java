package com.sael.service;
import com.sael.domain.entity.*;
import com.sael.domain.enums.LabType;
import com.sael.domain.repository.*;
import com.sael.exception.*;
import com.sael.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class OrgService {
    private final NetworkRepository networkRepo;
    private final HospitalRepository hospitalRepo;
    private final LabRepository labRepo;

    // ── Networks ──────────────────────────────────────────────────────────────
    @Transactional(readOnly=true)
    public Page<Map<String,Object>> listNetworks(Pageable p){
        return networkRepo.findAllByTenant_IdAndDeletedAtIsNull(TenantContext.requireTenantId(),p)
            .map(this::networkMap);
    }
    public Map<String,Object> getNetwork(UUID id){
        return networkMap(networkRepo.findByIdAndTenant_IdAndDeletedAtIsNull(id,TenantContext.requireTenantId())
            .orElseThrow(()->new ResourceNotFoundException("Network not found")));
    }
    @Transactional
    public Map<String,Object> createNetwork(String name){
        var t=new com.sael.domain.entity.Tenant();
        t.setId(TenantContext.requireTenantId());
        var n=Network.builder().tenant(t).name(name).build();
        return networkMap(networkRepo.save(n));
    }
    @Transactional
    public Map<String,Object> updateNetwork(UUID id,String name){
        var n=networkRepo.findByIdAndTenant_IdAndDeletedAtIsNull(id,TenantContext.requireTenantId())
            .orElseThrow(()->new ResourceNotFoundException("Network not found"));
        if(name!=null)n.setName(name);
        return networkMap(networkRepo.save(n));
    }
    @Transactional
    public void deleteNetwork(UUID id){
        var n=networkRepo.findByIdAndTenant_IdAndDeletedAtIsNull(id,TenantContext.requireTenantId())
            .orElseThrow(()->new ResourceNotFoundException("Network not found"));
        n.setDeletedAt(OffsetDateTime.now());
        networkRepo.save(n);
    }

    // ── Hospitals ─────────────────────────────────────────────────────────────
    @Transactional(readOnly=true)
    public Page<Map<String,Object>> listHospitals(UUID networkId,Pageable p){
        return hospitalRepo.findAllByNetwork_IdAndDeletedAtIsNull(networkId,p).map(this::hospitalMap);
    }
    public Map<String,Object> getHospital(UUID id){
        return hospitalMap(hospitalRepo.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(()->new ResourceNotFoundException("Hospital not found")));
    }
    @Transactional
    public Map<String,Object> createHospital(UUID networkId,String name,String city,String address){
        var network=networkRepo.findByIdAndTenant_IdAndDeletedAtIsNull(networkId,TenantContext.requireTenantId())
            .orElseThrow(()->new ResourceNotFoundException("Network not found"));
        var t=new com.sael.domain.entity.Tenant();t.setId(TenantContext.requireTenantId());
        var h=Hospital.builder().tenant(t).network(network).name(name).city(city).address(address).build();
        return hospitalMap(hospitalRepo.save(h));
    }
    @Transactional
    public Map<String,Object> updateHospital(UUID id,String name,String city,String address){
        var h=hospitalRepo.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(()->new ResourceNotFoundException("Hospital not found"));
        if(name!=null)h.setName(name);
        if(city!=null)h.setCity(city);
        if(address!=null)h.setAddress(address);
        return hospitalMap(hospitalRepo.save(h));
    }
    @Transactional
    public void deleteHospital(UUID id){
        var h=hospitalRepo.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(()->new ResourceNotFoundException("Hospital not found"));
        h.setDeletedAt(OffsetDateTime.now());
        hospitalRepo.save(h);
    }

    // ── Labs ──────────────────────────────────────────────────────────────────
    @Transactional(readOnly=true)
    public Page<Map<String,Object>> listLabsByHospital(UUID hospitalId,Pageable p){
        return labRepo.findAllByHospital_IdAndDeletedAtIsNull(hospitalId,p).map(this::labMap);
    }
    @Transactional(readOnly=true)
    public Page<Map<String,Object>> listAllLabs(UUID networkId,String status,Pageable p){
        return labRepo.findAllScoped(TenantContext.requireTenantId(),networkId,p).map(this::labMap);
    }
    public Map<String,Object> getLab(UUID id){
        return labMap(labRepo.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(()->new ResourceNotFoundException("Lab not found")));
    }
    @Transactional
    public Map<String,Object> createLab(UUID hospitalId,String name){
        var hospital=hospitalRepo.findByIdAndDeletedAtIsNull(hospitalId)
            .orElseThrow(()->new ResourceNotFoundException("Hospital not found"));
        var t=new com.sael.domain.entity.Tenant();t.setId(TenantContext.requireTenantId());
        var lab=Lab.builder()
                .tenant(t)
                .hospital(hospital)
                .labType(LabType.IVF)
                .name(name)
                .build();
        return labMap(labRepo.save(lab));
    }
    @Transactional
    public Map<String,Object> updateLab(UUID id,String name){
        var lab=labRepo.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(()->new ResourceNotFoundException("Lab not found"));
        if(name!=null)lab.setName(name);
        return labMap(labRepo.save(lab));
    }
    @Transactional
    public void deleteLab(UUID id){
        var lab=labRepo.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(()->new ResourceNotFoundException("Lab not found"));
        lab.setDeletedAt(OffsetDateTime.now());
        labRepo.save(lab);
    }

    // ── Mappers ───────────────────────────────────────────────────────────────
    private Map<String,Object> networkMap(Network n){
        long hospitals=hospitalRepo.countByNetwork_IdAndDeletedAtIsNull(n.getId());
        long labs=labRepo.countByHospital_Network_IdAndDeletedAtIsNull(n.getId());
        var m=new java.util.LinkedHashMap<String,Object>();
        m.put("id",n.getId());
        m.put("name",n.getName());
        m.put("tenantId",n.getTenant().getId());
        m.put("tenantName",n.getTenant().getName());
        m.put("hospitalsCount",hospitals);
        m.put("labsCount",labs);
        m.put("onlineLabsCount",labs);
        m.put("createdAt",n.getCreatedAt());
        return m;
    }
    private Map<String,Object> hospitalMap(Hospital h){
        long labs=labRepo.countByHospital_IdAndDeletedAtIsNull(h.getId());
        var m=new LinkedHashMap<String,Object>();
        m.put("id",h.getId());m.put("name",h.getName());m.put("networkId",h.getNetwork().getId());
        m.put("city",h.getCity());m.put("address",h.getAddress());
        m.put("labsCount",labs);m.put("onlineLabsCount",labs);m.put("createdAt",h.getCreatedAt());
        return m;
    }
    private Map<String,Object> labMap(Lab l){
        var m=new LinkedHashMap<String,Object>();
        m.put("id",l.getId());m.put("name",l.getName());
        m.put("hospitalId",l.getHospital().getId());
        m.put("hospitalName",l.getHospital().getName());
        m.put("networkId",l.getHospital().getNetwork().getId());
        m.put("status","nominal");m.put("deviceCount",0);
        m.put("lastReadingAt",null);m.put("createdAt",l.getCreatedAt());
        return m;
    }
}
