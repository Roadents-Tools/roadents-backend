package com.reroute.backend.utils;

/**
 * Static utility class for time-based helper methods.
 */
public class TimeUtils {

    /**
     * Converts a bit string to a boolean array.
     * Each character in the stream maps to 1 element of the array; '0' maps to false, and anything else maps to true.
     *
     * @param str the bit string to use
     * @return the boolean array representing the string
     */
    public static boolean[] bitStrToBools(String str) {
        boolean[] rval = new boolean[str.length()];
        for (int i = 0; i < str.length(); i++) {
            rval[i] = str.charAt(i) != '0';
        }

        return rval;
    }

    /**
     * Converts a boolean array to a string.
     * Each element is mapped to a '1' if true or a '0' if false.
     *
     * @param bools the boolean array to convert
     * @return the string containing 1's and 0's that maps to bools
     */
    public static String boolsToBitStr(boolean[] bools) {
        StringBuilder b = new StringBuilder(bools.length);
        for (boolean q : bools) {
            if (q) b.append('1');
            else b.append('0');
        }
        return b.toString();
    }
}
