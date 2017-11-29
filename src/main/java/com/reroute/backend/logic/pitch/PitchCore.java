package com.reroute.backend.logic.pitch;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import com.google.common.collect.Sets;
import com.reroute.backend.locations.LocationRetriever;
import com.reroute.backend.logic.ApplicationRequest;
import com.reroute.backend.logic.ApplicationResult;
import com.reroute.backend.logic.interfaces.LogicCore;
import com.reroute.backend.logic.utils.LogicUtils;
import com.reroute.backend.logic.utils.StationRoutesBuildRequest;
import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.LocationType;
import com.reroute.backend.model.routing.TravelRoute;
import com.reroute.backend.model.routing.TravelRouteNode;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.utils.LocationUtils;
import com.reroute.backend.utils.LoggingUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class PitchCore implements LogicCore {

    public static final String TAG = "DEMO";
    public static final Predicate<TravelRoute> isntDumbRoute = rt ->
            !(rt.getWalkDisp().inMeters() >= 5 + LocationUtils.distanceBetween(rt.getStart(), rt.getCurrentEnd())
                    .inMeters())
                    && rt.getTime().getDeltaLong() < 100 + LocationUtils.timeBetween(rt.getStart(), rt.getCurrentEnd())
                    .getDeltaLong();
    private static final int DEFAULT_LIMIT = PitchSorter.values().length * 15;
    private static LoadingCache<LocationType, Cache<LocationPoint, List<DestinationLocation>>> destCache = CacheBuilder.newBuilder()
            .maximumWeight(10000)
            .weigher((Weigher<LocationType, Cache<?, ?>>) (key, value) -> value.asMap().size())
            .build(new CacheLoader<LocationType, Cache<LocationPoint, List<DestinationLocation>>>() {
                @Override
                public Cache<LocationPoint, List<DestinationLocation>> load(LocationType key) {
                    return CacheBuilder.newBuilder()
                            .maximumWeight(1000)
                            .weigher((Weigher<LocationPoint, List<DestinationLocation>>) (key1, value1) -> value1.size())
                            .build();
                }
            });

    @Override
    public Set<String> getTags() {
        return Sets.newHashSet(TAG);
    }

    @Override
    public boolean isValid(ApplicationRequest request) {
        if (!request.getTag().equals(TAG)) return false;
        if (request.getLimit() % PitchSorter.values().length != 0) return false;
        return request.getStarts() != null && request.getStarts().size() == 1;
    }

    @Override
    public ApplicationResult performLogic(ApplicationRequest request) {
        int numRoutesPer = (request.getLimit() > 0 ? request.getLimit() : DEFAULT_LIMIT) / PitchSorter.values().length;

        StationRoutesBuildRequest statreq = new StationRoutesBuildRequest(
                request.getStarts().get(0),
                request.getStartTime(),
                request.getMaxDelta()
        )
                .withLayerFilter(rt -> {
                    int totalWalks = 1 + (int) rt.getRoute().stream()
                            .filter(TravelRouteNode::arrivesByFoot)
                            .count();
                    double maxPercent = IntStream.range(1, totalWalks)
                            .mapToDouble(a -> 1. / Math.pow(2, a))
                            .sum();
                    return rt.getWalkTime().getDeltaLong() <= maxPercent * request.getMaxDelta().getDeltaLong();
                })
                .andLayerFilter(isntDumbRoute)
                .withLayerLimit((int) (numRoutesPer * PitchSorter.values().length * 1.2));
        LoggingUtils.logMessage("DEMO", "Req: %s", statreq);
        Set<TravelRoute> stationRoutes = LogicUtils.buildStationRouteList(statreq);

        Map<String, List<TravelRoute>> bestStationRoutes = new ConcurrentHashMap<>();
        if (stationRoutes.size() >= numRoutesPer * PitchSorter.values().length) {
            for (PitchSorter compObj : PitchSorter.values()) {
                String tag = compObj.getTag();
                Comparator<TravelRoute> comp = compObj.getComparor();
                List<TravelRoute> sortRoutes = new ArrayList<>(stationRoutes);
                sortRoutes.sort(comp);
                bestStationRoutes.put(tag, sortRoutes.subList(0, numRoutesPer));
            }
        } else {
            for (PitchSorter compObj : PitchSorter.values()) {
                String tag = compObj.getTag();
                Comparator<TravelRoute> comp = compObj.getComparor();
                List<TravelRoute> sortRoutes = new ArrayList<>(stationRoutes);
                sortRoutes.sort(comp);
                bestStationRoutes.put(tag, sortRoutes);
            }
        }

        Collection<TravelRoute> destRoutes = bestStationRoutes.values().stream()
                .flatMap(Collection::stream)
                .flatMap(rt -> getDestRoutes(rt, request.getMaxDelta(), request.getQuery()))
                .collect(LogicUtils.OPTIMAL_ROUTES_FOR_DESTINATIONS)
                .values();
        if (destRoutes.isEmpty()) {
            TravelRoute base = new TravelRoute(request.getStarts().get(0), request.getStartTime());
            destRoutes = getDestRoutes(base, request.getMaxDelta(), request.getQuery()).collect(Collectors.toList());
        }

        Map<String, List<TravelRoute>> bestDestRoutes = new HashMap<>();
        for (PitchSorter compObj : PitchSorter.values()) {
            String tag = compObj.getTag();
            Comparator<TravelRoute> comp = compObj.getComparor();
            List<TravelRoute> sortRoutes = new ArrayList<>(destRoutes);
            sortRoutes.sort(comp);
            while (sortRoutes.size() < numRoutesPer) {
                sortRoutes.add(new TravelRoute(sortRoutes.get(0).getStart(), sortRoutes.get(0).getStartTime()));
            }
            bestDestRoutes.put(tag, sortRoutes.subList(0, numRoutesPer));
        }

        List<TravelRoute> rval = Arrays.stream(PitchSorter.values())
                .map(PitchSorter::getTag)
                .peek(a -> LoggingUtils.logMessage("PITCH", "Adding core %s.", a))
                .map(bestDestRoutes::get)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        List<TravelRoute> dafuq = rval.stream()
                .filter(r -> r.getTime().getDeltaLong() > request.getMaxDelta().getDeltaLong() || r.getDisp()
                        .inMeters() > LocationUtils.timeToMaxTransit(request.getMaxDelta()).inMeters())
                .collect(Collectors.toList());

        LoggingUtils.logMessage("PITCH", "Dafuq: %d", dafuq.size());

        return ApplicationResult.ret(rval);
    }

    private static Stream<TravelRoute> getDestRoutes(TravelRoute base, TimeDelta rawDelta, LocationType type) {
        LocationPoint center = base.getCurrentEnd();
        Distance range = LocationUtils.timeToWalkDistance(rawDelta.minus(base.getTime()));
        List<DestinationLocation> dests = destCache.getUnchecked(type).asMap().entrySet().stream()
                .filter(e -> LocationUtils.distanceBetween(e.getKey(), center).inMeters() < 5)
                .map(Map.Entry::getValue)
                .filter(ls -> ls.size() >= 50 || ls.stream()
                        .anyMatch(d -> LocationUtils.distanceBetween(d, center).inMeters() >= range.inMeters()))
                .findAny()
                .map(dests1 -> dests1.stream()
                        .filter(d -> LocationUtils.distanceBetween(d, center).inMeters() <= range.inMeters())
                        .collect(Collectors.toList())
                )
                .orElse(Collections.emptyList());

        if (dests.isEmpty()) {
            dests = LocationRetriever.getLocations(center, range, type);
            destCache.getUnchecked(type).put(center, dests);
        }

        Stream<TravelRoute> baseRoutes = dests.stream()
                .map(point -> new TravelRouteNode.Builder()
                        .setWalkTime(LocationUtils.timeBetween(center, point).getDeltaLong())
                        .setPoint(point)
                        .build()
                )
                .map(node -> base.copy().setDestinationNode(node))
                .filter(isntDumbRoute);
        Stream<TravelRoute> directRoutes = dests.stream()
                .filter(dest -> LocationUtils.timeBetween(base.getStart(), dest)
                        .getDeltaLong() <= rawDelta.getDeltaLong())
                .map(point -> new TravelRouteNode.Builder()
                        .setWalkTime(LocationUtils.timeBetween(base.getStart(), point).getDeltaLong())
                        .setPoint(point)
                        .build()
                )
                .map(node -> base.copyAt(0).setDestinationNode(node))
                .filter(isntDumbRoute);

        return Stream.concat(baseRoutes, directRoutes);
    }

}
