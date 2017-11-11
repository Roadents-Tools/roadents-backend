package com.reroute.backend.jsonconvertion.routing;

import com.reroute.backend.jsonconvertion.JsonConverter;
import com.reroute.backend.jsonconvertion.location.DestinationJsonConverter;
import com.reroute.backend.jsonconvertion.location.StartPointJsonConverter;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.StartPoint;
import com.reroute.backend.model.location.TransChain;
import com.reroute.backend.model.routing.TravelRoute;
import com.reroute.backend.model.routing.TravelRouteNode;
import com.reroute.backend.model.time.TimePoint;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ilan on 7/16/16.
 */
public class TravelRouteJsonConverter implements JsonConverter<TravelRoute> {

    private static final String START_TAG = "start";
    private static final String START_TIME_TAG = "starttime";
    private static final String END_TAG = "dest";
    private static final String ROUTE_TAG = "route";

    private final StartPointJsonConverter startConverter = new StartPointJsonConverter();
    private final DestinationJsonConverter destConverter = new DestinationJsonConverter();
    private final TravelRouteNodeJsonConverter nodeConverter = new TravelRouteNodeJsonConverter();

    @Override
    public String toJson(TravelRoute input) {
        JSONObject obj = new JSONObject();
        obj.put(START_TIME_TAG, input.getStartTime().getUnixTime()); //Store seconds from unix epoch
        obj.put(START_TAG, new JSONObject(startConverter.toJson(input.getStart())));
        obj.put(ROUTE_TAG, convertRoute(input));
        if (input.getDestination() != null)
            obj.put(END_TAG, new JSONObject(destConverter.toJson(input.getDestination())));
        return obj.toString();
    }

    private JSONArray convertRoute(TravelRoute input) {

        return input.getRoute().stream()
                .map(nodeConverter::toJson)
                .map(JSONObject::new)
                .collect(JSONArray::new, JSONArray::put, (jsonArray, jsonArray2) -> {
                    for (int i = 0, len = jsonArray2.length(); i < len; i++) {
                        jsonArray.put(jsonArray2.getJSONObject(i));
                    }
                });

    }

    @Override
    public TravelRoute fromJson(String json) {

        JSONObject jsonObject = new JSONObject(json);

        TimePoint startTime = TimePoint.from(jsonObject.getLong(START_TIME_TAG), "America/Los_Angeles");

        Map<String, TransChain> storedChains = new ConcurrentHashMap<>();

        List<TravelRouteNode> nodes = new ArrayList<>();
        TravelRouteNode startNode = null;
        TravelRouteNode endNode = null;

        TravelRouteNodeJsonConverter conv = new TravelRouteNodeJsonConverter(storedChains);

        JSONArray routeList = jsonObject.getJSONArray(ROUTE_TAG);
        for (int i = 0; i < routeList.length(); i++) {
            JSONObject nodeObj = routeList.getJSONObject(i);
            TravelRouteNode node = conv.fromJson(nodeObj.toString());
            if (node.getPt() instanceof StartPoint) startNode = node;
            else if (node.getPt() instanceof DestinationLocation) endNode = node;
            else nodes.add(node);
        }

        TravelRoute route = new TravelRoute((StartPoint) startNode.getPt(), startTime);
        nodes.forEach(route::addNode);
        route.setDestinationNode(endNode);

        return route;
    }
}
