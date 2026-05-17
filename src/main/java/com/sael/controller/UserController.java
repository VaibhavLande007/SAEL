package com.sael.controller;
import com.sael.service.UserManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/users") @RequiredArgsConstructor
public class UserController {
    private final UserManagementService userService;

    @GetMapping
    @PreAuthorize("hasAnyRole('NETWORK_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<Map<String,Object>> list(
            @RequestParam(required=false) String role,
            @RequestParam(required=false) UUID labId,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int perPage){
        var result=userService.listUsers(role,PageRequest.of(page,perPage));
        return ResponseEntity.ok(Map.of("data",result.getContent(),
            "meta",Map.of("total",result.getTotalElements(),"page",result.getNumber()+1,
                "perPage",result.getSize(),"totalPages",result.getTotalPages())));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('NETWORK_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<Map<String,Object>> create(@RequestBody Map<String,Object> body){
        @SuppressWarnings("unchecked")
        var scopes=(List<Map<String,Object>>)body.get("scopes");
        return ResponseEntity.status(201).body(userService.createUser(
            (String)body.get("name"),(String)body.get("email"),
            (String)body.get("password"),(String)body.get("role"),scopes));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('NETWORK_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<Map<String,Object>> update(@PathVariable UUID id,@RequestBody Map<String,Object> body){
        @SuppressWarnings("unchecked")
        var scopes=(List<Map<String,Object>>)body.get("scopes");
        Boolean isActive=body.containsKey("isActive")?(Boolean)body.get("isActive"):null;
        return ResponseEntity.ok(userService.updateUser(id,(String)body.get("name"),(String)body.get("role"),isActive,scopes));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('NETWORK_ADMIN','SYSTEM_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id){
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
