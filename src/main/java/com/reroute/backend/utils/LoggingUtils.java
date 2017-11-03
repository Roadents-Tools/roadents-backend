package com.reroute.backend.utils;

import com.reroute.backend.model.time.TimePoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Utilities related to logging.
 * Created by ilan on 7/8/16.
 */
public class LoggingUtils {
    private static final Queue<String[]> log = new ConcurrentLinkedQueue<>();
    private static final Queue<Exception> errors = new ConcurrentLinkedQueue<>();

    private static boolean printImmediate;

    static {
        setPrintImmediate(false);
    }

    /**
     * Adds a message to the log using printf.
     *
     * @param tag           the tag of the message
     * @param messageLocale the format string to pass to printf
     * @param args          the arguments to pass to printf
     */
    public static void logMessage(String tag, String messageLocale, Object... args) {
        logMessage(tag, String.format(messageLocale, args));
    }

    /**
     * Adds a message to the log.
     * @param tag the tag of the message
     * @param message the message to add
     */
    public static void logMessage(String tag, String message) {
        TimePoint now = TimePoint.now();
        log.add(new String[] {
                String.format(
                        "%d-%d-%d %d:%d:%d:%d",
                        now.getYear(), now.getMonth(), now.getDayOfMonth(),
                        now.getHour(), now.getMinute(), now.getSecond(), now.getMilliseconds()
                ),
                "Message", tag, message });
        if (printImmediate) printLog();
    }

    /**
     * Prints the current log, clearing the in-memory queue.
     */
    public static void printLog() {
        for (String[] msg : log) {
            System.out.printf("%s:       %-7s %s: %s\n\n", msg[0], msg[1] + ",", msg[2], msg[3]);
        }
        log.clear();
    }

    /**
     * Adds an error message to the log using printf.
     * @param origin the origin of the error
     * @param message the message string to pass to printf
     * @param args the arguments to pass to printf
     */
    public static void logError(String origin, String message, Object... args) {
        logError(origin, String.format(message, args));
    }

    /**
     * Adds an error message to the log.
     * @param origin the origin of the message
     * @param message the message itself
     */
    public static void logError(String origin, String message) {
        logError(new Exception(origin + ": " + message));
    }

    /**
     *
     * @param e
     */
    public static void logError(Exception e) {
        errors.add(e);
        log.add(errorToMessage(e));
        if (printImmediate) printLog();
    }

    private static String[] errorToMessage(Exception e) {
        TimePoint now = TimePoint.now();
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
        return new String[] {
                String.format(
                        "%d-%d-%d %d:%d:%d:%d",
                        now.getYear(), now.getMonth(), now.getDayOfMonth(),
                        now.getHour(), now.getMinute(), now.getSecond(), now.getMilliseconds()
                ), "ERROR", e.getClass().getName(), msg.toString() };
    }

    /**
     * Checks whether the log's current queue is empty.
     * @return whether the backlog is empty
     */
    public static boolean isEmpty() {
        return log.isEmpty();
    }

    /**
     * Checks whether there are errors in the log.
     * @return whether there are errors in the log
     */
    public static boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Gets the errors in the current, undumped log.
     * @return the errors in the log
     */
    public static List<Exception> getErrors() {
        List<Exception> newErrs = new ArrayList<>(errors);
        errors.clear();
        return newErrs;
    }

    /**
     * Sets whether all new log entries should be immediately sent to STDOUT
     * @param printImmediate whether we immediately print all log entries
     */
    public static void setPrintImmediate(boolean printImmediate) {
        LoggingUtils.printImmediate = printImmediate;
    }

    /**
     * Wraps a lambda with a checked exception so that the error is sent to the log.
     */
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

    /**
     * Wraps a lambda with a checked exception so that the error is sent to the log.
     */
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
