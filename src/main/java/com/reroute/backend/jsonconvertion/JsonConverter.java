package com.reroute.backend.jsonconvertion;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collection;
import java.util.StringJoiner;

/**
 * Created by ilan on 7/16/16.
 */
public interface JsonConverter<T> {
    default String toJson(Collection<? extends T> allObjs) {
        StringBuilder builder = new StringBuilder("[");
        StringJoiner objJoiner = new StringJoiner(",");
        allObjs.stream().map(this::toJson).forEach(objJoiner::add);
        builder.append(objJoiner.toString()).append("]");
        return builder.toString();
    }

    String toJson(T input);

    default void fromJson(String json, Collection<T> output) {
        JSONArray array = new JSONArray(json);
        for (int i = 0; i < array.length(); i++) {
            JSONObject objJson = array.getJSONObject(i);
            String objString = objJson.toString();
            T obj = fromJson(objString);
            output.add(obj);
        }
    }

    T fromJson(String json);
}
