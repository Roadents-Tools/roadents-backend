package org.tymit.projectdonut.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ilan on 7/8/16.
 */
public class LoggingUtils {
    private static List<String[]> log = new ArrayList<>();

    public static void logMessage(String tag, String message) {
        log.add(new String[]{"Message", tag, message});
    }

    public static void logError(Exception e) {
        StringBuilder msg = new StringBuilder();
        msg.append(e.getMessage() + "\n");
        for (StackTraceElement elm : e.getStackTrace()) {
            if (!elm.getClassName().contains("tymit")) continue; //Only our classes
            msg.append("     ");
            msg.append(elm.getClassName() + ":");
            msg.append(elm.getLineNumber());
        }
        log.add(new String[]{"ERROR", e.getClass().getName(), msg.toString()});
    }

    public static boolean isEmpty() {
        return log.isEmpty();
    }

    public static void printLog() {
        for (String[] msg : log) {
            System.out.printf("%10s, %10s: %s\n\n", msg[0], msg[1], msg[2]);
        }
    }


}
