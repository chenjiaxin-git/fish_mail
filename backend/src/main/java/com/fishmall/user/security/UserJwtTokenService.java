package com.fishmall.user.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import com.fishmall.user.config.UserSecurityProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class UserJwtTokenService {
    private static final String ISSUER = "fishmall-user";
    private static final String AUDIENCE = "fishmall-miniprogram";
    private static final String ROLE = "USER";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final UserSecurityProperties properties;
    private final ObjectMapper objectMapper;

    public UserJwtTokenService(UserSecurityProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public TokenIssueResult issue(UserPrincipal principal) {
        long now = Instant.now().getEpochSecond();
        long expiresAt = now + properties.tokenTtlSeconds();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("wxUserId", principal.wxUserId());
        claims.put("role", ROLE);
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

    public UserPrincipal parse(String token) {
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
            return new UserPrincipal(payload.path("wxUserId").asLong());
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
