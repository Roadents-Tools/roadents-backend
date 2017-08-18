package com.reroute.backend.stations.memory;

import com.conversantmedia.util.collection.spatial.HyperPoint;
import com.conversantmedia.util.collection.spatial.HyperRect;
import com.conversantmedia.util.collection.spatial.RectBuilder;
import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.distance.DistanceUnits;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.LocationType;
import com.reroute.backend.model.location.TransChain;
import com.reroute.backend.model.location.TransStation;
import com.reroute.backend.model.time.SchedulePoint;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.stations.WorldInfo;
import com.reroute.backend.stations.interfaces.StationCacheInstance;
import com.reroute.backend.utils.LocationUtils;
import com.reroute.backend.utils.LoggingUtils;
import com.reroute.backend.utils.StreamUtils;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DonutPutOnceCache implements StationCacheInstance.DonutCache {

    private Map<TransChain, Map<TransStation, List<SchedulePoint>>> worldInfo = new HashMap<>();
    private Map<Point, TransStation> areaMap = new HashMap<>();
    private List<WorldInfo> requests = new LinkedList<>();

    private static Distance latLengthAt(double lat) {
        double latmidrads = Math.toRadians(lat);
        return new Distance(
                111132.92 -
                        559.82 * Math.cos(2 * latmidrads) +
                        1.175 * Math.cos(4 * latmidrads - .0023 * Math.cos(6 * latmidrads)),
                DistanceUnits.METERS
        );
    }

    private static Distance lngLengthAt(double lat) {
        double latmidrads = Math.toRadians(lat);
        return new Distance(
                111412.85 * Math.cos(latmidrads) -
                        93.5 * Math.cos(3 * latmidrads) +
                        .118 * Math.cos(5 * latmidrads),
                DistanceUnits.METERS
        );
    }

    @Override
    public void close() {
        worldInfo.clear();
        areaMap.clear();
    }

    @Override
    public List<TransStation> getStationsInArea(LocationPoint center, Distance range) {
        //TODO Use the rtree.
        return getStationsInAreaBad(center, range);
    }

    private List<TransStation> getStationsInAreaBad(LocationPoint center, Distance range) {
        if (areaMap == null || areaMap.isEmpty()) return Collections.emptyList();
        return areaMap.values().stream()
                .filter(stat -> LocationUtils.distanceBetween(center, stat).inMeters() <= range.inMeters())
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public Map<TransChain, List<SchedulePoint>> getChainsForStation(TransStation station) {
        if (worldInfo == null || worldInfo.isEmpty()) return Collections.emptyMap();
        return worldInfo.entrySet().stream()
                .filter(entry -> entry.getValue().containsKey(station))
                .collect(StreamUtils.collectWithMapping(
                        Map.Entry::getKey,
                        entry -> entry.getValue().get(station)
                ));
    }

    @Override
    public Map<TransStation, TimeDelta> getArrivableStations(TransChain chain, TimePoint startTime, TimeDelta maxDelta) {
        if (worldInfo == null || worldInfo.isEmpty()) return Collections.emptyMap();
        return worldInfo.get(chain).entrySet().stream()
                .collect(StreamUtils.collectWithMapping(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .map(pt -> startTime.timeUntil(pt.nextValidTime(startTime)))
                                .filter(dt -> dt.getDeltaLong() <= maxDelta.getDeltaLong())
                                .min(Comparator.comparing(TimeDelta::getDeltaLong))
                                .orElse(null)
                ));
    }

    @Override
    public boolean putArea(LocationPoint center, Distance range, List<TransStation> stations) {
        return false;
    }

    @Override
    public boolean putChainsForStations(TransStation station, Map<TransChain, List<SchedulePoint>> chains) {
        return false;
    }

    @Override
    public boolean putWorld(WorldInfo info, Map<TransChain, Map<TransStation, List<SchedulePoint>>> world) {
        if (!worldInfo.isEmpty() || !areaMap.isEmpty()) return false;
        if (hasWorld(info)) return true;
        LoggingUtils.logMessage("DonutPutCache", "Putting world with %d chains.", world.size());
        worldInfo = world;
        for (Map<TransStation, List<SchedulePoint>> stmap : world.values()) {
            for (TransStation station : stmap.keySet()) {
                Point pt = new Point(station.getCoordinates()[0], station.getCoordinates()[1]);
                areaMap.put(pt, station);
            }
        }
        requests.add(info);
        return true;
    }

    @Override
    public boolean hasWorld(WorldInfo toCheck) {
        return requests.stream()
                .anyMatch(cached -> cached.containsInfo(toCheck));
    }

    private static class Rect2D implements HyperRect {
        final Point min, max;

        Rect2D(final Point p) {
            min = new Point(p.lat, p.lng);
            max = new Point(p.lat, p.lng);
        }

        Rect2D(final double x1, final double y1, final double x2, final double y2) {
            this(new Point(x1, y1), new Point(x2, y2));
        }

        Rect2D(final Point p1, final Point p2) {
            final double minX, minY, maxX, maxY;
            if (p1.lat < p2.lat) {
                minX = p1.lat;
                maxX = p2.lat;
            } else {
                minX = p2.lat;
                maxX = p2.lat;
            }
            if (p1.lng < p2.lng) {
                minY = p1.lng;
                maxY = p2.lng;
            } else {
                minY = p2.lng;
                maxY = p2.lng;
            }
            min = new Point(minX, minY);
            max = new Point(maxX, maxY);
        }

        @Override
        public HyperRect getMbr(final HyperRect r) {
            final Rect2D r2 = (Rect2D) r;
            final double minX = Math.min(min.lat, r2.min.lat);
            final double minY = Math.min(min.lng, r2.min.lng);
            final double maxX = Math.max(max.lat, r2.max.lat);
            final double maxY = Math.max(max.lng, r2.max.lng);
            return new Rect2D(minX, minY, maxX, maxY);
        }

        @Override
        public int getNDim() {
            return 2;
        }

        @Override
        public HyperPoint getMin() {
            return min;
        }

        @Override
        public HyperPoint getMax() {
            return max;
        }

        @Override
        public HyperPoint getCentroid() {
            final double dx = min.lat + (max.lat - min.lat) / 2.0;
            final double dy = min.lng + (max.lng - min.lng) / 2.0;
            return new Point(dx, dy);
        }

        @Override
        public double getRange(final int d) {
            if (d == 0) {
                return max.lat - min.lat;
            } else if (d == 1) {
                return max.lng - min.lng;
            } else {
                throw new RuntimeException(("Invalid dimension"));
            }
        }

        @Override
        public boolean contains(final HyperRect r) {
            final Rect2D r2 = (Rect2D) r;
            return min.lat <= r2.min.lat &&
                    max.lat >= r2.max.lat &&
                    min.lng <= r2.min.lng &&
                    max.lng >= r2.max.lng;
        }

        @Override
        public boolean intersects(final HyperRect r) {
            final Rect2D r2 = (Rect2D) r;
            return !(min.lat > r2.max.lat) &&
                    !(r2.min.lat > max.lat) &&
                    !(min.lng > r2.max.lng) &&
                    !(r2.min.lng > max.lng);
        }

        @Override
        public double cost() {
            final double dx = max.lat - min.lat;
            final double dy = max.lng - min.lng;
            return Math.abs(dx) * Math.abs(dy);
        }

        @Override
        public double perimeter() {
            final double dx = max.lat - min.lat;
            final double dy = max.lng - min.lng;
            return 2 * dx + 2 * dy;
        }

        public static final class Builder implements RectBuilder<Rect2D> {
            @Override
            public HyperRect getBBox(final Rect2D rect2D) {
                return rect2D;
            }

            @Override
            public HyperRect getMbr(final HyperPoint p1, final HyperPoint p2) {
                return new Rect2D(p1.getCoord(0), p1.getCoord(1), p2.getCoord(0), p2.getCoord(1));
            }
        }
    }

    private static class Point implements HyperPoint, LocationPoint {
        final double lat, lng;

        Point(final double x, final double y) {
            this.lat = x;
            this.lng = y;
        }

        @Override
        public int hashCode() {
            int result;
            long temp;
            temp = Double.doubleToLongBits(lat);
            result = (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(lng);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Point point = (Point) o;

            return Double.compare(point.lat, lat) == 0 && Double.compare(point.lng, lng) == 0;
        }

        @Override
        public int getNDim() {
            return 2;
        }

        @Override
        public Double getCoord(final int d) {
            if (d == 0) {
                return lat;
            } else if (d == 1) {
                return lng;
            } else {
                throw new RuntimeException("Invalid dimension");
            }
        }

        @Override
        public double distance(final HyperPoint p) {
            final Point p2 = (Point) p;
            return LocationUtils.distanceBetween(this, p2).inMeters();
        }

        public double[] getCoords() {
            return new double[] { lat, lng };
        }

        @Override
        public double distance(final HyperPoint p, final int d) {
            final Point p2 = (Point) p;
            if (d == 0) {
                double latmid = (lat + p2.lat) / 2;
                double latdif = Math.abs(lat - p2.lat);

                //See wikipedia for formula source.
                return latdif * latLengthAt(latmid).inMeters();
            } else if (d == 1) {
                double latmid = (lat + p2.lat) / 2;
                double lngdif = Math.abs(lng - p2.lng);

                //See wikipedia for formula source.
                return lngdif * lngLengthAt(latmid).inMeters();
            } else {
                throw new RuntimeException("Invalid dimension");
            }
        }

        @Override
        public String getName() {
            return "Point " + lat + ", " + lng;
        }

        @Override
        public LocationType getType() {
            return new LocationType("DonutPutOnceCache::HyperPoint", "donutputconcecache::hyperpoint");
        }

        @Override
        public double[] getCoordinates() {
            return new double[] { lat, lng };
        }

        public final static class Builder implements RectBuilder<Point> {
            @Override
            public HyperRect getBBox(final Point point) {
                return new Rect2D(point);
            }

            @Override
            public HyperRect getMbr(final HyperPoint p1, final HyperPoint p2) {
                final Point point1 = (Point) p1;
                final Point point2 = (Point) p2;
                return new Rect2D(point1, point2);
            }
        }
    }
}
