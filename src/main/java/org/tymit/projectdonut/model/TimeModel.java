package org.tymit.projectdonut.model;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ilan on 7/8/16.
 */
public class TimeModel implements Comparable {


    public static final int YEAR = 0;
    public static final int MONTH = 1;
    public static final int DAY_OF_MONTH = 2;
    public static final int HOUR = 3;
    public static final int MINUTE = 4;
    public static final int SECOND = 5;

    public static final int DAY_OF_WEEK = 6;

    public static final int NUMBER_OF_KEYS = 7;

    private static final int[] TIMEMODEL_TO_CALENDAR_KEYS = new int[]{
            Calendar.YEAR,
            Calendar.MONTH,
            Calendar.DAY_OF_MONTH,
            Calendar.HOUR,
            Calendar.MINUTE,
            Calendar.SECOND,
            Calendar.DAY_OF_WEEK
    };

    private Map<Integer, Integer> attributeMap;
    private long unixTime = -1; //Separate long because it encompasses all data.

    public TimeModel() {
        attributeMap = new HashMap<>();
    }

    public static TimeModel now() {
        return TimeModel.fromUnixTime(System.currentTimeMillis());
    }

    public static TimeModel fromUnixTime(long time) {
        TimeModel rval = new TimeModel();
        rval.setUnixTime(time);
        return rval;
    }

    public TimeModel set(int key, int value) {
        if (key >= NUMBER_OF_KEYS || key < 0) {
            throw new IllegalArgumentException("Invalid Key.");
        }

        attributeMap.put(key, value);

        return this;
    }

    private Calendar toCalendar() {
        Calendar rval = Calendar.getInstance();
        for (int i = 0; i < NUMBER_OF_KEYS; i++) {
            rval.set(TIMEMODEL_TO_CALENDAR_KEYS[i], get(i));
        }
        return rval;
    }

    public int get(int key) {
        if (key >= NUMBER_OF_KEYS || key < 0) {
            throw new IllegalArgumentException("Invalid Key.");
        }
        int storedVal = attributeMap.getOrDefault(key, -1);
        if (storedVal < 0 && key == DAY_OF_WEEK) {
            return toCalendar().get(Calendar.DAY_OF_WEEK);
        }
        return storedVal;
    }

    public long getUnixTime() {
        if (unixTime < 0) {
            Calendar instance = new GregorianCalendar();
            for (int i = 0; i < NUMBER_OF_KEYS; i++) {
                int currentValue = get(i);
                if (currentValue < 0) return unixTime;
            }
            return instance.getTimeInMillis();
        }
        return unixTime;
    }

    public void setUnixTime(long time) {
        this.unixTime = time;

        Calendar toCalc = Calendar.getInstance();
        toCalc.setTimeInMillis(time);

        for (int i = 0; i < NUMBER_OF_KEYS; i++) {
            set(i, toCalc.get(TIMEMODEL_TO_CALENDAR_KEYS[i]));
        }
    }

    public void clear() {
        attributeMap.clear();
        unixTime = -1;
    }

    @Override
    public int compareTo(Object o) {
        if (!(o instanceof TimeModel)) return 0;
        if (this.getUnixTime() > 0 && ((TimeModel) o).getUnixTime() > 0)
            return (int) (getUnixTime() - ((TimeModel) o).getUnixTime());

        Calendar self = new GregorianCalendar();
        Calendar other = new GregorianCalendar();

        Calendar defaults = new GregorianCalendar();
        defaults.setTimeInMillis(System.currentTimeMillis());

        for (int i = 0; i < NUMBER_OF_KEYS; i++) {

            if (i == DAY_OF_WEEK) continue;//We really don't need the day of the week

            int selfVal = get(i);
            int otherVal = ((TimeModel) o).get(i);
            int calendarKey = TIMEMODEL_TO_CALENDAR_KEYS[i];
            int defaultVal = defaults.get(TIMEMODEL_TO_CALENDAR_KEYS[i]);

            //Neither has a value; default to today
            if (selfVal < 0 && otherVal < 0) {
                self.set(calendarKey, defaultVal);
                other.set(calendarKey, defaultVal);
            }

            //Other does not have a value; default to this
            if (selfVal >= 0 && otherVal < 0) {
                self.set(calendarKey, get(i));
                other.set(calendarKey, get(i));
            }

            //This does not have a value; default to other's
            if (selfVal < 0 && otherVal >= 0) {
                self.set(calendarKey, ((TimeModel) o).get(i));
                other.set(calendarKey, ((TimeModel) o).get(i));
            }

            //Both have values; set those values
            else {
                self.set(calendarKey, get(i));
                other.set(calendarKey, ((TimeModel) o).get(i));
            }
        }

        //We divide by 1000l because a) we dont have milliseconds in the model and
        //b) we need to try our best to fit the long into the int
        long timeDiff = (self.getTimeInMillis() / 1000l - other.getTimeInMillis() / 1000l);
        if (timeDiff < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        if (timeDiff > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) timeDiff;
    }
}
