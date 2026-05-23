package com.abcd.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

    private static final Logger log =
            LoggerFactory.getLogger(
                    BackendApplication.class
            );

    public static void main(String[] args) {

        SpringApplication.run(
                BackendApplication.class,
                args
        );

        log.info("=================================");
        log.info(" Razorpay Backend Started ");
        log.info("=================================");
        log.info(" Server running on:");
        log.info(" http://localhost:8080");
        log.info("=================================");
        log.info(" API Endpoints:");
        log.info(" POST /api/payments/create-order");
        log.info(" POST /api/payments/verify");
        log.info(" GET  /api/payments/health");
        log.info("=================================");
    }
}