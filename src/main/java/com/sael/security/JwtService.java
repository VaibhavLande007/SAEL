package com.sael.security;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.util.*;

@Service @Slf4j
public class JwtService {
    @Value("${jwt.secret}") private String secret;
    @Value("${jwt.access-token-expiry-ms}") private long expiryMs;
    private SecretKey key(){return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));}
    public String generate(UUID userId,UUID tenantId,List<String> roles){
        return Jwts.builder().subject(userId.toString())
            .claim("tenantId",tenantId.toString()).claim("roles",roles)
            .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis()+expiryMs))
            .signWith(key()).compact();
    }
    public Claims parse(String token){return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();}
    public boolean isValid(String token){try{parse(token);return true;}catch(Exception e){return false;}}
    public UUID extractUserId(String t){return UUID.fromString(parse(t).getSubject());}
    public UUID extractTenantId(String t){return UUID.fromString(parse(t).get("tenantId",String.class));}
    @SuppressWarnings("unchecked") public List<String> extractRoles(String t){return parse(t).get("roles",List.class);}
    public long getExpiryMs(){return expiryMs;}
}
