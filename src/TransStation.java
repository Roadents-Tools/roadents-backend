import locationtypes.LocationType;

import java.util.List;

/**
 * Created by ilan on 6/19/16.
 */
public class TransStation extends LocationPoint {

    private List<DateTime> schedule;

    public TransStation(String name, double[] location, List<DateTime> schedule) {
        super(LocationType.TRANS_STATION, name, location);
        this.schedule = schedule;
    }

    public List<DateTime> getSchedule() {
        return schedule;
    }

}
