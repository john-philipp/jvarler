package com.jpd.utils;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/** Simple logging utils. Configures itself. */
public class LoggingUtils {

    public static final String FORMAT_PROPERTY = "java.util.logging.SimpleFormatter.format";
    public static final String SIMPLE_LOGGER_FORMAT = "[%1$tF %1$tT] [%4$-7s] %5$s %n";
    private static final AtomicBoolean configured = new AtomicBoolean(false);

    private LoggingUtils() {
    }

    private static void configure() {
        System.setProperty(FORMAT_PROPERTY, SIMPLE_LOGGER_FORMAT);
    }

    public static Logger getLogger(Class<?> cls) {
        if (!configured.get()) {
            configure();
            configured.set(true);
        }
        return Logger.getLogger(cls.getSimpleName());
    }
}
