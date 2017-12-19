package com.reroute.backend.locations.postgresql;

/**
 * String constants related to the Postgresql Location Database, organized by table.
 */
public final class PostgresqlLocationContract {
    private PostgresqlLocationContract() {
    }

    /**
     * The table storing the location data itself.
     */
    public final class LocationTable {

        /**
         * The name of the table.
         */
        public static final String TABLE_NAME = "locations";

        /**
         * The unique ID field.
         */
        public static final String ID_KEY = "id";

        /**
         * The name of the location
         */
        public static final String NAME_KEY = "name";

        /**
         * The latitude-longitude point on the earth that this location is located at
         */
        public static final String LOCATION_KEY = "location";

        /**
         * The script to create the table
         */
        public static final String CREATE_TABLE =
                "CREATE TABLE public.locations" +
                        "(" +
                        "  name text NOT NULL," +
                        "  location geography NOT NULL," +
                        "  id integer NOT NULL DEFAULT nextval('locations_id_seq'::regclass)," +
                        "  CONSTRAINT locations_pkey PRIMARY KEY (id)" +
                        ")" +
                        "WITH (" +
                        "  OIDS=FALSE" +
                        ");" +

                        "ALTER TABLE public.locations" +
                        "  OWNER TO generator;" +

                        "CREATE UNIQUE INDEX locations_location_name_idx" +
                        "  ON public.locations" +
                        "  USING btree" +
                        "  (location, name COLLATE pg_catalog.\"default\");";

        private LocationTable() {
        }

    }

    /**
     * The table that matches type tags to locations.
     */
    public final class TagTable {

        /**
         * The table name
         */
        public static final String TABLE_NAME = "locationtags";

        /**
         * The field that stores the tag
         */
        public static final String TAG_KEY = "tag";

        /**
         * The foreign key to the location with this tag
         */
        public static final String LOCATION_ID_KEY = "locationid";

        /**
         * The script to create the table
         */
        public static final String CREATE_TABLE =
                "CREATE TABLE public.locationtags" +
                        "(" +
                        "  tag text NOT NULL," +
                        "  locationid integer NOT NULL," +
                        "  CONSTRAINT locationtags_locationid_fkey FOREIGN KEY (locationid)" +
                        "      REFERENCES public.locations (id) MATCH SIMPLE" +
                        "      ON UPDATE NO ACTION ON DELETE CASCADE" +
                        ")" +
                        "WITH (" +
                        "  OIDS=FALSE" +
                        ");" +

                        "ALTER TABLE public.locationtags" +
                        "  OWNER TO generator;" +

                        "CREATE INDEX fki_tagforeignkey" +
                        "  ON public.locationtags" +
                        "  USING btree" +
                        "  (locationid);" +

                        "CREATE UNIQUE INDEX locationtags_tag_locationid_idx" +
                        "  ON public.locationtags" +
                        "  USING btree" +
                        "  (tag COLLATE pg_catalog.\"default\", locationid);";

        private TagTable() {
        }
    }
}
