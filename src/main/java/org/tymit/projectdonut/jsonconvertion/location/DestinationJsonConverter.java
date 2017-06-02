package org.tymit.projectdonut.jsonconvertion.location;

import com.google.gson.Gson;
import org.tymit.projectdonut.jsonconvertion.JsonConverter;
import org.tymit.projectdonut.model.location.DestinationLocation;

/**
 * Created by ilan on 7/16/16.
 */
public class DestinationJsonConverter implements JsonConverter<DestinationLocation> {

    final Gson gson = new Gson();

    @Override
    public String toJson(DestinationLocation input) {
        return gson.toJson(input);
    }

    @Override
    public DestinationLocation fromJson(String json) {
        return gson.fromJson(json, DestinationLocation.class);
    }
}
