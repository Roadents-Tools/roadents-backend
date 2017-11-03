package com.reroute.backend.jsonconvertion;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collection;
import java.util.StringJoiner;

/**
 * An interface for converting model objects to JSON.
 */
public interface JsonConverter<T> {

    /**
     * Converts a single model to JSON.
     *
     * @param input the model
     * @return the JSON string
     */
    String toJson(T input);

    /**
     * Converts a collection of model objects to JSON
     *
     * @param allObjs the collection to convert
     * @return the JSON string of an array of the models
     */
    default String toJson(Collection<? extends T> allObjs) {
        StringBuilder builder = new StringBuilder("[");
        StringJoiner objJoiner = new StringJoiner(",");
        allObjs.stream().map(this::toJson).forEach(objJoiner::add);
        builder.append(objJoiner.toString()).append("]");
        return builder.toString();
    }

    /**
     * Converts a JSON array of model JSON to a collection of model objects.
     * @param json the JSON to convert
     * @param output the collection to add the models to
     */
    default void fromJson(String json, Collection<T> output) {
        JSONArray array = new JSONArray(json);
        for (int i = 0; i < array.length(); i++) {
            JSONObject objJson = array.getJSONObject(i);
            String objString = objJson.toString();
            T obj = fromJson(objString);
            output.add(obj);
        }
    }

    /**
     * Converts a JSON string to a model object.
     * @param json the JSON to convert
     * @return the model the JSON represents
     */
    T fromJson(String json);

}
