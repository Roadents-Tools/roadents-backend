package org.tymit.projectdonut.stations.database;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tymit.projectdonut.model.SchedulePoint;
import org.tymit.projectdonut.model.TransChain;
import org.tymit.projectdonut.model.TransStation;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Created by ilan on 7/10/16.
 */
public class MysqlStationDbTest {

    private MysqlStationDb instance;

    @Before
    public void setupDb() {
        for (String url : MysqlStationDb.DB_URLS) {
            instance = new MysqlStationDb(url);
            if (instance.isUp()) break;
        }
    }

    @Test
    public void testLocalMysql() {
        Random rng = new Random(175);
        TransChain[] testChains = new TransChain[5];
        for (int i = 0; i < 5; i++) {
            TransChain chain = new TransChain("TESTCHAIN " + i);
            testChains[i] = chain;
        }
        List<TransStation> allStations = new ArrayList<>();
        for (TransChain chain : testChains) {
            for (int i = 0; i < 5; i++) {
                List<SchedulePoint> chainSchedule = new ArrayList<>(7);
                for (int j = 0; j < 7; j++) {
                    boolean[] validDays = new boolean[7];
                    validDays[i] = true;
                    chainSchedule.add(new SchedulePoint(j, 0, 0, validDays, 60));
                }
                String stationName = String.format("TEST STATION: chain=%s, i=%d", chain.getName(), i);
                TransStation station = new TransStation(stationName, new double[]{rng.nextDouble(), rng.nextDouble()}, chainSchedule, chain);
                allStations.add(station);
            }
        }
        boolean putSuccess = instance.putStations(allStations);
        if (!putSuccess) LoggingUtils.printLog();
        Assert.assertTrue(putSuccess);

        List<TransStation> retStations = allStations.stream()
                .flatMap(station -> instance.queryStations(station.getCoordinates(), 0, station.getChain()).stream())
                .collect(Collectors.toList());
        if (retStations == null) LoggingUtils.printLog();
        Assert.assertEquals(25, retStations.size());

        List<TransStation> selectiveRet = instance.queryStations(null, 0, testChains[0]);
        if (selectiveRet == null) LoggingUtils.printLog();
        Assert.assertEquals(5, selectiveRet.size());

        TransStation singleStation = allStations.get(0);
        List<TransStation> rangeQueried = instance.queryStations(singleStation.getCoordinates(), 0, null);
        if (rangeQueried == null) LoggingUtils.printLog();
        Assert.assertEquals(1, rangeQueried.size());
    }

    @Test
    public void testApostrepheAndQuotes() {
        TransChain chain = new TransChain("Ilan's chain");
        TransChain chain2 = new TransChain("Ilan\"s chain");
        List<TransStation> allStations = new ArrayList<>();
        allStations.add(new TransStation("Ilan's Test Station", new double[] { 131, 131 }, null, chain));
        allStations.add(new TransStation("Ilan\"s Test Station", new double[] { 131, 131 }, null, chain2));
        instance.putStations(allStations);

    }

    @After
    public void cleanupDb() {
        instance.removeItems(
                MysqlSupport.CHAIN_NAME_KEY + " LIKE '%TEST%'",
                new String[] { MysqlSupport.CHAIN_TABLE_NAME }
        );
        instance.removeItems(
                MysqlSupport.STATION_NAME_KEY + " LIKE '%TEST%'",
                new String[] { MysqlSupport.STATION_TABLE_NAME }
        );
    }

}