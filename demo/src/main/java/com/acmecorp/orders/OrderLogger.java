package com.acmecorp.orders;

public class OrderLogger {

    private final Logger log = Logger.getLogger(OrderLogger.class);

    // Real mismatch: 1 placeholder, 2 real arguments (neither is a Throwable) -- SHOULD be flagged.
    void logMismatch(String userId, String orderId) {
        log.info("Processing order for user {}", userId, orderId);
    }

    // Correct count -- should NOT be flagged.
    void logCorrect(String userId) {
        log.info("Order placed by user {}", userId);
    }

    // The real SLF4J trailing-Throwable case: 1 placeholder, 2 args, last is an exception -- CORRECT, should NOT be flagged.
    void logWithException(String userId, Exception e) {
        log.error("Failed to process order for user {}", userId, e);
    }
}

interface Logger {
    static Logger getLogger(Class<?> c) { return null; }
    void info(String msg, Object... args);
    void error(String msg, Object... args);
}
