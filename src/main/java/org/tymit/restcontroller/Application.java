package org.tymit.restcontroller;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.tymit.ScratchRunner;
import org.tymit.projectdonut.utils.LoggingUtils;

import java.util.Arrays;

/**
 * Created by ilan on 7/15/16.
 */

@SpringBootApplication
public class Application {

    //How often to updatae the station database; currently 15 minutes.
    private static final long DB_UPDATE_INTERVAL = 1000L * 60L * 15L;

    public static void main(String[] args) throws Exception {
        LoggingUtils.setPrintImmediate(true);
        if (Arrays.stream(args).anyMatch(s -> s.equals("--scratch"))) {
            ScratchRunner.main(args);
        } else {
            SpringApplication.run(Application.class, args);
        }
    }
}
