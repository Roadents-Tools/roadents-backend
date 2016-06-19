import java.util.List;

/**
 * Created by ilan on 6/19/16.
 */
public class TransChain {

    List<TransStation> stations;
    String name;

    public TransChain(List<TransStation> stations, String name) {
        this.stations = stations;
        this.name = name;
    }

    private static DateTime getTimeDistBetweenStations(DateTime start, TransStation a, TransStation b) {
        DateTime aTime = null;
        for (DateTime time : a.getSchedule()) {
            if (aTime == null || aTime.getUnixTime() > start.distanceTo(time).getUnixTime()) aTime = time;
        }
        DateTime bTime = null;
        for (DateTime time : b.getSchedule()) {
            if (bTime == null || bTime.getUnixTime() > aTime.distanceTo(time).getUnixTime()) bTime = time;
        }
        return bTime;
    }

    public List<TransStation> getStations() {
        return stations;
    }

    public String getName() {
        return name;
    }
}
