package net.unit8.timeshift.examples;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        for(int i=0; i<10; i++) {
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    Random rnd = new Random();

                    NDC.push(Thread.currentThread().getName());
                    for (int i=0; i<10; i++) {
                        logger.log(levels[rnd.nextInt(5)], "jojo", new Exception());
                    }
                }
            });
        }
        executorService.shutdown();
    }
}
