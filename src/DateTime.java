import java.util.Date;

/**
 * Created by ilan on 6/19/16.
 */
public class DateTime {

    private int year = -1;
    private int month = -1;
    private int day = -1;
    private int hour = -1;
    private int minute = -1;
    private int second = -1;
    private int millis = -1;

    public int getYear() {
        return year;
    }

    public int getMonth() {
        return month;
    }

    public int getDay() {
        return day;
    }

    public int getHour() {
        return hour;
    }

    public int getMinute() {
        return minute;
    }

    public int getSecond() {
        return second;
    }

    public int getMillis() {
        return millis;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DateTime && distanceTo((DateTime) o).getUnixTime() == 0;
    }

    public long getUnixTime() {
        if (year == -1) {
            //TODO: Implement millis from time
            return -1;
        }
        Date out = new Date();
        out.setYear((year != -1) ? year : 1970);
        out.setMonth((month != -1) ? month : 1);
        out.setDate((day != -1) ? day : 1);
        out.setHours((hour != -1) ? hour : 0);
        out.setMinutes((minute != -1) ? minute : 0);
        out.setSeconds((second != -1) ? second : 0);
        return out.getTime() + millis;
    }

    public DateTime distanceTo(DateTime other) {

        //TODO: Write this
        return null;
    }
}
