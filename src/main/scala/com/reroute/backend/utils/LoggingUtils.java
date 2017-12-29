package com.reroute.backend.utils;

import com.reroute.backend.model.time.TimePoint;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

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
     * Prints the current log, clearing the in-memory queue.
     */
    private static void printLog() {
        for (String[] msg : log) {
            System.out.printf("%s:       %-7s %s: %s\n\n", msg[0], msg[1] + ",", msg[2], msg[3]);
        }
        log.clear();
    }

    /**
     * Adds an error message to the log.
     * @param origin the origin of the message
     * @param message the message itself
     */
    public static void logError(String origin, String message) {
        logError(new Exception(origin + ": " + message));
    }

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
                        now.year(), now.month(), now.dayOfMonth(),
                        now.hours(), now.minutes(), now.seconds(), now.milliseconds()
                ), "ERROR", e.getClass().getName(), msg.toString() };
    }

    /**
     * Sets whether all new log entries should be immediately sent to STDOUT
     * @param printImmediate whether we immediately print all log entries
     */
    public static void setPrintImmediate(boolean printImmediate) {
        LoggingUtils.printImmediate = printImmediate;
    }

}
