package com.reroute.backend.locations.gmaps;

import com.reroute.backend.locations.interfaces.LocationProvider;
import com.reroute.backend.model.distance.Distance;
import com.reroute.backend.model.location.DestinationLocation;
import com.reroute.backend.model.location.LocationPoint;
import com.reroute.backend.model.location.LocationType;
import com.reroute.backend.utils.LocationUtils;
import com.reroute.backend.utils.LoggingUtils;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.http.GET;
import retrofit2.http.Query;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by ilan on 8/25/16.
 */
public class GoogleLocationsProvider implements LocationProvider {

    public static final String[] API_KEYS = {
            "AIzaSyB0pBdXuC4VRte73qnVtE5pLmxNs3ju0Gg"
    };

    private static final String BASE_URL = "https://maps.googleapis.com/";
    private final RestInterface rest;
    private int apiInd = 0;

    public GoogleLocationsProvider() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .build();
        rest = retrofit.create(RestInterface.class);
    }

    @Override
    public boolean isUsable() {
        return apiInd < API_KEYS.length;
    }

    @Override
    public boolean isValidType(LocationType type) {
        return true;
    }

    @Override
    public List<DestinationLocation> queryLocations(LocationPoint center, Distance range, LocationType type) {
        Call<ResponseBody> result = rest.getLocations(center.getCoordinates()[0] + "," + center.getCoordinates()[1], type
                .getEncodedname(), "distance", API_KEYS[apiInd]);

        Response<ResponseBody> response;
        try {
            response = result.execute();
        } catch (IOException e) {
            LoggingUtils.logError(e);
            apiInd++;
            return Collections.emptyList();
        }

        if (!response.isSuccessful()) {
            LoggingUtils.logError("GoogleRetrofitProvider", "Response failed.\nResponse: " + response.raw().toString());
            apiInd++;
        }


        try {
            String raw = new String(response.body().bytes());
            JSONObject obj = new JSONObject(raw);
            JSONArray arr = obj.getJSONArray("results");
            return getLocationsFromGglJson(arr, type).stream()
                    .filter(location -> LocationUtils.distanceBetween(center, location).inMeters() < range.inMeters())
                    .collect(Collectors.toList());
        } catch (JSONException | IOException e) {
            LoggingUtils.logError(e);
            LoggingUtils.logError("GoogleRetrofitProvider", "JSON: " + response.toString());
            return Collections.emptyList();
        }
    }

    @Override
    public void close() {
    }

    private static List<DestinationLocation> getLocationsFromGglJson(JSONArray arr, LocationType type) {
        int size = arr.length();

        return IntStream.range(0, size)
                .boxed().parallel()
                .map(arr::getJSONObject)
                .map(currObj -> {

                    JSONObject coords = currObj.getJSONObject("geometry").getJSONObject("location");
                    double[] latlong = new double[] { coords.getDouble("lat"), coords.getDouble("lng") };

                    String name = currObj.getString("name");
                    return new DestinationLocation(name, type, latlong);
                })
                .collect(Collectors.toList());

    }

    private interface RestInterface {

        @GET("maps/api/place/nearbysearch/json")
        Call<ResponseBody> getLocations(@Query("location") String latLong, @Query("keyword") String keyword,
                                        @Query("rankby") String rankBy, @Query("key") String apiKey);

    }


}
