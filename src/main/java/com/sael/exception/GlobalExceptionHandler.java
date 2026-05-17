package com.sael.exception;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestControllerAdvice @Slf4j
public class GlobalExceptionHandler {
    private Map<String,Object> err(String code,String msg){
        var e=new LinkedHashMap<String,Object>();
        e.put("code",code);e.put("message",msg);e.put("timestamp",OffsetDateTime.now());
        return Map.of("error",e);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> validation(MethodArgumentNotValidException ex){
        var errors=ex.getBindingResult().getFieldErrors().stream()
            .map(f->Map.of("field",f.getField(),"message",f.getDefaultMessage())).toList();
        var body=new LinkedHashMap<String,Object>();
        var e=new LinkedHashMap<String,Object>();
        e.put("code","VALIDATION_ERROR");e.put("message","Validation failed");e.put("errors",errors);
        body.put("error",e);
        return ResponseEntity.badRequest().body(body);
    }
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<?> auth(AuthException ex){return ResponseEntity.status(401).body(err("AUTH_ERROR",ex.getMessage()));}
    @ExceptionHandler({ForbiddenException.class,AccessDeniedException.class})
    public ResponseEntity<?> forbidden(Exception ex){return ResponseEntity.status(403).body(err("FORBIDDEN","Insufficient permissions"));}
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> notFound(ResourceNotFoundException ex){return ResponseEntity.status(404).body(err("NOT_FOUND",ex.getMessage()));}
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<?> conflict(ConflictException ex){return ResponseEntity.status(409).body(err("CONFLICT",ex.getMessage()));}
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> business(BusinessException ex){return ResponseEntity.status(422).body(err("BUSINESS_ERROR",ex.getMessage()));}
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> all(Exception ex){log.error("Unhandled: {}",ex.getMessage(),ex);return ResponseEntity.status(500).body(err("INTERNAL_ERROR","An unexpected error occurred"));}
}
