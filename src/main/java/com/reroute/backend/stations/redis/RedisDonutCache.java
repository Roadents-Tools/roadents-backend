package com.reroute.backend.stations.redis;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.LocationPoint;
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
import redis.clients.jedis.GeoCoordinate;
import redis.clients.jedis.GeoRadiusResponse;
import redis.clients.jedis.GeoUnit;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class RedisDonutCache implements StationCacheInstance.DonutCache {


    private static final int BULK_SIZE = 1024;

    private Jedis jedis;
    private boolean isUp;
    private Cache<Long, TransChain> existingChains = CacheBuilder.newBuilder()
            .maximumSize(30000)
            .concurrencyLevel(3)
            .build();
    private Cache<String, SchedulePoint> existingSchedules = CacheBuilder.newBuilder()
            .maximumSize(30000)
            .concurrencyLevel(3)
            .build();

    public RedisDonutCache(String url, int port) {
        jedis = new Jedis(url, port);
        isUp = true;
    }

    @Override
    public List<TransStation> getStationsInArea(LocationPoint center, Distance range) {
        if (!isUp) return Collections.emptyList();

        String[] stationIds = jedis.georadius(
                RedisUtils.LATLNG_INDEX,
                center.getCoordinates()[1],
                center.getCoordinates()[0],
                range.inMeters(),
                GeoUnit.M
        )
                .stream()
                .map(GeoRadiusResponse::getMemberByString)
                .toArray(String[]::new);
        if (stationIds.length == 0) return Collections.emptyList();

        List<String> stationInfo = stationIds.length > 1 ? jedis.mget(stationIds) : Lists.newArrayList(jedis.get(stationIds[0]));

        return IntStream.range(0, stationIds.length).boxed().parallel()
                .map(i -> RedisUtils.unpackStation(stationIds[i], stationInfo.get(i)))
                .filter(st -> LocationUtils.distanceBetween(center, st).inMeters() <= range.inMeters())
                .collect(Collectors.toList());
    }

    @Override
    public Map<TransChain, List<SchedulePoint>> getChainsForStation(TransStation station) {
        try {
            if (!isUp) return Collections.emptyMap();

            //TODO: Check if station is in cache

            //Get the IDs of the chain and schedule info to get
            String indexKey = RedisUtils.SCHEDULE_STATION_INDEX_PREFIX + RedisUtils.KEY_SPLITER +
                    station.getID().getDatabaseName() + RedisUtils.KEY_SPLITER +
                    station.getID().getId();
            Map<Long, List<String>> chainidToScheduleId = jedis.hgetAll(indexKey).entrySet().stream()
                    .collect(
                            ConcurrentHashMap::new,
                            (map, entry) -> map.computeIfAbsent(Long.parseLong(entry.getValue()), aLong -> new ArrayList<>())
                                    .add(entry.getKey()),
                            ConcurrentHashMap::putAll
                    );


            //Get the schedule info
            String[] schedQuery = chainidToScheduleId.values().stream()
                    .flatMap(Collection::stream)
                    .filter(a -> !existingSchedules.asMap().containsKey(a) || existingSchedules.getIfPresent(a) == null)
                    .toArray(String[]::new);
            if (schedQuery.length > 0) {
                List<String> rawScheds = jedis.mget(schedQuery);
                IntStream.range(0, schedQuery.length).boxed().parallel()
                        .forEach(i -> {
                            String raw = rawScheds.get(i);
                            if (raw == null) return;
                            String key = schedQuery[i];
                            SchedulePoint point = RedisUtils.unpackSchedule(key, raw);
                            existingSchedules.put(key, point);
                        });
            }

            //Get the chain info
            Long[] toQueryId = chainidToScheduleId.keySet().parallelStream()
                    .filter(a -> !existingChains.asMap().containsKey(a))
                    .toArray(Long[]::new);
            String[] toQuery = Arrays.stream(toQueryId).parallel()
                    .filter(a -> !existingChains.asMap().containsKey(a))
                    .map(i -> RedisUtils.CHAIN_PREFIX_NAME + RedisUtils.KEY_SPLITER + station.getID()
                            .getDatabaseName() + RedisUtils.KEY_SPLITER + i)
                    .toArray(String[]::new);

            if (toQuery.length > 0) {
                List<String> allChainsRaw = jedis.mget(toQuery);
                IntStream.range(0, toQuery.length).boxed().parallel().forEach(i -> {
                    String raw = allChainsRaw.get(i);
                    if (raw == null) {
                        return;
                    }
                    Long id = toQueryId[i];
                    TransChain value = RedisUtils.unpackChain(toQuery[i], raw);
                    existingChains.put(id, value);
                });
            }


            //Combine
            Map<TransChain, List<SchedulePoint>> rval = new ConcurrentHashMap<>(chainidToScheduleId.size());
            chainidToScheduleId.entrySet().parallelStream().forEach(e -> {
                Long key1 = e.getKey();
                List<String> value1 = e.getValue();
                TransChain key = existingChains.getIfPresent(key1);
                List<SchedulePoint> value = value1.parallelStream()
                        .map(existingSchedules::getIfPresent)
                        .collect(Collectors.toList());
                if (key != null && value != null && !value.contains(null)) {
                    rval.put(key, value);
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

        //Get the index info
        String indexKey = RedisUtils.SCHEDULE_CHAIN_INDEX_PREFIX + RedisUtils.KEY_SPLITER +
                chain.getID().getDatabaseName() + RedisUtils.KEY_SPLITER +
                chain.getID().getId();
        Map<Long, List<String>> stationidToScheduleId = jedis.hgetAll(indexKey).entrySet().stream()
                .collect(
                        ConcurrentHashMap::new,
                        (map, entry) -> map
                                .computeIfAbsent(Long.parseLong(entry.getValue()), aLong -> new ArrayList<>())
                                .add(entry.getKey()),
                        ConcurrentHashMap::putAll
                );

        //Get the schedule info
        String[] schedIds = stationidToScheduleId.values().stream()
                .flatMap(Collection::stream)
                .distinct()
                .toArray(String[]::new);
        List<String> allScheduleRaw = jedis.mget(schedIds);
        Map<String, SchedulePoint> idToSched = IntStream.range(0, schedIds.length).boxed().parallel()
                .filter(RedisUtils.indexNonNull(schedIds, allScheduleRaw))
                .collect(StreamUtils.collectWithMapping(
                        i -> schedIds[i],
                        i -> RedisUtils.unpackSchedule(schedIds[i], allScheduleRaw.get(i))
                ));

        //Get the station info
        String[] stationIds = stationidToScheduleId.keySet().stream()
                .map(i -> RedisUtils.STATION_PREFIX_NAME + RedisUtils.KEY_SPLITER + chain.getID()
                        .getDatabaseName() + RedisUtils.KEY_SPLITER + i)
                .toArray(String[]::new);

        List<String> allStationsRaw = jedis.mget(stationIds);
        Map<Long, TransStation> idToChain = IntStream.range(0, stationIds.length).boxed().parallel()
                .filter(RedisUtils.indexNonNull(stationIds, allStationsRaw))
                .collect(StreamUtils.collectWithMapping(
                        i -> Long.parseLong(stationIds[i].split(RedisUtils.KEY_SPLITER)[2]),
                        i -> RedisUtils.unpackStation(stationIds[i], allStationsRaw.get(i))
                ));


        //Combine
        return stationidToScheduleId.entrySet().stream()
                .collect(StreamUtils.collectWithMapping(
                        entry -> idToChain.get(entry.getKey()),
                        entry -> entry.getValue().stream()
                                .map(idToSched::get)
                                .map(sched -> startTime.timeUntil(sched.nextValidTime(startTime)))
                                .filter(dt -> dt.getDeltaLong() <= maxDelta.getDeltaLong())
                                .min(Comparator.comparing(TimeDelta::getDeltaLong))
                                .orElse(null)
                ));
    }

    @Override
    public boolean putArea(LocationPoint center, Distance range, List<TransStation> stations) {
        if (!isUp) return false;

        //TODO: Check and record areas already stored

        Map<TransStation, String[]> stationToPacked = stations.stream()
                .collect(StreamUtils.collectWithValues(RedisUtils::packStation));

        String[] msetInput = stationToPacked.values().stream()
                .flatMap(Arrays::stream)
                .toArray(String[]::new);
        jedis.mset(msetInput);

        Map<String, GeoCoordinate> geoInput = stationToPacked.entrySet().stream()
                .collect(StreamUtils.collectWithMapping(
                        entry -> entry.getValue()[0],
                        entry -> new GeoCoordinate(entry.getKey().getCoordinates()[1], entry.getKey()
                                .getCoordinates()[0])
                ));
        jedis.geoadd(RedisUtils.LATLNG_INDEX, geoInput);

        return true;
    }

    @Override
    public boolean putChainsForStations(TransStation station, Map<TransChain, List<SchedulePoint>> chains) {
        if (!isUp) return false;

        //TODO: Check and record stations already stored

        //Put the raw info
        String[] msetInput = chains.values().stream()
                .flatMap(Collection::stream)
                .distinct()
                .map(RedisUtils::packSchedule)
                .flatMap(Arrays::stream)
                .toArray(String[]::new);
        jedis.mset(msetInput);


        //Put the station index value
        String stationIndexKey = RedisUtils.SCHEDULE_STATION_INDEX_PREFIX + RedisUtils.KEY_SPLITER +
                station.getID().getDatabaseName() + RedisUtils.KEY_SPLITER +
                station.getID().getId();

        Map<String, String> stidxVals = new ConcurrentHashMap<>();
        chains.forEach((chain, pts) -> pts.stream()
                .map(RedisUtils::packSchedule)
                .map(idVal -> idVal[0])
                .forEach(id -> stidxVals.put(id, chain.getID().getId()))
        );

        jedis.hmset(stationIndexKey, stidxVals);

        //Put the chain index value
        chains.forEach((chain, scheds) -> {
            String chainIndexKey = RedisUtils.SCHEDULE_CHAIN_INDEX_PREFIX + RedisUtils.KEY_SPLITER +
                    chain.getID().getDatabaseName() + RedisUtils.KEY_SPLITER +
                    chain.getID().getId();

            Map<String, String> chainidxVals = scheds.stream()
                    .map(RedisUtils::packSchedule)
                    .map(idVal -> idVal[0])
                    .collect(StreamUtils.collectWithValues(id -> station.getID().getId()));

            jedis.hmset(chainIndexKey, chainidxVals);
        });

        return true;
    }

    @Override
    public boolean putWorld(WorldInfo request, Map<TransChain, Map<TransStation, List<SchedulePoint>>> world) {
        if (!isUp) return false;
        if (hasWorld(request)) return true;

        Map<String, GeoCoordinate> stLatlngIndexData = new ConcurrentHashMap<>();
        Map<String, Map<String, String>> schedStatIndexData = new ConcurrentHashMap<>();
        Map<String, Map<String, String>> schedChainIndexData = new ConcurrentHashMap<>();
        List<String[]> msetData = new ArrayList<>();

        AtomicInteger i = new AtomicInteger(0);

        //Build the data to place
        world.forEach((cur, curData) -> {
            List<String[]> chainData = new ArrayList<>();
            List<String[]> stationData = new ArrayList<>();
            List<String[]> scheduleData = new ArrayList<>();

            chainData.add(RedisUtils.packChain(cur)); //Raw chain

            curData.forEach((station, schedule) -> {

                String[] stIdVal = RedisUtils.packStation(station);
                stationData.add(stIdVal); //Raw station

                GeoCoordinate packedLatlng = new GeoCoordinate(station.getCoordinates()[1], station.getCoordinates()[0]);
                stLatlngIndexData.put(stIdVal[0], packedLatlng); //Station geolocation index

                for (SchedulePoint schedulePoint : schedule) {

                    String[] scIdVal = RedisUtils.packSchedule(schedulePoint);
                    scheduleData.add(scIdVal); //Raw schedule

                    String stationIndexKey = RedisUtils.SCHEDULE_STATION_INDEX_PREFIX + RedisUtils.KEY_SPLITER +
                            station.getID().getDatabaseName() + RedisUtils.KEY_SPLITER +
                            station.getID().getId();
                    schedStatIndexData.computeIfAbsent(stationIndexKey, (key) -> new ConcurrentHashMap<>())
                            .put(scIdVal[0], cur.getID().getId()); //Schedule station primary index

                    String chainIndexKey = RedisUtils.SCHEDULE_CHAIN_INDEX_PREFIX + RedisUtils.KEY_SPLITER +
                            cur.getID().getDatabaseName() + RedisUtils.KEY_SPLITER +
                            cur.getID().getId();
                    schedChainIndexData.computeIfAbsent(chainIndexKey, (key) -> new ConcurrentHashMap<>())
                            .put(scIdVal[0], station.getID().getId()); //Schedule chain primary index
                }
            });

            msetData.add(Stream.concat(
                    chainData.stream().flatMap(Arrays::stream),
                    Stream.concat(
                            stationData.stream().flatMap(Arrays::stream),
                            scheduleData.stream().flatMap(Arrays::stream)
                    )
            ).toArray(String[]::new));

            if (msetData.stream().mapToInt(a -> a.length).sum() >= BULK_SIZE) {
                while (msetData.stream().anyMatch(ar -> ar.length > BULK_SIZE * 2)) {
                    List<String[]> m2 = new ArrayList<>();
                    for (String[] ar : msetData) {
                        if (ar.length > BULK_SIZE * 2) {
                            m2.add(Arrays.copyOfRange(ar, 0, BULK_SIZE * 2));
                            m2.add(Arrays.copyOfRange(ar, BULK_SIZE * 2, ar.length));
                        } else {
                            m2.add(ar);
                        }
                    }
                    msetData.clear();
                    msetData.addAll(m2);
                }
                msetData.forEach(jedis::mset);
                msetData.clear();
                stationData.clear();
                chainData.clear();
                scheduleData.clear();
                jedis.geoadd(RedisUtils.LATLNG_INDEX, stLatlngIndexData);
                stLatlngIndexData.clear();
                schedChainIndexData.forEach(jedis::hmset);
                schedChainIndexData.clear();
                schedStatIndexData.forEach(jedis::hmset);
                schedStatIndexData.clear();

            }
            i.incrementAndGet();
        });


        //Then the indices
        jedis.geoadd(RedisUtils.LATLNG_INDEX, stLatlngIndexData);
        schedStatIndexData.forEach(jedis::hmset);
        schedChainIndexData.forEach(jedis::hmset);

        //Finally the world request itself
        jedis.lpush(RedisUtils.WORLD_LIST_NAME, request.packInfo());
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
