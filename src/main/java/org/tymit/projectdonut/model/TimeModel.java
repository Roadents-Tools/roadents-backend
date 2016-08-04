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
    private boolean recalculateUnixTime; //if we set stuff after unix time
    private long unixTime = -1; //Separate long because it encompasses all data.

    private TimeModel() {
        attributeMap = new HashMap<>();
        recalculateUnixTime = true;
    }

    public static TimeModel empty() {
        return new TimeModel();
    }

    public static TimeModel now() {
        return TimeModel.fromUnixTime(System.currentTimeMillis());
    }

    public static TimeModel fromUnixTime(long time) {
        TimeModel newModel = new TimeModel();

        newModel.unixTime = time;

        Calendar toCalc = Calendar.getInstance();
        toCalc.setTimeInMillis(time);

        for (int i = 0; i < NUMBER_OF_KEYS; i++) {
            newModel.attributeMap.put(i, toCalc.get(TIMEMODEL_TO_CALENDAR_KEYS[i]));
        }
        newModel.recalculateUnixTime = false;
        return newModel;
    }

    public static TimeModel fromUnixTimeDelta(long time) {
        TimeModel rval = new TimeModel();
        int total = (int) (time / 1000l);

        int seconds = total % 60;
        rval.attributeMap.put(SECOND, seconds);
        total = total / 60;

        int minutes = total % 60;
        rval.attributeMap.put(MINUTE, minutes);
        total = total / 60;

        int hours = total % 24;
        rval.attributeMap.put(HOUR, hours);
        total = total / 24;

        int days = total;
        rval.attributeMap.put(DAY_OF_MONTH, days);
        return rval;
    }

    public TimeModel set(int key, int value) {
        if (key >= NUMBER_OF_KEYS || key < 0) {
            throw new IllegalArgumentException("Invalid Key.");
        }
        TimeModel newModel = clone();
        newModel.attributeMap.put(key, value);
        newModel.recalculateUnixTime = true;
        return newModel;
    }

    private Calendar toCalendar() {
        Calendar rval = Calendar.getInstance();
        for (int i = 0; i < NUMBER_OF_KEYS - 1; i++) {
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
        if (recalculateUnixTime || unixTime < 0) {
            Calendar instance = new GregorianCalendar();
            for (int i = 0; i < NUMBER_OF_KEYS; i++) {
                if (i == DAY_OF_WEEK) continue;
                int currentValue = get(i);
                if (currentValue < 0) return -1;
                instance.set(TIMEMODEL_TO_CALENDAR_KEYS[i], currentValue);
            }
            unixTime = instance.getTimeInMillis();
            recalculateUnixTime = false;
        }
        return unixTime;
    }

    public long getUnixTimeDelta() {
        if (getUnixTime() > 0) return getUnixTime();

        long unixDelta = 0l;
        unixDelta += (get(SECOND) > 0) ? get(SECOND) * 1000l : 0;
        unixDelta += (get(MINUTE) > 0) ? get(MINUTE) * 1000l * 60l : 0;
        unixDelta += (get(HOUR) > 0) ? get(HOUR) * 1000l * 60l * 60l : 0;
        unixDelta += (get(DAY_OF_MONTH) > 0) ? get(DAY_OF_MONTH) * 1000l * 60l * 60l * 24l : 0;
        return unixDelta;
    }


    public TimeModel addUnixTime(long unixTime) {
        if (isDelta()) {
            long newUnix = getUnixTimeDelta() + unixTime;
            return TimeModel.fromUnixTimeDelta(newUnix);
        } else {
            long newUnix = getUnixTime() + unixTime;
            return TimeModel.fromUnixTime(newUnix);
        }
    }

    public boolean isDelta() {
        return getUnixTime() < 0;
    }

    public TimeModel toInstant(TimeModel base) {
        if (!isDelta()) return this;
        TimeModel newModel = base.clone();
        for (int i = 0; i < NUMBER_OF_KEYS; i++) {
            int ourVal = get(i);
            if (ourVal > 0) newModel.set(i, ourVal);
        }
        return newModel;
    }

    public long compareTo(TimeModel o) {
        if (this.getUnixTime() > 0 && o.getUnixTime() > 0) {
            return getUnixTime() - o.getUnixTime();
        }

        Calendar self = new GregorianCalendar();
        Calendar other = new GregorianCalendar();

        Calendar defaults = new GregorianCalendar();
        defaults.setTimeInMillis(System.currentTimeMillis());

        for (int i = 0; i < NUMBER_OF_KEYS; i++) {

            if (i == DAY_OF_WEEK) continue;//We really don't need the day of the week

            int selfVal = get(i);
            int otherVal = o.get(i);
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
                self.set(calendarKey, o.get(i));
                other.set(calendarKey, o.get(i));
            }

            //Both have values; set those values
            if (selfVal >= 0 && otherVal >= 0) {
                self.set(calendarKey, get(i));
                other.set(calendarKey, o.get(i));
            }
        }

        long timeDiff = (self.getTimeInMillis() - other.getTimeInMillis());
        return timeDiff;
    }

    @Override
    public int compareTo(Object o) {
        if (!(o instanceof TimeModel)) return 0;
        return (int) compareTo((TimeModel) o);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TimeModel model = (TimeModel) o;
        if (getUnixTime() > 0 && getUnixTime() == model.getUnixTime()) return true;
        return attributeMap.equals(model.attributeMap);
    }

    public TimeModel clone() {
        TimeModel newModel = new TimeModel();
        if (unixTime >= 0 && !recalculateUnixTime) {
            return TimeModel.fromUnixTime(unixTime);
        }
        for (int tag : attributeMap.keySet()) {
            newModel.attributeMap.put(tag, get(tag));
        }
        return newModel;
    }

    @Override
    public String toString() {
        return "TimeModel{" +
                "attributeMap=" + attributeMap +
                ", unixTime=" + unixTime +
                ", recalc:" + recalculateUnixTime +
                '}';
    }
}
