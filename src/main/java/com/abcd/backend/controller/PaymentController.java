package com.abcd.backend.controller;

import com.abcd.backend.security.HmacService;
import com.abcd.backend.security.RateLimitService;
import com.abcd.backend.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private static final int PREMIUM_AMOUNT = 9900;
    private static final int ANIMALS_AMOUNT = 100;

    private final PaymentService paymentService;
    private final HmacService hmacService;
    private final RateLimitService rateLimitService;

    public PaymentController(PaymentService paymentService,
                             HmacService hmacService,
                             RateLimitService rateLimitService) {
        this.paymentService = paymentService;
        this.hmacService = hmacService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/create-order")
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestBody Map<String, Object> request,
            HttpServletRequest servletRequest
    ) {
        try {
            String ip = getClientIp(servletRequest);
            if (!rateLimitService.isAllowed("create-order:" + ip)) {
                return ResponseEntity.status(429).body(Map.of("error", "Too many requests. Try again later."));
            }

            String hmacResult = hmacService.verifyAndGetExpected(
                    servletRequest.getHeader("X-Timestamp"),
                    servletRequest.getHeader("X-Signature"),
                    "POST",
                    "/api/payments/create-order"
            );
            if (!"ok".equals(hmacResult)) {
                Map<String, Object> errResponse = new HashMap<>();
                errResponse.put("error", "Invalid request signature");
                errResponse.put("hmac", hmacResult);
                return ResponseEntity.status(401).body(errResponse);
            }

            Object amountObj = request.get("amount");
            if (amountObj == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing 'amount' field"));
            }
            int amount;
            if (amountObj instanceof String) {
                amount = Integer.parseInt((String) amountObj);
            } else {
                amount = ((Number) amountObj).intValue();
            }
            String category = (String) request.getOrDefault("category", "premium");
            int expectedAmount = "animals".equals(category) ? ANIMALS_AMOUNT : PREMIUM_AMOUNT;
            if (amount != expectedAmount) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid amount for category: " + category));
            }
            String currency = (String) request.getOrDefault("currency", "INR");
            Map<String, Object> order = paymentService.createOrder(amount, currency);
            order.put("category", category);
            log.info("Order created: {} for category '{}'", order.get("orderId"), category);
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            log.error("createOrder failed", e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Failed to create order");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyPayment(
            @RequestBody Map<String, String> request,
            HttpServletRequest servletRequest
    ) {
        try {
            String ip = getClientIp(servletRequest);
            if (!rateLimitService.isAllowed("verify:" + ip)) {
                return ResponseEntity.status(429).body(Map.of("error", "Too many requests. Try again later."));
            }

            String hmacResult = hmacService.verifyAndGetExpected(
                    servletRequest.getHeader("X-Timestamp"),
                    servletRequest.getHeader("X-Signature"),
                    "POST",
                    "/api/payments/verify"
            );
            if (!"ok".equals(hmacResult)) {
                Map<String, Object> errResponse = new HashMap<>();
                errResponse.put("error", "Invalid request signature");
                errResponse.put("hmac", hmacResult);
                return ResponseEntity.status(401).body(errResponse);
            }

            String orderId = request.get("razorpay_order_id");
            String paymentId = request.get("razorpay_payment_id");
            String signature = request.get("razorpay_signature");
            String category = request.getOrDefault("category", "premium");

            if (orderId == null || paymentId == null || signature == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing payment details"));
            }

            boolean isValid = paymentService.verifyPayment(orderId, paymentId, signature);
            log.info("Payment verified: orderId={}, category={}, valid={}", orderId, category, isValid);
            Map<String, Object> result = new HashMap<>();
            result.put("success", isValid);
            result.put("category", category);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("verifyPayment failed", e);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Verification failed");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
