package net.unit8.timeshift.examples;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

import java.io.IOException;
import java.util.Random;

/**
 * @author kawasima
 */
public class Main {
    private static Level[] levels = {
            Level.FATAL,
            Level.ERROR,
            Level.WARN,
            Level.INFO,
            Level.DEBUG
    };

    private static final Logger logger = Logger.getLogger(Main.class);
    public static void main(String[] args) throws IOException {
        Random rnd = new Random();
        NDC.push("USER01");
        for (int i=0; i<10; i++) {
            logger.log(levels[rnd.nextInt(5)], "jojo", new Exception());
        }
    }
}
