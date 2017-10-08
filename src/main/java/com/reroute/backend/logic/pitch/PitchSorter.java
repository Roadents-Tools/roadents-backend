package com.reroute.backend.logic.pitch;

import com.reroute.backend.model.routing.TravelRoute;
import com.reroute.backend.utils.LocationUtils;

import java.util.Comparator;

public enum PitchSorter {
    MIN_TIME("time MIN", Comparator.comparing(t -> t.getTotalTime().getDeltaLong())),
    MAX_TIME("time MAX", Comparator.comparing((t -> t.getTotalTime().getDeltaLong()), Comparator.reverseOrder())),
    MIN_NODE("node MIN", Comparator.comparing(t -> t.getRoute().size(), Comparator.reverseOrder())),
    MAX_NODE("node MAX", Comparator.comparing(t -> t.getRoute().size())),
    MIN_DIST("dist MIN", Comparator.comparing(t -> LocationUtils.distanceBetween(t.getStart(), t.getCurrentEnd())
            .inMeters())),
    MAX_DIST("dist MAX", Comparator.comparing(t -> LocationUtils.distanceBetween(t.getStart(), t.getCurrentEnd())
            .inMeters(), Comparator.reverseOrder())),
    MIN_LENGTH("leng MIN", Comparator.comparing(t -> t.getTotalDisp().inMeters())),
    MAX_LENGTH("leng MAX", Comparator.comparing(t -> t.getTotalDisp().inMeters(), Comparator.reverseOrder())),
    MIN_LABOR("labor MIN", Comparator.comparing(t -> t.getTotalWalkDisp().inMeters())),
    MAX_LABOR("labor MAX", Comparator.comparing(t -> t.getTotalWalkDisp().inMeters(), Comparator.reverseOrder())),;


    private String tag;
    private Comparator<TravelRoute> comp;

    PitchSorter(String tag, Comparator<TravelRoute> comp) {
        this.tag = tag;
        this.comp = comp;
    }

    public String getTag() {
        return tag;
    }

    public Comparator<TravelRoute> getComparor() {
        return comp;
    }
}
