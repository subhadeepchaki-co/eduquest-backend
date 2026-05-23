package com.abcd.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentService.class);

    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 30000;

    private final String keyId;
    private final String keySecret;

    private final RestTemplate restTemplate;

    public PaymentService(
            @Value("${razorpay.key.id}") String keyId,
            @Value("${razorpay.key.secret}") String keySecret
    ) {

        this.keyId = keyId;
        this.keySecret = keySecret;

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);

        this.restTemplate = new RestTemplate(factory);

        if (keyId == null || keyId.isBlank() ||
                keySecret == null || keySecret.isBlank()) {
            log.warn(
                    "Razorpay credentials not configured. " +
                    "Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET " +
                    "environment variables to enable payments."
            );
        }
    }

    /**
     * CREATE RAZORPAY ORDER
     */
    public Map<String, Object> createOrder(
            int amountPaise,
            String currency
    ) {

        if (keyId == null || keyId.isBlank() ||
                keySecret == null || keySecret.isBlank()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Razorpay not configured");
            return error;
        }

        try {

            HttpHeaders headers = new HttpHeaders();

            headers.setContentType(MediaType.APPLICATION_JSON);

            // Razorpay API Authentication
            headers.setBasicAuth(keyId, keySecret);

            Map<String, Object> body =
                    new HashMap<>();

            body.put("amount", amountPaise);

            body.put("currency", currency);

            body.put(
                    "receipt",
                    "receipt_" +
                            UUID.randomUUID()
                                    .toString()
                                    .replace("-", "")
            );

            body.put("payment_capture", 1);

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(body, headers);

            log.info(
                    "Creating Razorpay order: amount={}, currency={}",
                    amountPaise,
                    currency
            );

            ResponseEntity<Map> response =
                    restTemplate.postForEntity(
                            "https://api.razorpay.com/v1/orders",
                            request,
                            Map.class
                    );

            Map<String, Object> responseBody =
                    response.getBody();

            if (responseBody == null) {

                throw new RuntimeException(
                        "Empty response from Razorpay"
                );
            }

            Map<String, Object> result =
                    new HashMap<>();

            result.put("success", true);

            result.put(
                    "orderId",
                    responseBody.get("id")
            );

            result.put(
                    "amount",
                    responseBody.get("amount")
            );

            result.put(
                    "currency",
                    responseBody.get("currency")
            );

            result.put(
                    "keyId",
                    keyId
            );

            log.info(
                    "Razorpay order created successfully: {}",
                    responseBody.get("id")
            );

            return result;

        } catch (Exception e) {

            log.error(
                    "Failed to create Razorpay order",
                    e
            );

            Map<String, Object> error =
                    new HashMap<>();

            error.put("success", false);

            error.put("message", e.getMessage());

            return error;
        }
    }

    /**
     * VERIFY PAYMENT SIGNATURE
     */
    public boolean verifyPayment(
            String razorpayOrderId,
            String razorpayPaymentId,
            String razorpaySignature
    ) {

        if (keyId == null || keyId.isBlank() ||
                keySecret == null || keySecret.isBlank()) {
            log.warn("Razorpay not configured — cannot verify payment");
            return false;
        }

        try {

            String payload =
                    razorpayOrderId +
                            "|" +
                            razorpayPaymentId;

            Mac sha256Hmac =
                    Mac.getInstance("HmacSHA256");

            SecretKeySpec secretKey =
                    new SecretKeySpec(
                            keySecret.getBytes(
                                    StandardCharsets.UTF_8
                            ),
                            "HmacSHA256"
                    );

            sha256Hmac.init(secretKey);

            byte[] hash =
                    sha256Hmac.doFinal(
                            payload.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            String generatedSignature =
                    bytesToHex(hash);

            boolean isValid =
                    generatedSignature.equals(
                            razorpaySignature
                    );

            log.info(
                    "Payment verification result: orderId={}, valid={}",
                    razorpayOrderId,
                    isValid
            );

            return isValid;

        } catch (Exception e) {

            log.error(
                    "Payment verification failed",
                    e
            );

            return false;
        }
    }

    /**
     * CONVERT BYTE ARRAY TO HEX STRING
     */
    private String bytesToHex(byte[] bytes) {

        StringBuilder hexString =
                new StringBuilder();

        for (byte b : bytes) {

            hexString.append(
                    String.format("%02x", b)
            );
        }

        return hexString.toString();
    }
}