package net.unit8.timeshift.examples;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;

/**
 * @author kawasima
 */
public class FlumeAppender extends AppenderSkeleton {
    @Override
    protected void append(LoggingEvent event) {
    }

    @Override
    public void close() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean requiresLayout() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
