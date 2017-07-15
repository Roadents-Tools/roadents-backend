package org.tymit.projectdonut.locations.postgresql;

/**
 * Created by ilan on 7/9/17.
 */
public final class PostgresqlLocationContract {
    private PostgresqlLocationContract() {
    }

    public final class LocationTable {
        public static final String TABLE_NAME = "locations";
        public static final String ID_KEY = "id";
        public static final String NAME_KEY = "name";
        public static final String LOCATION_KEY = "location";

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
                        "  OWNER TO donut;" +

                        "CREATE UNIQUE INDEX locations_location_name_idx" +
                        "  ON public.locations" +
                        "  USING btree" +
                        "  (location, name COLLATE pg_catalog.\"default\");";

        private LocationTable() {
        }

    }

    public final class TagTable {
        public static final String TABLE_NAME = "locationtags";
        public static final String TAG_KEY = "tag";
        public static final String LOCATION_ID_KEY = "locationid";

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
                        "  OWNER TO donut;" +

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
