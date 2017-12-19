package com.reroute.backend.stations;

import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.distance.DistanceUnits;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.utils.LocationUtils;
import org.json.JSONObject;

public class WorldInfo {

    private static final long SECONDS_IN_DAY = 24 * 60 * 60 * 1000;
    private static final long SECONDS_IN_WEEK = SECONDS_IN_DAY * 7;

    private final LocationPoint center;
    private final Distance range;
    private final TimePoint startTime;
    private final TimeDelta maxDelta;

    private WorldInfo(LocationPoint center, Distance range, TimePoint startTime, TimeDelta maxDelta) {
        this.center = center;
        this.range = range;
        this.startTime = startTime;
        this.maxDelta = maxDelta;
    }

    public static WorldInfo unpackInfo(String packed) {
        JSONObject obj = new JSONObject(packed);
        return new WorldInfo(
                new StartPoint(new double[] { obj.getDouble("centerlat"), obj.getDouble("centerlng") }),
                new Distance(obj.getDouble("range"), DistanceUnits.METERS),
                TimePoint.from(obj.getLong("time"), obj.getString("tz")),
                new TimeDelta(obj.getLong("delta"))
        );
    }

    public boolean containsInfo(WorldInfo other) {

        if (getMaxDelta().getDeltaLong() < other.getMaxDelta().getDeltaLong()) {
            return false;
        }

        Distance maxDist = LocationUtils.distanceBetween(getCenter(), other.getCenter()).plus(other.getRange());
        if (getRange().inMeters() < maxDist.inMeters()) {
            return false;
        }

        //Null start times or max deltas indicate that we used ALL the times, not just some.
        if (getStartTime() == null || getStartTime().equals(TimePoint.NULL)
                || getMaxDelta() == null || getMaxDelta().equals(TimeDelta.NULL)) {
            return true;
        }

        //Right now we only really care about the time of the day and day of the week.
        //We therefore convert each start and end time to a "seconds since 00:00:00 on Sunday" format,
        //to easily compare the ranges.


        //We span longer than a week already, and should therefore have all times in our area.
        if (maxDelta.inDays() >= 7) {
            return true;
        }

        //Since we have a finite range and the other one doesn't, we cannot contain them.
        if (other.getStartTime() == null || other.getStartTime().equals(TimePoint.NULL)
                || other.getMaxDelta() == null || other.getMaxDelta().equals(TimeDelta.NULL)) {
            return false;
        }

        long rangeStart = getStartTime().getDayOfWeek() * SECONDS_IN_DAY + getStartTime().getPackedTime();

        long rangeEnd = rangeStart + (long) getMaxDelta().inSeconds();

        long otherStart = other.getStartTime().getDayOfWeek() * SECONDS_IN_DAY + other.getStartTime().getPackedTime();

        long otherEnd = otherStart + (long) other.getMaxDelta().inSeconds();

        //If we already encapsulate the other world's time, return true.
        if (rangeStart <= otherStart && rangeEnd >= otherEnd) {
            return true;
        }

        //If we overflow into the next week and the other's range is completely in that overflow, return true.
        return otherEnd < SECONDS_IN_WEEK && rangeEnd >= SECONDS_IN_WEEK && otherEnd <= rangeEnd - SECONDS_IN_WEEK;

    }

    public LocationPoint getCenter() {
        return center;
    }

    public Distance getRange() {
        return range;
    }

    public TimePoint getStartTime() {
        return startTime;
    }

    public TimeDelta getMaxDelta() {
        return maxDelta;
    }

    @Override
    public int hashCode() {
        int result = getCenter() != null ? getCenter().hashCode() : 0;
        result = 31 * result + (getRange() != null ? getRange().hashCode() : 0);
        result = 31 * result + (getStartTime() != null ? getStartTime().hashCode() : 0);
        result = 31 * result + (getMaxDelta() != null ? getMaxDelta().hashCode() : 0);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        WorldInfo worldInfo = (WorldInfo) o;

        if (getCenter() != null ? !getCenter().equals(worldInfo.getCenter()) : worldInfo.getCenter() != null)
            return false;
        if (getRange() != null ? !getRange().equals(worldInfo.getRange()) : worldInfo.getRange() != null) return false;
        if (getStartTime() != null ? !getStartTime().equals(worldInfo.getStartTime()) : worldInfo.getStartTime() != null)
            return false;
        return getMaxDelta() != null ? getMaxDelta().equals(worldInfo.getMaxDelta()) : worldInfo.getMaxDelta() == null;
    }

    public String packInfo() {
        return new JSONObject()
                .put("centerlat", center.getCoordinates()[0])
                .put("centerlng", center.getCoordinates()[1])
                .put("range", range.inMeters())
                .put("time", startTime.getUnixTime())
                .put("tz", startTime.getTimeZone())
                .put("delta", maxDelta.getDeltaLong())
                .toString();
    }

    public static class Builder {
        private LocationPoint center = null;
        private Distance range = null;
        private TimePoint startTime = null;
        private TimeDelta maxDelta = null;

        public Builder setCenter(LocationPoint center) {
            this.center = center;
            return this;
        }

        public Builder setRange(Distance range) {
            this.range = range;
            return this;
        }

        public Builder setStartTime(TimePoint startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder setMaxDelta(TimeDelta delta) {
            this.maxDelta = delta;
            return this;
        }

        public WorldInfo build() {
            if (center == null) {
                throw new IllegalStateException("Center cannot be null.");
            }
            if (maxDelta == null) {
                maxDelta = TimeDelta.NULL;
            }
            if (startTime == null) {
                startTime = TimePoint.NULL;
            }

            if (range == null && !maxDelta.equals(TimeDelta.NULL)) {
                range = LocationUtils.timeToMaxTransit(maxDelta);
            }
            return new WorldInfo(center, range, startTime, maxDelta);
        }
    }
}
