package org.tymit.projectdonut.stations.caches;

/**
 * Created by ilan on 3/7/17.
 */
public final class PostgresqlContract {

    public static final String CHAIN_TABLE_NAME = "transchain";
    public static final String CHAIN_NAME_KEY = "name";
    public static final String CHAIN_ID_KEY = "id";
    public static final String CREATE_CHAIN_TABLE =
            "CREATE TABLE public.transchain" +
                    "(" +
                    "  name text," +
                    "  id integer NOT NULL DEFAULT nextval('transchain_id_seq'::regclass)," +
                    "  CONSTRAINT transchain_pkey PRIMARY KEY (id)," +
                    "  CONSTRAINT transchain_id_key UNIQUE (id)," +
                    "  CONSTRAINT transchain_name_key UNIQUE (name)" +
                    ")" +
                    "WITH (" +
                    "  OIDS=FALSE" +
                    ");" +

                    "CREATE UNIQUE INDEX transchain_id_idx" +
                    "  ON public.transchain" +
                    "  USING btree" +
                    "  (id);" +

                    "CREATE UNIQUE INDEX transchain_name_idx" +
                    "  ON public.transchain" +
                    "  USING btree" +
                    "  (name COLLATE pg_catalog.\"default\");";

    public static final String STATION_CHAIN_COST_TABLE_NAME = "StationChainCosts";
    public static final String COST_ID_KEY = "id";
    public static final String COST_STATION_KEY = "stationId";
    public static final String COST_CHAIN_KEY = "chainId";
    public static final String CREATE_COST_TABLE =
            "CREATE TABLE public.stationchaincosts" +
                    "(" +
                    "  id integer NOT NULL DEFAULT nextval('\"StationChainCosts_id_seq\"'::regclass)," +
                    "  stationid integer NOT NULL," +
                    "  chainid integer NOT NULL," +
                    "  CONSTRAINT \"StationChainCosts_pkey\" PRIMARY KEY (id)," +
                    "  CONSTRAINT \"StationChainCosts_chainid_fkey\" FOREIGN KEY (chainid)" +
                    "      REFERENCES public.transchain (id) MATCH SIMPLE" +
                    "      ON UPDATE NO ACTION ON DELETE NO ACTION," +
                    "  CONSTRAINT \"StationChainCosts_stationid_fkey\" FOREIGN KEY (stationid)" +
                    "      REFERENCES public.transstation (id) MATCH SIMPLE" +
                    "      ON UPDATE NO ACTION ON DELETE NO ACTION," +
                    "  CONSTRAINT stationchaincosts_stationid_chainid_key UNIQUE (stationid, chainid)" +
                    ")" +
                    "WITH (" +
                    "  OIDS=FALSE" +
                    ");" +

                    "CREATE INDEX stationchaincosts_chainid_idx" +
                    "  ON public.stationchaincosts" +
                    "  USING btree" +
                    "  (chainid);" +

                    "CREATE UNIQUE INDEX stationchaincosts_stationid_chainid_idx" +
                    "  ON public.stationchaincosts" +
                    "  USING btree" +
                    "  (stationid, chainid);" +

                    "CREATE INDEX stationchaincosts_stationid_idx" +
                    "  ON public.stationchaincosts" +
                    "  USING btree" +
                    "  (stationid);";

    public static final String SCHEDULE_TABLE_NAME = "schedule";
    public static final String SCHEDULE_ID_KEY = "id";
    public static final String SCHEDULE_FUZZ_KEY = "fuzz";
    public static final String SCHEDULE_TIME_KEY = "schedpoint";
    public static final String SCHEDULE_COST_ID_KEY = "costid";
    public static final String SCHEDULE_SUNDAY_VALID_KEY = "sunday";
    public static final String SCHEDULE_SATURDAY_VALID_KEY = "saturday";
    public static final String SCHEDULE_FRIDAY_VALID_KEY = "friday";
    public static final String SCHEDULE_THURSDAY_VALID_KEY = "thursday";
    public static final String SCHEDULE_WEDNESDAY_VALID_KEY = "wednesday";
    public static final String SCHEDULE_TUESDAY_VALID_KEY = "tuesday";
    public static final String SCHEDULE_MONDAY_VALID_KEY = "monday";
    public static final String CREATE_SCHEDULE_TABLE =
            "CREATE TABLE public.schedule" +
                    "(" +
                    "  id integer NOT NULL DEFAULT nextval('schedule_id_seq'::regclass)," +
                    "  sunday boolean NOT NULL," +
                    "  saturday boolean NOT NULL," +
                    "  friday boolean NOT NULL," +
                    "  thursday boolean NOT NULL," +
                    "  wednesday boolean NOT NULL," +
                    "  tuesday boolean NOT NULL," +
                    "  monday boolean NOT NULL," +
                    "  schedpoint time without time zone NOT NULL," +
                    "  fuzz integer NOT NULL," +
                    "  costid integer NOT NULL," +
                    "  CONSTRAINT schedule_pkey PRIMARY KEY (id)," +
                    "  CONSTRAINT schedule_costid_fkey FOREIGN KEY (costid)" +
                    "      REFERENCES public.stationchaincosts (id) MATCH SIMPLE" +
                    "      ON UPDATE NO ACTION ON DELETE NO ACTION," +
                    "  CONSTRAINT schedule_sunday_monday_tuesday_wednesday_thursday_friday_sa_key UNIQUE (sunday, monday, tuesday, wednesday, thursday, friday, saturday, costid, fuzz, schedpoint)" +
                    ")" +
                    "WITH (" +
                    "  OIDS=FALSE" +
                    ");" +

                    "CREATE INDEX schedule_costid_idx" +
                    "  ON public.schedule" +
                    "  USING btree" +
                    "  (costid);" +

                    "CREATE INDEX schedule_friday_idx" +
                    "  ON public.schedule" +
                    "  USING btree" +
                    "  (friday);" +

                    "CREATE INDEX schedule_monday_idx" +
                    "  ON public.schedule" +
                    "  USING btree" +
                    "  (monday);" +

                    "CREATE INDEX schedule_saturday_idx" +
                    "  ON public.schedule" +
                    "  USING btree" +
                    "  (saturday);" +

                    "CREATE INDEX schedule_sunday_idx" +
                    "  ON public.schedule" +
                    "  USING btree" +
                    "  (sunday);" +

                    "CREATE INDEX schedule_thursday_idx" +
                    "  ON public.schedule" +
                    "  USING btree" +
                    "  (thursday);" +

                    "CREATE INDEX schedule_time_idx" +
                    "  ON public.schedule" +
                    "  USING btree" +
                    "  (schedpoint);" +

                    "CREATE INDEX schedule_tuesday_idx" +
                    "  ON public.schedule" +
                    "  USING btree" +
                    "  (tuesday);" +

                    "CREATE INDEX schedule_wednesday_idx" +
                    "  ON public.schedule" +
                    "  USING btree" +
                    "  (wednesday);";

    public static final String RANGE_TABLE_NAME = "ranges";
    public static final String RANGE_ID_KEY = "id";
    public static final String RANGE_FUZZ_KEY = "maxfuzz";
    public static final String RANGE_TIME_KEY = "startime";
    public static final String RANGE_LAT_KEY = "latlng";
    public static final String RANGE_BOX_KEY = "distance";
    public static final String CREATE_RANGE_TABLE =
            "CREATE TABLE public.ranges" +
                    "(" +
                    "  latlng geography NOT NULL," +
                    "  distance bigint NOT NULL," +
                    "  startime timestamp without time zone NOT NULL," +
                    "  maxfuzz interval NOT NULL," +
                    "  id integer NOT NULL DEFAULT nextval('ranges_id_seq'::regclass)," +
                    "  CONSTRAINT ranges_pkey PRIMARY KEY (id)," +
                    "  CONSTRAINT ranges_latlng_startime_key UNIQUE (latlng, startime)" +
                    ")" +
                    "WITH (" +
                    "  OIDS=FALSE" +
                    ");" +
                    "CREATE INDEX ranges_latlng_idx" +
                    "  ON public.ranges" +
                    "  USING btree" +
                    "  (latlng);";

    public static final String STATION_TABLE_NAME = "transstation";
    public static final String STATION_ID_KEY = "id";
    public static final String STATION_NAME_KEY = "name";
    public static final String STATION_LATLNG_KEY = "latlng";
    public static final String CREATE_STATION_TABLE =
            "CREATE TABLE public.transstation" +
                    "(" +
                    "  latlng geography NOT NULL," +
                    "  name text NOT NULL," +
                    "  id integer NOT NULL DEFAULT nextval('transstation_id_seq'::regclass)," +
                    "  CONSTRAINT transstation_pkey PRIMARY KEY (id)," +
                    "  CONSTRAINT transstation_id_key UNIQUE (id)" +
                    ")" +
                    "WITH (" +
                    "  OIDS=FALSE" +
                    ");" +

                    "CREATE UNIQUE INDEX transstation_id_idx" +
                    "  ON public.transstation" +
                    "  USING btree" +
                    "  (id);" +

                    "CREATE UNIQUE INDEX transstation_latlng_idx" +
                    "  ON public.transstation" +
                    "  USING btree" +
                    "  (latlng);";

    private PostgresqlContract() {
    }
}
