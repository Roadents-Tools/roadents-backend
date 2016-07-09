package org.tymit.projectdonut.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ilan on 7/8/16.
 */
public class LoggingUtil {
    private static List<String[]> log = new ArrayList<>();

    public static void logError(String tag, String message) {
        log.add(new String[]{"ERROR", tag, message});
    }

    public static boolean isEmpty() {
        return log.isEmpty();
    }

    public static void printLog() {
        for (String[] msg : log) {
            System.out.printf("%10s, %10s: %s", msg[0], msg[1], msg[2]);
        }
    }


}
