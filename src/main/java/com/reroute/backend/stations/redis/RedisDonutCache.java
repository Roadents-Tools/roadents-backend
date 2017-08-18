package com.reroute.backend.stations.redis;

import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.TransChain;
import com.reroute.backend.model.location.TransStation;
import com.reroute.backend.model.time.SchedulePoint;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.model.time.TimePoint;
import com.reroute.backend.stations.interfaces.StationCacheInstance;
import com.reroute.backend.utils.LocationUtils;
import com.reroute.backend.utils.StreamUtils;
import redis.clients.jedis.GeoCoordinate;
import redis.clients.jedis.GeoRadiusResponse;
import redis.clients.jedis.GeoUnit;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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

        List<String> stationIds = jedis.georadius(
                RedisUtils.LATLNG_INDEX,
                center.getCoordinates()[0],
                center.getCoordinates()[1],
                range.inMeters(),
                GeoUnit.M
        )
                .stream()
                .map(GeoRadiusResponse::getMemberByString)
                .collect(Collectors.toList());

        List<String> stationInfo = jedis.mget(stationIds.toArray(new String[0]));

        return IntStream.range(0, stationIds.size()).boxed().parallel()
                .map(i -> RedisUtils.unpackStation(stationIds.get(i), stationInfo.get(i)))
                .filter(st -> LocationUtils.distanceBetween(center, st).inMeters() <= range.inMeters())
                .collect(Collectors.toList());
    }

    @Override
    public Map<TransChain, List<SchedulePoint>> getChainsForStation(TransStation station) {
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
        String[] schedIds = chainidToScheduleId.values().stream()
                .flatMap(Collection::stream)
                .distinct()
                .toArray(String[]::new);
        List<String> allScheduleRaw = jedis.mget(schedIds);
        Map<String, SchedulePoint> idToSched = IntStream.range(0, schedIds.length).boxed().parallel()
                .collect(StreamUtils.collectWithMapping(
                        i -> schedIds[i],
                        i -> RedisUtils.unpackSchedule(schedIds[i], allScheduleRaw.get(i))
                ));


        //Get the chain info
        String[] chainIds = chainidToScheduleId.keySet().stream()
                .map(i -> RedisUtils.CHAIN_PREFIX_NAME + RedisUtils.KEY_SPLITER + station.getID()
                        .getDatabaseName() + RedisUtils.KEY_SPLITER + i)
                .toArray(String[]::new);

        List<String> allChainsRaw = jedis.mget(chainIds);
        Map<Long, TransChain> idToChain = IntStream.range(0, chainIds.length).boxed().parallel()
                .collect(StreamUtils.collectWithMapping(
                        i -> Long.parseLong(chainIds[i].split(RedisUtils.KEY_SPLITER)[2]),
                        i -> RedisUtils.unpackChain(chainIds[i], allChainsRaw.get(i))
                ));


        //Combine
        return chainidToScheduleId.entrySet().stream()
                .collect(StreamUtils.collectWithMapping(
                        entry -> idToChain.get(entry.getKey()),
                        entry -> entry.getValue().stream()
                                .map(idToSched::get)
                                .collect(Collectors.toList())
                ));
    }

    @Override
    public Map<TransStation, TimeDelta> getArrivableStations(TransChain chain, TimePoint startTime, TimeDelta maxDelta) {
        return null;
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
                        entry -> new GeoCoordinate(entry.getKey().getCoordinates()[0], entry.getKey()
                                .getCoordinates()[1])
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
    public boolean putWorld(Map<TransChain, Map<TransStation, List<SchedulePoint>>> world) {
        if (!isUp) return false;

        //TODO: Check and record existing world info

        Set<String[]> chainData = new HashSet<>();
        Set<String[]> stationData = new HashSet<>();
        Set<String[]> scheduleData = new HashSet<>();

        Map<String, GeoCoordinate> stLatlngIndexData = new ConcurrentHashMap<>();
        Map<String, Map<String, String>> schedStatIndexData = new ConcurrentHashMap<>();
        Map<String, Map<String, String>> schedChainIndexData = new ConcurrentHashMap<>();


        //Build the data to place
        world.forEach((cur, curData) -> {

            chainData.add(RedisUtils.packChain(cur)); //Raw chain

            curData.forEach((station, schedule) -> {

                String[] stIdVal = RedisUtils.packStation(station);
                stationData.add(stIdVal); //Raw station

                GeoCoordinate packedLatlng = new GeoCoordinate(station.getCoordinates()[0], station.getCoordinates()[1]);
                stLatlngIndexData.put(stIdVal[0], packedLatlng); //Station geolocaiton index

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
        });

        //Insert station and chains with a single mset
        List<String[]> msetData = new ArrayList<>();
        msetData.add(Stream.concat(
                chainData.stream().flatMap(Arrays::stream),
                Stream.concat(
                        stationData.stream().flatMap(Arrays::stream),
                        scheduleData.stream().flatMap(Arrays::stream)
                )
        ).toArray(String[]::new));

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
            msetData = m2;
        }

        msetData.forEach(jedis::mset);


        //Then the indices
        jedis.geoadd(RedisUtils.LATLNG_INDEX, stLatlngIndexData);
        schedStatIndexData.forEach(jedis::hmset);
        schedChainIndexData.forEach(jedis::hmset);
        return true;
    }

    @Override
    public void close() {
        jedis.close();
        isUp = false;
    }
}
