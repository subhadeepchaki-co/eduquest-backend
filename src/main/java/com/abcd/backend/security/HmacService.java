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

    public boolean verify(String timestampHeader, String signatureHeader,
                          String method, String path) {
        if (secret == null || secret.isBlank()) {
            log.warn("HMAC_SECRET not set — allowing request without verification");
            return true;
        }
        if (timestampHeader == null || signatureHeader == null) {
            log.warn("Missing HMAC headers");
            return false;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException e) {
            log.warn("Invalid timestamp format: {}", timestampHeader);
            return false;
        }

        long now = System.currentTimeMillis();
        if (Math.abs(now - timestamp) > MAX_TIME_DIFF_MS) {
            log.warn("Timestamp too old or in future: diff={}ms", now - timestamp);
            return false;
        }

        String payload = timestamp + method + path;
        String expected = hmacHex(payload);
        boolean valid = expected.equals(signatureHeader);

        if (!valid) {
            log.warn("HMAC signature mismatch");
        }
        return valid;
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
