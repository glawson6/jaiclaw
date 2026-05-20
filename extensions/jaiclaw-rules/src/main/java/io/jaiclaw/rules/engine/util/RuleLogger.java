package io.jaiclaw.rules.engine.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RuleLogger {
    private static final Logger logger = LoggerFactory.getLogger(RuleLogger.class);

    public static void logFact(Object fact) {
        logger.info("Fact inserted/updated: {}", fact);
    }

    public static void logMessage(String message) {
        logger.info(message);
    }
}
