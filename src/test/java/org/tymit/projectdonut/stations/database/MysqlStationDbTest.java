package org.tymit.projectdonut.stations.database;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tymit.projectdonut.model.TimeModel;
import org.tymit.projectdonut.model.TransChain;
import org.tymit.projectdonut.model.TransStation;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Created by ilan on 7/10/16.
 */
public class MysqlStationDbTest {

    private MysqlStationDb instance;

    @Before
    public void setupDb() {
        instance = new MysqlStationDb(MysqlStationDb.DB_URLS[0]);
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
                List<TimeModel> chainSchedule = new ArrayList<>(7);
                for (int j = 0; j < 7; j++) {
                    TimeModel newModel = new TimeModel()
                            .set(TimeModel.DAY_OF_WEEK, i)
                            .set(TimeModel.HOUR, j);
                    chainSchedule.add(newModel);
                }
                String stationName = String.format("TEST STATION: chain=%s, i=%d", chain.getName(), i);
                TransStation station = new TransStation(stationName, new double[]{rng.nextDouble(), rng.nextDouble()}, chainSchedule, chain);
                allStations.add(station);
            }
        }
        boolean putSuccess = instance.putStations(allStations);
        if (!putSuccess) LoggingUtils.printLog();
        Assert.assertTrue(putSuccess);

        List<TransStation> retStations = instance.queryStations(null, 0, null);
        if (retStations == null) LoggingUtils.printLog();
        Assert.assertEquals(25, retStations.size());

        List<TransStation> selectiveRet = instance.queryStations(null, 0, testChains[0]);
        if (selectiveRet == null) LoggingUtils.printLog();
        Assert.assertEquals(5, selectiveRet.size());

        TransStation singleStation = allStations.get(0);
        System.out.println(singleStation.getName());
        List<TransStation> rangeQueried = instance.queryStations(singleStation.getCoordinates(), 0, null);
        if (rangeQueried == null) LoggingUtils.printLog();
        Assert.assertEquals(1, rangeQueried.size());
    }

    @After
    public void cleanupDb() {
        try {
            instance.clearDb();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}