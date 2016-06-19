import locationtypes.LocationType;

/**
 * Created by ilan on 6/19/16.
 */
public class LocationPoint {

    private LocationType type;
    private String name;
    private double[] location;

    public LocationPoint(LocationType type, String name, double[] location) {
        this.type = type;
        this.name = name;
        this.location = location;
    }

    public LocationType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public double[] getLocation() {
        return location;
    }
}
