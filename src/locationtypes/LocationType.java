package locationtypes;

/**
 * Created by ilan on 6/19/16.
 */
public enum LocationType {

    RESTAURANT("Restaurant", "Restaurant"),
    TRANS_STATION("Station", null);

    private final String name;
    private final String queryTag;

    LocationType(String name, String queryTag) {
        this.name = name;
        this.queryTag = queryTag;
    }

    public String getName() {
        return name;
    }

    public String getQueryTag() {
        return queryTag;
    }
}
