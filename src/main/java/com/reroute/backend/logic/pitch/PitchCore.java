package com.reroute.backend.logic.pitch;

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
    public static final Predicate<TravelRoute> isntDumbRoute = rt -> {
        if (rt.getWalkDisp().inMeters() >= 5 + LocationUtils.distanceBetween(rt.getStart(), rt.getCurrentEnd())
                .inMeters()) {
            return false;
        }

        return rt.getTime().getDeltaLong() < 100 + LocationUtils.timeBetween(rt.getStart(), rt.getCurrentEnd())
                .getDeltaLong();
    };
    private static final int DEFAULT_LIMIT = PitchSorter.values().length * 20;

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
                .withLayerLimit(200);
        Set<TravelRoute> stationRoutes = LogicUtils.buildStationRouteList(statreq);

        int numRoutesPer = (request.getLimit() > 0 ? request.getLimit() : DEFAULT_LIMIT) / PitchSorter.values().length;
        Map<String, List<TravelRoute>> bestStationRoutes = new ConcurrentHashMap<>();
        for (PitchSorter compObj : PitchSorter.values()) {
            String tag = compObj.getTag();
            Comparator<TravelRoute> comp = compObj.getComparor();
            List<TravelRoute> sortRoutes = new ArrayList<>(stationRoutes);
            sortRoutes.sort(comp);
            bestStationRoutes.put(tag, sortRoutes.subList(0, numRoutesPer));
        }

        Collection<TravelRoute> destRoutes = bestStationRoutes.values().stream()
                .flatMap(Collection::stream)
                .flatMap(rt -> getDestRoutes(rt, request.getMaxDelta(), request.getQuery()))
                .collect(LogicUtils.OPTIMAL_ROUTES_FOR_DESTINATIONS)
                .values();

        Map<String, List<TravelRoute>> bestDestRoutes = new HashMap<>();
        for (PitchSorter compObj : PitchSorter.values()) {
            String tag = compObj.getTag();
            Comparator<TravelRoute> comp = compObj.getComparor();
            List<TravelRoute> sortRoutes = new ArrayList<>(destRoutes);
            sortRoutes.sort(comp);
            bestDestRoutes.put(tag, sortRoutes.subList(0, numRoutesPer));
        }

        List<TravelRoute> rval = Arrays.stream(PitchSorter.values())
                .map(PitchSorter::getTag)
                .peek(a -> LoggingUtils.logMessage("PITCH", "Adding core %s.", a))
                .map(bestDestRoutes::get)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        return ApplicationResult.ret(rval);
    }

    public static Stream<TravelRoute> getDestRoutes(TravelRoute base, TimeDelta rawDelta, LocationType type) {
        LocationPoint center = base.getCurrentEnd();
        Distance range = LocationUtils.timeToWalkDistance(rawDelta.minus(base.getTime()));
        List<DestinationLocation> dests = LocationRetriever.getLocations(center, range, type, null);


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
