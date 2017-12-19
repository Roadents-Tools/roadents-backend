package com.reroute.backend.jsonconvertion.location;

import com.reroute.backend.jsonconvertion.JsonConverter;
import com.reroute.backend.jsonconvertion.time.SchedulePointJsonConverter;
import com.reroute.backend.model.database.DatabaseID;
import com.reroute.backend.model.location.TransChain;
import com.reroute.backend.model.location.TransStation;
import com.reroute.backend.model.time.SchedulePoint;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ilan on 6/3/17.
 */
public class TransStationJsonConverter implements JsonConverter<TransStation> {

    private static final String NAME_TAG = "name";
    private static final String CHAIN_TAG = "chain";
    private static final String LAT_TAG = "latitude";
    private static final String LNG_TAG = "longitude";
    private static final String SCHEDULE_TAG = "schedule";
    private static final String ID_TAG = "id";

    @Override
    public JSONObject toJsonObject(TransStation input) {
        JSONObject obj = new JSONObject();
        obj.put(NAME_TAG, input.getName());
        obj.put(LAT_TAG, input.getCoordinates()[0]);
        obj.put(LNG_TAG, input.getCoordinates()[1]);
        if (input.getChain() != null) {
            obj.put(CHAIN_TAG, input.getChain().getName());
        }
        if (input.getSchedule() != null && !input.getSchedule().isEmpty()) {
            JSONArray arr = new JSONArray(new SchedulePointJsonConverter().toJson(input.getSchedule()));
            obj.put(SCHEDULE_TAG, arr);
        }
        if (input.getID() != null) {
            obj.put(ID_TAG, input.getID().getDatabaseName() + "::" + input.getID().getId());
        }


        return obj;
    }

    @Override
    public void fromJson(String json, Collection<TransStation> output) {
        Map<String, TransChain> chainMap = new ConcurrentHashMap<>();
        JSONArray array = new JSONArray(json);
        for (int i = 0; i < array.length(); i++) {
            JSONObject objJson = array.getJSONObject(i);
            TransStation obj = fromJsonObject(objJson, chainMap);
            output.add(obj);
        }
    }

    @Override
    public void fromJsonArray(JSONArray array, Collection<TransStation> output) {
        Map<String, TransChain> chainMap = new ConcurrentHashMap<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject objJson = array.getJSONObject(i);
            TransStation obj = fromJsonObject(objJson, chainMap);
            output.add(obj);
        }
    }

    @Override
    public TransStation fromJsonObject(JSONObject json) {
        return fromJsonObject(json, new ConcurrentHashMap<>());
    }

    public TransStation fromJsonObject(JSONObject obj, Map<String, TransChain> chains) {
        String name = obj.getString(NAME_TAG);
        double[] coords = new double[] { obj.getDouble(LAT_TAG), obj.getDouble(LNG_TAG) };

        TransChain chain = null;
        if (obj.has(CHAIN_TAG)) {
            String chainName = obj.getString(CHAIN_TAG);
            chains.putIfAbsent(chainName, new TransChain(chainName));
            chain = chains.get(chainName);
        }

        List<SchedulePoint> schedule = new ArrayList<>();
        if (obj.has(SCHEDULE_TAG)) {
            new SchedulePointJsonConverter().fromJson(obj.getJSONArray(SCHEDULE_TAG).toString(), schedule);
        }

        DatabaseID id = null;
        if (obj.has(ID_TAG)) {
            String[] components = obj.getString(ID_TAG).split("::");
            id = new DatabaseID(components[0], components[1]);
        }

        if (chain != null) return new TransStation(name, coords, schedule, chain, id);
        return new TransStation(name, coords, id);
    }
}
