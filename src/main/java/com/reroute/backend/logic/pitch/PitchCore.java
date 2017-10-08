package com.reroute.backend.logic.pitch;

import com.google.common.collect.Sets;
import com.reroute.backend.locations.LocationRetriever;
import com.reroute.backend.logic.ApplicationRequest;
import com.reroute.backend.logic.ApplicationResult;
import com.reroute.backend.logic.interfaces.LogicCore;
import com.reroute.backend.logic.utils.LogicUtils;
import com.reroute.backend.logic.utils.StationRoutesBuildRequest;
import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.LocationType;
import com.reroute.backend.model.routing.TravelRoute;
import com.reroute.backend.model.routing.TravelRouteNode;
import com.reroute.backend.model.time.TimeDelta;
import com.reroute.backend.utils.LocationUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PitchCore implements LogicCore {

    private static final String TAG = "DEMO";

    @Override
    public ApplicationResult performLogic(ApplicationRequest request) {
        StationRoutesBuildRequest statreq = new StationRoutesBuildRequest(
                request.getStarts().get(0),
                request.getStartTime(),
                request.getMaxDelta()
        );
        Set<TravelRoute> stationRoutes = LogicUtils.buildStationRouteList(statreq);

        int numRoutesPer = request.getLimit() / PitchSorter.values().length;
        Map<String, List<TravelRoute>> bestStationRoutes = new ConcurrentHashMap<>();
        for (TravelRoute route : stationRoutes) {
            for (PitchSorter compObj : PitchSorter.values()) {
                String tag = compObj.getTag();
                Comparator<TravelRoute> comp = compObj.getComparor();
                List<TravelRoute> curBest = bestStationRoutes.computeIfAbsent(tag, unused -> new ArrayList<>());
                if (curBest.size() < numRoutesPer) {
                    curBest.add(route);
                    if (curBest.size() == numRoutesPer) curBest.sort(comp);
                    continue;
                }
                int toInsert = numRoutesPer;
                TravelRoute toCompare = curBest.get(toInsert - 1);
                while (comp.compare(route, toCompare) < 0 && toInsert > 0) {
                    toInsert--;
                    toCompare = curBest.get(toInsert - 1);
                }
                if (toInsert == numRoutesPer) continue;
                curBest.add(toInsert, route);
                curBest.remove(numRoutesPer);
            }
        }

        Collection<TravelRoute> destRoutes = bestStationRoutes.values().stream()
                .flatMap(Collection::stream)
                .flatMap(rt -> getDestRoutes(rt, request.getMaxDelta(), request.getQuery()))
                .collect(LogicUtils.OPTIMAL_ROUTES_FOR_DESTINATIONS)
                .values();

        Map<String, List<TravelRoute>> bestDestRoutes = new HashMap<>();
        for (TravelRoute route : destRoutes) {
            for (PitchSorter compObj : PitchSorter.values()) {
                String tag = compObj.getTag();
                Comparator<TravelRoute> comp = compObj.getComparor();
                List<TravelRoute> curBest = bestDestRoutes.computeIfAbsent(tag, unused -> new ArrayList<>());
                if (curBest.size() < numRoutesPer) {
                    curBest.add(route);
                    if (curBest.size() == numRoutesPer) curBest.sort(comp);
                    continue;
                }
                int toInsert = numRoutesPer;
                TravelRoute toCompare = curBest.get(toInsert - 1);
                while (comp.compare(route, toCompare) < 0 && toInsert > 0) {
                    toInsert--;
                    toCompare = curBest.get(toInsert - 1);
                }
                if (toInsert == numRoutesPer) continue;
                curBest.add(toInsert, route);
                curBest.remove(numRoutesPer);
            }
        }

        List<TravelRoute> rval = Arrays.stream(PitchSorter.values())
                .map(PitchSorter::getTag)
                .map(bestDestRoutes::get)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        return ApplicationResult.ret(rval);
    }

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

    public static Stream<TravelRoute> getDestRoutes(TravelRoute base, TimeDelta rawDelta, LocationType type) {
        LocationPoint center = base.getCurrentEnd();
        Distance range = LocationUtils.timeToWalkDistance(rawDelta.minus(base.getTime()));

        return LocationRetriever.getLocations(center, range, type, null)
                .stream()
                .map(point -> new TravelRouteNode.Builder()
                        .setWalkTime(LocationUtils.timeBetween(center, point).getDeltaLong())
                        .setPoint(point)
                        .build()
                )
                .map(node -> base.copy().setDestinationNode(node));
    }

}
