package net.unit8.timeshift.examples;

import org.apache.log4j.PatternLayout;

/**
 * @author kawasima
 */
public class PatternLayoutWithThrowable extends PatternLayout {
    @Override
    public boolean ignoresThrowable() {
        return false;
    }
}
