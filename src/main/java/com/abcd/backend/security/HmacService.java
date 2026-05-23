package com.abcd.backend.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Service
public class HmacService {

    private static final Logger log = LoggerFactory.getLogger(HmacService.class);
    private static final long MAX_TIME_DIFF_MS = 300_000;
    private static final String HMAC_ALGO = "HmacSHA256";

    private final String secret;

    public HmacService(@Value("${hmac.secret:}") String secret) {
        this.secret = secret;
        if (secret == null || secret.isBlank()) {
            log.warn("HMAC_SECRET not configured — API request signing disabled");
        }
    }

    public String verifyAndGetExpected(String timestampHeader, String signatureHeader,
                                       String method, String path) {
        if (secret == null || secret.isBlank()) {
            return "ok";
        }
        if (timestampHeader == null || signatureHeader == null) {
            return "missing_headers";
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException e) {
            return "bad_timestamp";
        }
        long now = System.currentTimeMillis();
        if (Math.abs(now - timestamp) > MAX_TIME_DIFF_MS) {
            return "expired";
        }
        String payload = timestamp + method + path;
        String expected = hmacHex(payload);
        if (expected.equals(signatureHeader)) {
            return "ok";
        }
        return expected;
    }

    public boolean verify(String timestampHeader, String signatureHeader,
                          String method, String path) {
        return "ok".equals(verifyAndGetExpected(timestampHeader, signatureHeader, method, path));
    }

    private String hmacHex(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO
            );
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            log.error("HMAC computation failed", e);
            return "";
        }
    }
}
