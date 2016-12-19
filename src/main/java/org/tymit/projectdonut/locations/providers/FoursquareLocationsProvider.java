package org.tymit.projectdonut.locations.providers;

import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tymit.projectdonut.model.DestinationLocation;
import org.tymit.projectdonut.model.LocationType;
import org.tymit.projectdonut.utils.LocationUtils;
import org.tymit.projectdonut.utils.LoggingUtils;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.http.GET;
import retrofit2.http.Query;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by ilan on 12/18/16.
 */
public class FoursquareLocationsProvider implements LocationProvider{

    public static final String[][] API_ID_KEYS = new String[][]{
        new String[]{"NTJ2WDYNSATDGFBVF45QEHXSJWM5SXEHTGABU05LYHMRJXQD",  "PJ22A4QHXL5IT4FPY3E22SZWFXRCQXRPAVRGKFTB32RZZKZE"}
    };
    private static final String BASE_URL = "https://api.foursquare.com/";
    private static final String VERSION_DATE = "20161219";

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
        JSONObject locationObj = jsonObject.getJSONObject("location");
        return new DestinationLocation(jsonObject.getString("name"), type, new double[] { locationObj.getDouble("lat"), locationObj.getDouble("lng") });
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
    public List<DestinationLocation> queryLocations(double[] center, double range, LocationType type) {

        //Build the parameters for the call
        String ll = center[0]+","+center[1];
        int rangeInMeters = 1+(int) LocationUtils.milesToMeters(range);

        if (categories == null || categories.isEmpty()) buildCategoryMap();
        String category = (categories.containsKey(type.getEncodedname())) ? categories.get(type.getEncodedname()) : categories.get(type.getVisibleName());
        String titleFilter = (category == null) ? type.getEncodedname() : null;

        //Perform the call
        Call<ResponseBody> result = rest.searchVenues(VERSION_DATE, API_ID_KEYS[apiInd][0], API_ID_KEYS[apiInd][1],
                ll, titleFilter, 50, "browse", rangeInMeters, category);

        Response<ResponseBody> response;
        try {
            response = result.execute();
        } catch (IOException e){
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
            return IntStream.range(0, size)
                    .boxed().parallel()
                    .map(arr::getJSONObject)
                    .map(jsonObject -> mapJsonToDest(jsonObject, type))
                    .filter(dest -> LocationUtils.distanceBetween(dest.getCoordinates(), center, true) < range)
                    .collect(Collectors.toList());

        } catch (JSONException | IOException e) {
            LoggingUtils.logError(e);
            LoggingUtils.logError("FourSquareRetrofitProvider", "JSON: " + response.toString());
            return Collections.emptyList();
        }
    }

    private void buildCategoryMap() {
        Call<ResponseBody> result = rest.getCategories("20161218",API_ID_KEYS[apiInd][0], API_ID_KEYS[apiInd][1]);

        Response<ResponseBody> response;
        try {
            response = result.execute();
        } catch (IOException e) {
            LoggingUtils.logError(e);
            apiInd++;
            return;
        }

        if (!response.isSuccessful()) {
            LoggingUtils.logError("FourSquareRetrofitProvider", "Response failed.\nResponse: " + response.raw().toString());
            apiInd++;
            return;
        }

        try {
            String raw = new String(response.body().bytes());
            JSONObject obj = new JSONObject(raw);
            JSONArray arr = obj.getJSONObject("response").getJSONArray("categories");
            addCategoriesFromArray(arr);

        } catch (JSONException | IOException e) {
            LoggingUtils.logError(e);
            LoggingUtils.logError("FourSquareRetrofitProvider", "JSON: " + response.toString());
        }
    }

    private void addCategoriesFromArray(JSONArray arr){
        int size = arr.length();

        IntStream.range(0, size)
                .boxed().parallel()
                .map(arr::getJSONObject)
                .forEach(jsonObject -> {
                    String id = jsonObject.getString("id");
                    categories.put(jsonObject.getString("name"), id);
                    categories.put(jsonObject.getString("shortName"), id);
                    categories.put(jsonObject.getString("pluralName"), id);

                    //Each supercategory can have subcategories, eg "Food" can have subcategories like "Chinese Food"
                    //or "Russian Food"
                    if (jsonObject.has("categories")){
                        addCategoriesFromArray(jsonObject.getJSONArray("categories"));
                    }
                });
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
