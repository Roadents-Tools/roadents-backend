package com.reroute.backend.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Created by ilan on 7/8/16.
 */
public class LoggingUtils {
    private static final Queue<String[]> log = new ConcurrentLinkedQueue<>();
    private static final Queue<Exception> errors = new ConcurrentLinkedQueue<>();

    private static boolean printImmediate;

    static {
        setPrintImmediate(false);
    }

    public static void logMessage(String tag, String messageLocale, Object... args) {
        logMessage(tag, String.format(messageLocale, args));
    }

    public static void logMessage(String tag, String message) {
        log.add(new String[] { "" + System.currentTimeMillis(), "Message", tag, message });
        if (printImmediate) printLog();
    }

    public static void printLog() {
        for (String[] msg : log) {
            System.out.printf("%s:       %-7s %s: %s\n\n", msg[0], msg[1] + ",", msg[2], msg[3]);
        }
        log.clear();
    }

    public static void logError(String origin, String message, Object... args) {
        logError(origin, String.format(message, args));
    }

    public static void logError(String origin, String message) {
        logError(new Exception(origin + ": " + message));
    }

    public static void logError(Exception e) {
        errors.add(e);
        log.add(errorToMessage(e));
        if (printImmediate) printLog();
    }

    private static String[] errorToMessage(Exception e) {
        StringBuilder msg = new StringBuilder();
        if (e.getMessage() != null) msg.append(e.getMessage().replaceAll("\n\n", "\n"));
        else msg.append(e.getClass().getName());
        msg.append("\n\n");
        for (StackTraceElement elm : e.getStackTrace()) {
            msg.append("     ");
            msg.append(elm.getClassName()).append(":");
            msg.append(elm.getLineNumber());
            msg.append("\n");
        }
        return new String[] { "" + System.currentTimeMillis(), "ERROR", e.getClass().getName(), msg.toString() };
    }

    public static boolean isEmpty() {
        return log.isEmpty();
    }

    public static boolean hasErrors() {
        return !errors.isEmpty();
    }

    public static List<Exception> getErrors() {
        List<Exception> newErrs = new ArrayList<>(errors);
        errors.clear();
        return newErrs;
    }

    public static void setPrintImmediate(boolean printImmediate) {
        LoggingUtils.printImmediate = printImmediate;
    }

    @FunctionalInterface
    public interface WrappedFunction<T, R> extends Function<T, R> {

        @Override
        default R apply(T o) {
            try {
                return acceptWithException(o);
            } catch (Exception e) {
                LoggingUtils.logError(e);
                return null;
            }
        }

        R acceptWithException(T o) throws Exception;
    }

    @FunctionalInterface
    public interface WrappedConsumer<T> extends Consumer<T> {

        @Override
        default void accept(T t) {
            try {
                acceptWithException(t);
            } catch (Exception e) {
                LoggingUtils.logError(e);
            }
        }

        void acceptWithException(T t) throws Exception;
    }
}
