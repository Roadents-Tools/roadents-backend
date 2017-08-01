package org.tymit.projectdonut.stations.dynamodb;

import ch.hsr.geohash.GeoHash;
import ch.hsr.geohash.WGS84Point;
import ch.hsr.geohash.queries.GeoHashCircleQuery;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.RangeKeyCondition;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import org.tymit.projectdonut.model.distance.Distance;
import org.tymit.projectdonut.model.distance.DistanceUnits;
import org.tymit.projectdonut.model.location.StartPoint;
import org.tymit.projectdonut.model.location.TransChain;
import org.tymit.projectdonut.model.location.TransStation;
import org.tymit.projectdonut.model.time.TimeDelta;
import org.tymit.projectdonut.model.time.TimePoint;
import org.tymit.projectdonut.stations.interfaces.StationDbInstance;
import org.tymit.projectdonut.utils.LocationUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Created by ilan on 6/9/17.
 */
public class DynamoStationChainDb implements StationDbInstance.ComboDb {

    private static final double MILES_TO_LAT = 1.0 / 69.5;
    private static final double MILES_TO_LONG = 1 / 69.5;
    private static final double MAX_RANGE = 50;
    private static final double ERROR_MARGIN = 0.0001;
    public static String[][] credentialList = new String[][] {
            { "AKIAISDSP6RHQG2RARNA", "HByI/CwMsL8fobViNGe63Lob0jkpIXLA7iiEAwiE" }
    };
    private AmazonDynamoDB client;
    private DynamoDB dynamoDB;
    private boolean isUp = true;

    public DynamoStationChainDb(String accessKey, String secret) {
        AWSCredentials credentials = new BasicAWSCredentials(accessKey, secret);
        AWSCredentialsProvider provider = new AWSStaticCredentialsProvider(credentials);
        client = AmazonDynamoDBClientBuilder.standard()
                .withCredentials(provider)
                .withRegion(Regions.US_WEST_2)
                .build();
        dynamoDB = new DynamoDB(client);

        initializeTables();
        long totalSize = StreamSupport.stream(dynamoDB.listTables().spliterator(), false)
                .map(Table::describe)
                .mapToLong(TableDescription::getTableSizeBytes)
                .sum();

        long totalItems = StreamSupport.stream(dynamoDB.listTables().spliterator(), false)
                .map(Table::describe)
                .mapToLong(TableDescription::getItemCount)
                .sum();

    }

    private void initializeTables() {
        boolean makeStation = true;
        boolean makeChain = true;

        for (Table table : dynamoDB.listTables()) {

            String tableName = table.getTableName();

            if (tableName.equals(DynamoDbContract.StationTable.TABLE_NAME)) makeStation = false;
            else if (tableName.equals(DynamoDbContract.ChainTable.TABLE_NAME)) makeChain = false;

            if (!makeChain && !makeStation) break;
        }

        if (makeStation) {
            initializeStationTable();
        }
        if (makeChain) {
            initializeChainTable();
        }
    }

    private void initializeStationTable() {
        CreateTableRequest request = new CreateTableRequest()
                .withTableName(DynamoDbContract.StationTable.TABLE_NAME)
                .withAttributeDefinitions(
                        new AttributeDefinition(DynamoDbContract.StationTable.GEOHASH, ScalarAttributeType.N),
                        new AttributeDefinition(DynamoDbContract.StationTable.LONG_HASH, ScalarAttributeType.S)
                )
                .withProvisionedThroughput(new ProvisionedThroughput(12L, 12L))
                .withKeySchema(
                        new KeySchemaElement(DynamoDbContract.StationTable.GEOHASH, KeyType.HASH),
                        new KeySchemaElement(DynamoDbContract.StationTable.LONG_HASH, KeyType.RANGE)
                );
        dynamoDB.createTable(request);
    }

    private void initializeChainTable() {
        CreateTableRequest request = new CreateTableRequest()
                .withTableName(DynamoDbContract.ChainTable.TABLE_NAME)
                .withAttributeDefinitions(
                        new AttributeDefinition(DynamoDbContract.ChainTable.CHAIN_NAME, ScalarAttributeType.S)
                )
                .withProvisionedThroughput(new ProvisionedThroughput(12L, 12L))
                .withKeySchema(
                        new KeySchemaElement(DynamoDbContract.ChainTable.CHAIN_NAME, KeyType.HASH)
                );
        dynamoDB.createTable(request);
    }

    @Override
    public boolean putStations(List<TransStation> stations) {
        return DynamoDbSupport.insertStations(stations, dynamoDB);
    }

    @Override
    public boolean isUp() {
        return isUp;
    }

    @Override
    public void close() {
        dynamoDB.shutdown();
        client.shutdown();
        isUp = false;
    }

    public boolean putStations(Map<TransChain, List<TransStation>> stations) {
        return DynamoDbSupport.insertStations(stations, dynamoDB);
    }

    public DynamoDB getDynamoDB() {
        return this.dynamoDB;
    }

    public AmazonDynamoDB getClient() {
        return client;
    }

    @Override
    public List<TransStation> queryStations(double[] center, double range, TimePoint startTime, TimeDelta maxDelta, TransChain chain) {

        //We need a primary key query on either the station table or the chain table. Otherwise we return nothing.
        if ((center == null || center.length != 2 || range < 0 || range > MAX_RANGE) && (chain == null || chain.getName() == null)) {
            return Collections.emptyList();
        }

        Predicate<TransStation> timeTest = withinTime(startTime, maxDelta);

        //If our seach area is small enough we treat the query as a single station item request
        if (center != null && range <= ERROR_MARGIN) {
            GeoHash shortHash = GeoHash.withBitPrecision(center[0], center[1], DynamoDbContract.StationTable.GEOHASH_BITS);
            GeoHash bigHash = GeoHash.withBitPrecision(center[0], center[1], DynamoDbContract.StationTable.LONGHASH_BITS);
            Item singleStation = dynamoDB.getTable(DynamoDbContract.StationTable.TABLE_NAME)
                    .getItem(
                            DynamoDbContract.StationTable.GEOHASH, shortHash.ord(),
                            DynamoDbContract.StationTable.LONG_HASH, bigHash.toBinaryString()
                    );

            if (singleStation != null) {
                return DynamoDbSupport.readItemS(singleStation);
            }
        }

        Predicate<TransStation> rangeTest = withinRange(center, range);

        //If we have a given chain only query that chain
        if (chain != null) {
            Item itemToFilter = dynamoDB.getTable(DynamoDbContract.ChainTable.TABLE_NAME)
                    .getItem(DynamoDbContract.ChainTable.CHAIN_NAME, chain.getName());
            List<TransStation> stationsToFilter = DynamoDbSupport.readItemC(itemToFilter);
            return stationsToFilter.stream()
                    .filter(rangeTest)
                    .filter(timeTest)
                    .collect(Collectors.toList());
        }


        GeoHashCircleQuery circleQuery = new GeoHashCircleQuery(
                new WGS84Point(center[0], center[1]),
                new Distance(range, DistanceUnits.MILES).inMeters()
        );

        List<Long> shortHashes = circleQuery.getSearchHashes().stream()
                .map(GeoHash::toBinaryString)
                .peek(System.out::println)
                .map(binstr -> binstr.substring(0, DynamoDbContract.StationTable.GEOHASH_BITS))
                .distinct()
                .map(str -> Long.valueOf(str, 2))
                .collect(Collectors.toList());

        String longHashPrefix = circleQuery.getSearchHashes().stream()
                .map(GeoHash::toBinaryString)
                .distinct()
                .reduce((s, s2) -> {
                    StringBuilder builder = new StringBuilder();
                    for (int i = 0; i < s.length(); i++) {
                        if (s.charAt(i) != s2.charAt(i)) break;
                        if (i >= s2.length()) break;
                        builder.append(s.charAt(i));
                    }
                    return builder.toString();
                })
                .orElse("");


        QuerySpec request = new QuerySpec()
                .withHashKey(DynamoDbContract.StationTable.GEOHASH, shortHashes.get(0))
                .withRangeKeyCondition(new RangeKeyCondition(DynamoDbContract.StationTable.LONG_HASH).beginsWith(longHashPrefix));

        return StreamSupport.stream(dynamoDB.getTable(DynamoDbContract.StationTable.TABLE_NAME)
                .query(request)
                .spliterator(), false)

                .map(DynamoDbSupport::readItemS)
                .flatMap(Collection::stream)

                .filter(timeTest)
                .filter(rangeTest)

                .collect(Collectors.toList());
    }

    private static Predicate<TransStation> withinRange(double[] center, double range) {
        if (center == null || range < 0) return any -> true;
        StartPoint startPoint = new StartPoint(center);
        return stat -> LocationUtils.distanceBetween(startPoint, stat).inMiles() <= range;
    }

    private static Predicate<TransStation> withinTime(TimePoint startTime, TimeDelta maxDelta) {
        if (startTime == null || maxDelta == null || startTime.equals(TimePoint.NULL) || maxDelta.getDeltaLong() <= 0) {
            return a -> true;
        }
        return station ->
                startTime.timeUntil(station.getNextArrival(startTime)).getDeltaLong() <= maxDelta.getDeltaLong();
    }

}
