package org.tymit.projectdonut.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ilan on 7/8/16.
 */
public class LoggingUtils {
    private static List<String[]> log = new ArrayList<>();
    private static List<Exception> errors = new ArrayList<>();

    private static boolean printImmediate = false;

    public static void logMessage(String tag, String messageLocale, Object... args) {
        logMessage(tag, String.format(messageLocale, args));
    }

    public static void logMessage(String tag, String message) {
        log.add(new String[]{"Message", tag, message});
        if (printImmediate) printLog();
    }

    public static void printLog() {
        for (String[] msg : log) {
            System.out.printf("%-7s %s: %s\n\n", msg[0] + ",", msg[1], msg[2]);
        }
        log.clear();
    }

    public static void logError(String origin, String message) {
        logError(new Exception(origin + ": " + message));
    }

    public static void logError(Exception e) {
        if (printImmediate) return;
        errors.add(e);
        StringBuilder msg = new StringBuilder();
        msg.append(e.getMessage().replaceAll("\n\n", "\n"));
        for (StackTraceElement elm : e.getStackTrace()) {
            if (!elm.getClassName().contains("tymit")) continue; //Only our classes
            msg.append("     ");
            msg.append(elm.getClassName() + ":");
            msg.append(elm.getLineNumber());
            msg.append("\n");
        }
        log.add(new String[]{"ERROR", e.getClass().getName(), msg.toString()});
        if (printImmediate) printLog();
    }

    public static boolean isEmpty() {
        return log.isEmpty();
    }

    public static boolean hasErrors() {
        return !errors.isEmpty();
    }

    public static List<Exception> getErrors() {
        return errors;
    }

    public static void setPrintImmediate(boolean printImmediate) {
        LoggingUtils.printImmediate = printImmediate;
    }
}
