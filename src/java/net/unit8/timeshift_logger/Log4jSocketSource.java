package net.unit8.timeshift_logger;

import org.apache.flume.Context;
import org.apache.flume.CounterGroup;
import org.apache.flume.EventDrivenSource;
import org.apache.flume.conf.Configurable;
import org.apache.flume.conf.Configurables;
import org.apache.flume.event.EventBuilder;
import org.apache.flume.source.AbstractSource;
import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.serialization.CompatibleObjectDecoder;
import org.jboss.netty.handler.codec.serialization.ObjectDecoderInputStream;
import org.jboss.netty.handler.codec.serialization.ObjectEncoderOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Source for Log4j SocketAppender.
 *
 * @author kawasima
 */
public class Log4jSocketSource extends AbstractSource implements EventDrivenSource, Configurable{
    private static final Logger logger = LoggerFactory
            .getLogger(Log4jSocketSource.class);

    /** Log server host */
    private String host = null;

    /** Log server port */
    private int port;

    /** Redis host */
    private String redisHost = null;

    /** Redis port */
    private int redisPort;

    /** redis database number */
    private int redisDb;

    /** The retention seconds */
    private int retentionSeconds;

    /** Log level threshold */
    private Level thresholdLevel;

    /** redis client */
    private Jedis jedis;
    private Channel nettyChannel;
    private CounterGroup counterGroup = new CounterGroup();

    /**
     * The Channel handler for log4j SocketAppender.
     */
    public class Log4jSocketHandler extends SimpleChannelHandler {
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
            if (!(e.getCause() instanceof EOFException)) {
                logger.error("Something wrong.", e.getCause());
            }
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent mEvent) {
            LoggingEvent loggingEvent = (LoggingEvent) mEvent.getMessage();
            if (loggingEvent == null) {
                logger.debug("Parsed partial event, event will be generated when " +
                        "rest of the event is received.");
                return;
            }
            try {
                String userId = loggingEvent.getNDC();
                ByteArrayOutputStream baos = new ByteArrayOutputStream(2048);
                ObjectEncoderOutputStream oeos = new ObjectEncoderOutputStream(baos);
                if (userId != null) {
                    byte[] cached = jedis.get(userId.getBytes());
                    List<LoggingEvent> histories = null;
                    if (cached != null) {
                        ObjectDecoderInputStream in = new ObjectDecoderInputStream(new ByteArrayInputStream(cached));
                        histories = (List<LoggingEvent>) in.readObject();
                    }
                    if (histories == null)
                        histories = new ArrayList<LoggingEvent>();
                    histories.add(loggingEvent);
                    oeos.writeObject(histories);
                    if (loggingEvent.getLevel().isGreaterOrEqual(thresholdLevel)) {
                        getChannelProcessor().processEvent(EventBuilder.withBody(baos.toByteArray()));
                        jedis.del(userId.getBytes());
                    } else {
                        jedis.set(userId.getBytes(), baos.toByteArray());
                        jedis.expire(userId.getBytes(), retentionSeconds);
                    }
                }
                counterGroup.incrementAndGet("events.success");
            } catch (Exception ex) {
                counterGroup.incrementAndGet("events.dropped");
                logger.error("Error writting to channel, event dropped", ex);
            }
        }

    }

    @Override
    public void start() {
        jedis = new Jedis(redisHost, redisPort, redisDb);
        ChannelFactory factory = new NioServerSocketChannelFactory(
                Executors.newCachedThreadPool(), Executors.newCachedThreadPool());

        ServerBootstrap serverBootstrap = new ServerBootstrap(factory);
        serverBootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                return Channels.pipeline(
                        new CompatibleObjectDecoder(),
                        new Log4jSocketHandler()
                );
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

        redisHost = context.getString("redisHost", "localhost");
        redisPort = context.getInteger("redisPort", 6379);
        redisDb   = context.getInteger("redisDb", 8);

        retentionSeconds = context.getInteger("retentionSeconds", 900);

        thresholdLevel = Level.toLevel(context.getString("thresholdLevel"), Level.ERROR);
    }
}
