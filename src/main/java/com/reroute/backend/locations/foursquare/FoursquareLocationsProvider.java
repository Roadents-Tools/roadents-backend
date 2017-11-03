package com.reroute.backend.locations.foursquare;

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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The FourSquare API as a source of destinations.
 */
public class FoursquareLocationsProvider implements LocationProvider {

    private static final String[][] API_ID_KEYS = new String[][] {
            new String[] { "NTJ2WDYNSATDGFBVF45QEHXSJWM5SXEHTGABU05LYHMRJXQD", "PJ22A4QHXL5IT4FPY3E22SZWFXRCQXRPAVRGKFTB32RZZKZE" }
    };
    private static final String BASE_URL = "https://api.foursquare.com/";
    private static final String VERSION_DATE = "20171001";
    private static final String CATEGORIES_PATH = "datafiles/FoursquareCategories.json";

    private static final Map<String, String> categories = new ConcurrentHashMap<>();
    private final RestInterface rest;
    private int apiInd = 0;

    public FoursquareLocationsProvider() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .build();
        rest = retrofit.create(RestInterface.class);
    }

    private static DestinationLocation mapJsonToDest(JSONObject jsonObject, LocationType type) {
        String name = jsonObject.getString("name");

        JSONObject locationObj = jsonObject.getJSONObject("location");
        double[] coords = { locationObj.getDouble("lat"), locationObj.getDouble("lng") };
        String address = locationObj.optString("address");

        return new DestinationLocation(name, type, coords, address);
    }

    @Override
    public boolean isUsable() {
        return apiInd < API_ID_KEYS.length;
    }

    @Override
    public boolean isValidType(LocationType type) {
        return true;
    }

    @Override
    public List<DestinationLocation> queryLocations(LocationPoint center, Distance range, LocationType type) {

        //Build the parameters for the call
        String ll = center.getCoordinates()[0] + "," + center.getCoordinates()[1];
        int rangeInMeters = 1 + (int) range.inMeters();

        if (categories == null || categories.isEmpty()) buildCategoryMap();
        String category = (categories.containsKey(type.getEncodedname())) ? categories.get(type.getEncodedname()) : categories.get(type.getVisibleName());
        String titleFilter = (category == null) ? type.getEncodedname() : null;

        //Perform the call
        Call<ResponseBody> result = rest.searchVenues(VERSION_DATE, API_ID_KEYS[apiInd][0], API_ID_KEYS[apiInd][1],
                ll, titleFilter, 50, "browse", rangeInMeters, category);

        Response<ResponseBody> response;
        try {
            response = result.execute();
        } catch (IOException e) {
            LoggingUtils.logError(e);
            apiInd++;
            return Collections.emptyList();
        }

        if (!response.isSuccessful()) {
            LoggingUtils.logError("FourSquareRetrofitProvider", "Response failed.\nResponse: " + response.raw().toString());
            apiInd++;
            return Collections.emptyList();
        }

        try {
            String raw = new String(response.body().bytes());
            JSONObject obj = new JSONObject(raw);
            JSONArray arr = obj.getJSONObject("response").getJSONArray("venues");
            int size = arr.length();
            List<DestinationLocation> rval = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                JSONObject jsonObject = arr.getJSONObject(i);
                DestinationLocation dest = mapJsonToDest(jsonObject, type);
                if (LocationUtils.distanceBetween(dest, center).inMeters() < range.inMeters()) {
                    rval.add(dest);
                }
            }
            return rval;

        } catch (JSONException | IOException e) {
            LoggingUtils.logError(e);
            LoggingUtils.logError("FourSquareRetrofitProvider", "JSON: " + response.toString());
            return Collections.emptyList();
        }
    }

    @Override
    public void close() {
    }

    private void buildCategoryMap() {
        try {
            String raw = Files.lines(Paths.get(CATEGORIES_PATH)).collect(Collectors.joining());
            JSONObject obj = new JSONObject(raw);
            JSONArray arr = obj.getJSONObject("response").getJSONArray("categories");
            addCategoriesFromArrayIter(arr);

        } catch (JSONException | IOException e) {
            LoggingUtils.logError(e);
        }
    }

    private void addCategoriesFromArrayIter(JSONArray arr) {
        int size = arr.length();
        for (int i = 0; i < size; i++) {
            JSONObject jsonObject = arr.getJSONObject(i);
            String id = jsonObject.getString("id");
            categories.put(jsonObject.getString("name"), id);
            categories.put(jsonObject.getString("shortName"), id);
            categories.put(jsonObject.getString("pluralName"), id);
            //Each supercategory can have subcategories, eg "Food" can have subcategories like "Chinese Food"
            //or "Russian Food"
            if (jsonObject.has("categories")) {
                addCategoriesFromArrayIter(jsonObject.getJSONArray("categories"));
            }
        }
    }

    private interface RestInterface {
        @GET("/v2/venues/categories")
        Call<ResponseBody> getCategories(@Query("v") String dateString, @Query("client_id") String clientId,
                                         @Query("client_secret") String clientPass);

        @GET("v2/venues/search")
        Call<ResponseBody> searchVenues(@Query("v") String dateString, @Query("client_id") String clientId, @Query("client_secret") String clientPass,
                                        @Query("ll") String latlong, @Query("query") String titleFilter, @Query("limit") int limit,
                                        @Query("intent") String intent, @Query("radius") int radius, @Query("categoryId") String category);
    }
}
