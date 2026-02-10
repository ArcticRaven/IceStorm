package dev.arctic.icestorm.corelib.storage.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.params.XReadGroupParams;
import redis.clients.jedis.resps.StreamEntry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime Redis manager (instance-based).
 *
 * <p>Manages:</p>
 * <ul>
 *   <li>Pub/Sub listeners (live signals, not durable)</li>
 *   <li>Stream consumers (durable event processing)</li>
 * </ul>
 *
 * <p>Notes:</p>
 * <ul>
 *   <li>Pub/Sub uses a dedicated {@link Jedis} connection per subscription thread because {@code subscribe()} blocks.</li>
 *   <li>Streams use consumer groups. The ID {@code ">"} reads only new (unclaimed) messages.</li>
 * </ul>
 */
public final class RedisService {

    /**
     * Handler for Pub/Sub messages.
     */
    public interface MessageHandler {
        void onMessage(String channel, String message);
    }

    /**
     * Handler for Stream entries.
     */
    public interface StreamHandler {
        void onEntry(String stream, StreamEntryID id, Map<String, String> fields);
    }

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final Map<String, ActiveSubscription> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, ActiveStreamConsumer> consumers = new ConcurrentHashMap<>();

    /**
     * Starts this service instance.
     *
     * @throws IllegalStateException if {@link RedisUtil#init(String, int, String)} was not called
     */
    public void start() {
        RedisUtil.client();
        running.set(true);
    }

    /**
     * Stops all listeners/consumers managed by this instance.
     */
    public void shutdown() {
        running.set(false);

        for (ActiveSubscription subscription : subscriptions.values()) {
            subscription.stop();
        }
        subscriptions.clear();

        for (ActiveStreamConsumer consumer : consumers.values()) {
            consumer.stop();
        }
        consumers.clear();
    }

    /**
     * Subscribes to a Pub/Sub channel.
     *
     * @param channel channel name
     * @param handler message handler
     */
    public void subscribe(String channel, MessageHandler handler) {
        if (!running.get()) {
            throw new IllegalStateException("RedisService not started. Call start() first.");
        }
        subscriptions.computeIfAbsent(channel, ch -> startSubscription(ch, handler));
    }

    /**
     * Unsubscribes from a Pub/Sub channel.
     *
     * @param channel channel name
     */
    public void unsubscribe(String channel) {
        ActiveSubscription subscription = subscriptions.remove(channel);
        if (subscription != null) {
            subscription.stop();
        }
    }

    /**
     * Starts a durable stream consumer using a Redis consumer group.
     *
     * <p>This reads only new messages (">") and acks after your handler runs.</p>
     *
     * @param stream stream key
     * @param group consumer group name
     * @param consumerName consumer name (e.g. "mc-01", "bot-01")
     * @param handler entry handler
     */
    public void startStreamConsumer(String stream, String group, String consumerName, StreamHandler handler) {
        if (!running.get()) {
            throw new IllegalStateException("RedisService not started. Call start() first.");
        }
        String key = stream + "|" + group + "|" + consumerName;
        consumers.computeIfAbsent(key, k -> startStreamLoop(stream, group, consumerName, handler));
    }

    /**
     * Stops a stream consumer.
     *
     * @param stream stream key
     * @param group group name
     * @param consumerName consumer name
     */
    public void stopStreamConsumer(String stream, String group, String consumerName) {
        String key = stream + "|" + group + "|" + consumerName;
        ActiveStreamConsumer consumer = consumers.remove(key);
        if (consumer != null) {
            consumer.stop();
        }
    }

    private ActiveSubscription startSubscription(String channel, MessageHandler handler) {
        AtomicBoolean subRunning = new AtomicBoolean(true);

        JedisPubSub pubSub = new JedisPubSub() {
            @Override
            public void onMessage(String ch, String msg) {
                if (running.get() && subRunning.get()) {
                    handler.onMessage(ch, msg);
                }
            }
        };

        Thread thread = new Thread(() -> {
            while (running.get() && subRunning.get()) {
                try (Jedis jedis = RedisUtil.newPubSubConnection()) {
                    jedis.subscribe(pubSub, channel);
                } catch (Exception ignored) {
                    sleepQuiet(500L);
                }
            }
        }, "redis-sub-" + channel);

        thread.setDaemon(true);
        thread.start();

        return new ActiveSubscription(subRunning, pubSub);
    }

    private ActiveStreamConsumer startStreamLoop(String stream, String group, String consumerName, StreamHandler handler) {
        AtomicBoolean loopRunning = new AtomicBoolean(true);

        Thread thread = new Thread(() -> {
            ensureGroupExists(stream, group);

            while (running.get() && loopRunning.get()) {
                try {
                    XReadGroupParams params = XReadGroupParams.xReadGroupParams()
                            .count(50)
                            .block(2000);

                    Map<String, StreamEntryID> streams = Map.of(stream, new StreamEntryID(">"));

                    List<Map.Entry<String, List<StreamEntry>>> batches =
                            RedisUtil.client().xreadGroup(group, consumerName, params, streams);

                    if (batches == null || batches.isEmpty()) {
                        continue;
                    }

                    for (Map.Entry<String, List<StreamEntry>> batch : batches) {
                        String batchStream = batch.getKey();
                        for (StreamEntry entry : batch.getValue()) {
                            handler.onEntry(batchStream, entry.getID(), entry.getFields());
                            RedisUtil.client().xack(batchStream, group, entry.getID());
                        }
                    }
                } catch (Exception ignored) {
                    sleepQuiet(500L);
                }
            }
        }, "redis-stream-" + stream + "-" + group + "-" + consumerName);

        thread.setDaemon(true);
        thread.start();

        return new ActiveStreamConsumer(loopRunning);
    }

    /**
     * Ensures a consumer group exists for a stream.
     *
     * <p>Uses {@code mkstream=true} so Redis creates the stream if it does not exist.</p>
     *
     * <p>Start ID choices:</p>
     * <ul>
     *   <li>{@code "$"} = only new messages from now on</li>
     *   <li>{@code "0-0"} = read from the beginning (not usually what you want for live ops)</li>
     * </ul>
     *
     * @param stream stream key
     * @param group group name
     */
    private void ensureGroupExists(String stream, String group) {
        try {
            RedisUtil.client().xgroupCreate(stream, group, new StreamEntryID("$"), true);
        } catch (Exception ignored) {
            // group exists (BUSYGROUP) or stream exists already; keep it simple
        }
    }

    private static void sleepQuiet(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class ActiveSubscription {
        private final AtomicBoolean running;
        private final JedisPubSub pubSub;

        private ActiveSubscription(AtomicBoolean running, JedisPubSub pubSub) {
            this.running = running;
            this.pubSub = pubSub;
        }

        private void stop() {
            running.set(false);
            pubSub.unsubscribe();
        }
    }

    private static final class ActiveStreamConsumer {
        private final AtomicBoolean running;

        private ActiveStreamConsumer(AtomicBoolean running) {
            this.running = running;
        }

        private void stop() {
            running.set(false);
        }
    }
}
