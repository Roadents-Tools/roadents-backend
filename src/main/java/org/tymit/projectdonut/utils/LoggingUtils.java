package org.tymit.projectdonut.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Created by ilan on 7/8/16.
 */
public class LoggingUtils {
    private static final List<String[]> log = new ArrayList<>();
    private static final List<Exception> errors = new ArrayList<>();

    private static boolean printImmediate;

    static {
        setPrintImmediate(false);
    }

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
            if (!elm.getClassName().contains("tymit")) continue; //Only our classes
            msg.append("     ");
            msg.append(elm.getClassName()).append(":");
            msg.append(elm.getLineNumber());
            msg.append("\n");
        }
        return new String[]{"ERROR", e.getClass().getName(), msg.toString()};
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
