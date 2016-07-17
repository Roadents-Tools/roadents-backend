package org.tymit.restcontroller.jsonconvertion;

import com.google.gson.Gson;
import org.tymit.projectdonut.model.DestinationLocation;

/**
 * Created by ilan on 7/16/16.
 */
public class DestinationJsonConverter implements JsonConverter<DestinationLocation> {

    Gson gson = new Gson();

    @Override
    public String toJson(DestinationLocation input) {
        return gson.toJson(input);
    }

    @Override
    public DestinationLocation fromJson(String json) {
        return gson.fromJson(json, DestinationLocation.class);
    }
}
