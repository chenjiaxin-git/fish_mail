package com.fishmall.admin.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishmall.admin.config.AdminProperties;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AdminJwtTokenService {
    private static final String ISSUER = "fishmall-admin";
    private static final String AUDIENCE = "fishmall-admin-web";
    private static final String ROLE = "ADMIN";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final AdminProperties properties;
    private final ObjectMapper objectMapper;

    public AdminJwtTokenService(AdminProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public TokenIssueResult issue(AdminPrincipal principal) {
        long now = Instant.now().getEpochSecond();
        long expiresAt = now + properties.tokenTtlSeconds();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("adminId", principal.adminId());
        claims.put("username", principal.username());
        claims.put("role", ROLE);
        claims.put("tokenVersion", principal.tokenVersion());
        claims.put("iat", now);
        claims.put("exp", expiresAt);
        claims.put("iss", ISSUER);
        claims.put("aud", AUDIENCE);
        String header = json(Map.of("alg", "HS256", "typ", "JWT"));
        String payload = json(claims);
        String signingInput = base64(header) + "." + base64(payload);
        String token = signingInput + "." + base64(hmac(signingInput));
        return new TokenIssueResult(token, properties.tokenTtlSeconds());
    }

    public AdminPrincipal parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new BusinessException(ErrorCode.AUTH_TOKEN_REVOKED);
            }
            String signingInput = parts[0] + "." + parts[1];
            String expected = base64(hmac(signingInput));
            if (!constantTimeEquals(expected, parts[2])) {
                throw new BusinessException(ErrorCode.AUTH_TOKEN_REVOKED);
            }
            JsonNode payload = objectMapper.readTree(new String(DECODER.decode(parts[1]), StandardCharsets.UTF_8));
            long now = Instant.now().getEpochSecond();
            if (!ISSUER.equals(payload.path("iss").asText())
                    || !AUDIENCE.equals(payload.path("aud").asText())
                    || !ROLE.equals(payload.path("role").asText())
                    || payload.path("exp").asLong(0) <= now) {
                throw new BusinessException(ErrorCode.AUTH_TOKEN_REVOKED);
            }
            return new AdminPrincipal(
                    payload.path("adminId").asLong(),
                    payload.path("username").asText(),
                    payload.path("tokenVersion").asLong()
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_REVOKED);
        }
    }

    private String json(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encode JWT payload", exception);
        }
    }

    private byte[] hmac(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign JWT", exception);
        }
    }

    private String base64(String input) {
        return ENCODER.encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }

    private String base64(byte[] input) {
        return ENCODER.encodeToString(input);
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left.length() != right.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < left.length(); i++) {
            result |= left.charAt(i) ^ right.charAt(i);
        }
        return result == 0;
    }

    public record TokenIssueResult(String token, long expiresIn) {
    }
}
