package net.unit8.timeshift_logger;

import clojure.lang.IPersistentMap;
import clojure.lang.RT;
import clojure.lang.Symbol;
import org.apache.flume.Context;
import org.apache.flume.CounterGroup;
import org.apache.flume.Event;
import org.apache.flume.EventDrivenSource;
import org.apache.flume.conf.Configurable;
import org.apache.flume.conf.Configurables;
import org.apache.flume.event.EventBuilder;
import org.apache.flume.source.AbstractSource;
import org.apache.log4j.spi.LoggingEvent;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.serialization.ClassResolvers;
import org.jboss.netty.handler.codec.serialization.ObjectDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author kawasima
 */
public class Log4jSoketSource extends AbstractSource implements EventDrivenSource, Configurable{
    private static final Logger logger = LoggerFactory
            .getLogger(Log4jSoketSource.class);

    private int port;
    private String host = null;
    private Channel nettyChannel;
    private CounterGroup counterGroup = new CounterGroup();

    public class Log4jSocketHandler extends SimpleChannelHandler {
        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent mEvent) {
            while (ctx.getChannel().isReadable()) {
                LoggingEvent loggingEvent = (LoggingEvent) mEvent.getMessage();
                if (loggingEvent == null) {
                    logger.debug("Parsed partial event, event will be generated when " +
                            "rest of the event is received.");
                    continue;
                }
                try {
                    IPersistentMap logMap = RT.map(
                            Symbol.create(":date"), loggingEvent.getTimeStamp(),
                            Symbol.create(":NDC"), loggingEvent.getNDC()
                    );
                    Event e = EventBuilder.withBody(RT.printString(logMap), StandardCharsets.UTF_8);
                    getChannelProcessor().processEvent(e);
                    counterGroup.incrementAndGet("events.success");
                } catch (ChannelException ex) {
                    counterGroup.incrementAndGet("events.dropped");
                    logger.error("Error writting to channel, event dropped", ex);
                }
            }

        }
    }

    @Override
    public void start() {
        ChannelFactory factory = new NioServerSocketChannelFactory(
                Executors.newCachedThreadPool(), Executors.newCachedThreadPool());

        ServerBootstrap serverBootstrap = new ServerBootstrap(factory);
        serverBootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() {
                return Channels.pipeline(
                        new ObjectDecoder(ClassResolvers.softCachingResolver(Thread.currentThread().getContextClassLoader())),
                        new Log4jSocketHandler());
            }
        });

        logger.info("Log4j socket Source starting...");

        if (host == null) {
            nettyChannel = serverBootstrap.bind(new InetSocketAddress(port));
        } else {
            nettyChannel = serverBootstrap.bind(new InetSocketAddress(host, port));
        }

        super.start();
    }

    @Override
    public void stop() {
        logger.info("Log4j Socket Source stopping...");
        logger.info("Metrics:{}", counterGroup);

        if (nettyChannel != null) {
            nettyChannel.close();
            try {
                nettyChannel.getCloseFuture().await(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.warn("netty server stop interrupted", e);
            } finally {
                nettyChannel = null;
            }
        }

        super.stop();
    }

    @Override
    public void configure(Context context) {
        Configurables.ensureRequiredNonNull(context, "port");
        port = context.getInteger("port");
        host = context.getString("host");
    }
}
