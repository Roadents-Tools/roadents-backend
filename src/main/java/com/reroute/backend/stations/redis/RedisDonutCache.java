package com.reroute.backend.stations.redis;

import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.TransChain;
import com.reroute.backend.model.location.TransStation;
import com.reroute.backend.model.time.SchedulePoint;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.stations.WorldInfo;
import com.reroute.backend.stations.interfaces.StationCacheInstance;
import com.reroute.backend.utils.LoggingUtils;
import com.reroute.backend.utils.StreamUtils;
import redis.clients.jedis.GeoCoordinate;
import redis.clients.jedis.GeoRadiusResponse;
import redis.clients.jedis.GeoUnit;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.geo.GeoRadiusParam;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RedisDonutCache implements StationCacheInstance.DonutCache {


    private static final int BULK_SIZE = 1024;

    private Jedis jedis;
    private boolean isUp;

    public RedisDonutCache(String url, int port) {
        jedis = new Jedis(url, port);
        isUp = true;
    }

    @Override
    public List<TransStation> getStationsInArea(LocationPoint center, Distance range) {
        if (!isUp) return Collections.emptyList();

        return jedis.georadius(
                RedisUtils.LATLNG_INDEX,
                center.getCoordinates()[1],
                center.getCoordinates()[0],
                range.inMeters(),
                GeoUnit.M,
                GeoRadiusParam.geoRadiusParam().count(250)
        )
                .parallelStream()
                .map(GeoRadiusResponse::getMemberByString)
                .map(RedisUtils::deserializeStation)
                .collect(Collectors.toList());
    }

    @Override
    public Map<TransChain, List<SchedulePoint>> getChainsForStation(TransStation station) {
        try {
            if (!isUp) return Collections.emptyMap();

            //TODO: Check if station is in cache

            String key = RedisUtils.SCHEDULE_STATION_INDEX_PREFIX + RedisUtils.KEY_SPLITER + station.getID()
                    .getDatabaseName() + station.getID().getId();
            Set<String> toParse = jedis.zrange(key, 0, 500);
            Map<TransChain, List<SchedulePoint>> rval = new ConcurrentHashMap<>();
            for (String p : toParse) {
                String[] items = p.split(RedisUtils.ITEM_SPLITER);
                TransChain chain = RedisUtils.deserializeChain(items[0]);
                SchedulePoint pt = RedisUtils.deserializeSchedule(items[1]);
                rval.computeIfAbsent(chain, c -> new ArrayList<>()).add(pt);
            }
            return rval;

        } catch (Exception e) {
            LoggingUtils.logError(e);
            return Collections.emptyMap();
        }
    }

    @Override
    public Map<TransChain, List<SchedulePoint>> getChainsForStation(TransStation station, TimePoint startTime, TimeDelta maxDelta) {
        try {
            if (!isUp) return Collections.emptyMap();

            //TODO: Check if station is in cache

            TimePoint endTime = startTime.plus(maxDelta);
            int minDay = startTime.getDayOfWeek();
            int maxDay = endTime.getDayOfWeek() > minDay ? endTime.getDayOfWeek() : endTime.getDayOfWeek() + 7;
            double min = startTime.getPackedTime();
            double max = endTime.getPackedTime();
            if (maxDelta.getDeltaLong() >= 86390000L) {
                min = 0;
                max = 86400;
            }

            String key = RedisUtils.SCHEDULE_STATION_INDEX_PREFIX + RedisUtils.KEY_SPLITER + station.getID()
                    .getDatabaseName() + station.getID().getId();
            Map<TransChain, List<SchedulePoint>> rval = new ConcurrentHashMap<>();

            Stream<String> toParse;
            if (max - min >= 8390) {
                toParse = jedis.zrange(key, 0, 500).stream();
            } else if (min < max) {
                toParse = jedis.zrangeByScore(key, min, max, 0, 500).stream();
            } else {
                toParse = Stream.concat(
                        jedis.zrangeByScore(key, 0, max, 0, 500).stream(),
                        jedis.zrangeByScore(key, min, 86400, 0, 500).stream()
                );
            }
            toParse.forEach(p -> {
                String[] items = p.split(RedisUtils.ITEM_SPLITER);
                SchedulePoint pt = RedisUtils.deserializeSchedule(items[1]);
                for (int i = minDay; i < maxDay; i++) {
                    if (!pt.getValidDays()[i % 7]) continue;
                    TransChain chain = RedisUtils.deserializeChain(items[0]);
                    rval.computeIfAbsent(chain, c -> new ArrayList<>()).add(pt);
                    return;
                }
            });
            return rval;

        } catch (Exception e) {
            LoggingUtils.logError(e);
            return Collections.emptyMap();
        }
    }

    @Override
    public Map<TransStation, TimeDelta> getArrivableStations(TransChain chain, TimePoint startTime, TimeDelta maxDelta) {

        String key = RedisUtils.SCHEDULE_CHAIN_INDEX_PREFIX + RedisUtils.KEY_SPLITER +
                chain.getID().getDatabaseName() + RedisUtils.KEY_SPLITER +
                chain.getID().getId();
        double min = startTime.getPackedTime() - 1;
        double max = startTime.plus(maxDelta).getPackedTime() + 1;
        Set<String> toParse;
        if (min < max) {
            toParse = jedis.zrangeByScore(key, min, max, 0, 250);
        } else {
            toParse = jedis.zrange(key, 0, 250);
        }
        Map<TransStation, TimeDelta> rval = new ConcurrentHashMap<>();
        for (String p : toParse) {
            String[] items = p.split(RedisUtils.ITEM_SPLITER);
            SchedulePoint pt = RedisUtils.deserializeSchedule(items[1]);
            TimeDelta diff = startTime.timeUntil(pt.nextValidTime(startTime));
            if (diff.getDeltaLong() >= maxDelta.getDeltaLong()) continue;
            TransStation stat = RedisUtils.deserializeStation(items[0]);
            rval.compute(stat, (transStation, timeDelta) -> (timeDelta == null || timeDelta.getDeltaLong() < diff.getDeltaLong()) ? diff : timeDelta);
        }
        return rval;
    }

    @Override
    public boolean putArea(LocationPoint center, Distance range, List<TransStation> stations) {

        //TODO: Record the area.
        return putAreaRaw(stations);
    }

    @Override
    public boolean putChainsForStations(TransStation station, Map<TransChain, List<SchedulePoint>> chains) {
        if (!isUp) return false;
        if (chains.isEmpty()) return true;
        Map<String, Double> data = chains.entrySet().stream()
                .map(
                        e -> e.getValue().stream().collect(StreamUtils.collectWithMapping(
                                sched -> RedisUtils.serializeChain(e.getKey()) +
                                        RedisUtils.ITEM_SPLITER +
                                        RedisUtils.serializeSchedule(sched),
                                sched -> (double) sched.getPackedTime()))
                )
                .reduce((stringDoubleMap, stringDoubleMap2) -> {
                    stringDoubleMap.putAll(stringDoubleMap2);
                    return stringDoubleMap;
                })
                .orElse(Collections.emptyMap());
        if (data.isEmpty()) return false;
        String key = RedisUtils.SCHEDULE_STATION_INDEX_PREFIX + RedisUtils.KEY_SPLITER + station.getID()
                .getDatabaseName() + station.getID().getId();
        jedis.zadd(key, data);
        return true;
    }

    @Override
    public boolean putWorld(WorldInfo request, Map<TransChain, Map<TransStation, List<SchedulePoint>>> world) {
        if (!isUp) return false;
        if (hasWorld(request)) return true;

        List<TransStation> sts = world.values().stream()
                .flatMap(m -> m.keySet().stream())
                .collect(Collectors.toList());
        boolean areareq = putAreaRaw(sts);
        if (!areareq) return false;

        boolean stat4chains = world.entrySet().stream()
                .map(e -> putStationsForChain(e.getKey(), e.getValue()))
                .reduce((b1, b2) -> b1 & b2)
                .orElse(true);
        if (!stat4chains) return false;

        Map<TransStation, Map<TransChain, List<SchedulePoint>>> restructed = new ConcurrentHashMap<>();
        world.forEach(
                (chain, statSchedMap) -> statSchedMap.forEach(
                        (stat, sched) -> restructed.computeIfAbsent(stat, transStation -> new ConcurrentHashMap<>())
                                .put(chain, sched)
                )
        );
        boolean chains4stat = restructed.entrySet().stream()
                .map(e -> putChainsForStations(e.getKey(), e.getValue()))
                .reduce((b1, b2) -> b1 & b2)
                .orElse(true);
        return chains4stat && jedis.lpush(RedisUtils.WORLD_LIST_NAME, request.packInfo()) > 0;
    }

    private boolean putAreaRaw(Collection<? extends TransStation> stations) {
        Map<String, GeoCoordinate> data = stations.parallelStream()
                .collect(StreamUtils.collectWithMapping(
                        RedisUtils::serializeStation,
                        s -> new GeoCoordinate(s.getCoordinates()[1], s.getCoordinates()[0])
                ));
        jedis.geoadd(RedisUtils.LATLNG_INDEX, data);
        return true;
    }

    public boolean putStationsForChain(TransChain chain, Map<TransStation, List<SchedulePoint>> stations) {
        if (!isUp) return false;
        if (stations.isEmpty()) return true;
        Map<String, Double> data = stations.entrySet().stream()
                .map(
                        e -> e.getValue().stream().collect(StreamUtils.collectWithMapping(
                                sched -> RedisUtils.serializeStation(e.getKey()) +
                                        RedisUtils.ITEM_SPLITER +
                                        RedisUtils.serializeSchedule(sched),
                                sched -> (double) sched.getPackedTime()
                        ))
                )
                .reduce((stringDoubleMap, stringDoubleMap2) -> {
                    stringDoubleMap.putAll(stringDoubleMap2);
                    return stringDoubleMap;
                })
                .orElse(Collections.emptyMap());
        if (data.isEmpty()) return false;
        String key = RedisUtils.SCHEDULE_CHAIN_INDEX_PREFIX + RedisUtils.KEY_SPLITER +
                chain.getID().getDatabaseName() + RedisUtils.KEY_SPLITER +
                chain.getID().getId();
        jedis.zadd(key, data);
        return true;
    }

    @Override
    public boolean hasWorld(WorldInfo toCheck) {
        return jedis.lrange(RedisUtils.WORLD_LIST_NAME, 0, -1).stream()
                .map(WorldInfo::unpackInfo)
                .anyMatch(cached -> cached.containsInfo(toCheck));
    }

    @Override
    public void close() {
        jedis.close();
        isUp = false;
    }
}
