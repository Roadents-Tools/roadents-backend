package org.tymit.restcontroller;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.tymit.projectdonut.utils.LoggingUtils;

/**
 * Created by ilan on 7/15/16.
 */

@SpringBootApplication
public class Application {

    //How often to updatae the station database; currently 15 minutes.
    private static final long DB_UPDATE_INTERVAL = 1000L * 60L * 15L;

    public static void main(String[] args) {
        LoggingUtils.setPrintImmediate(true);
        SpringApplication.run(Application.class, args);
    }
}
