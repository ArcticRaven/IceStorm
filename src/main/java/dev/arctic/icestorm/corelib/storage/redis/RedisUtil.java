package dev.arctic.icestorm.corelib.storage.redis;

import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.params.XAddParams;

import java.time.Duration;
import java.util.Map;

/**
 * Static Redis helpers built on Jedis 7.2+.
 *
 * <p>Uses {@link RedisClient} for pooled commands. Uses a dedicated {@link Jedis}
 * connection for Pub/Sub because subscribe blocks.</p>
 */
public final class RedisUtil {

    private static RedisClient client;
    private static String host;
    private static int port;
    private static String password;

    private RedisUtil() {}

    /**
     * Initializes the shared pooled client.
     *
     * @param redisHost Redis host / IP
     * @param redisPort Redis port (usually 6379)
     * @param redisPassword Redis password
     */
    public static void init(String redisHost, int redisPort, String redisPassword) {
        if (client != null) {
            throw new IllegalStateException("RedisUtil already initialized.");
        }

        host = redisHost;
        port = redisPort;
        password = redisPassword;

        ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
        poolConfig.setMaxTotal(8);
        poolConfig.setMaxIdle(8);
        poolConfig.setMinIdle(1);
        poolConfig.setBlockWhenExhausted(true);
        poolConfig.setMaxWait(Duration.ofSeconds(1));
        poolConfig.setTestWhileIdle(true);
        poolConfig.setTimeBetweenEvictionRuns(Duration.ofSeconds(1));
        poolConfig.setMinEvictableIdleDuration(Duration.ofMinutes(1));

        var endpoint = new HostAndPort(host, port);

        var clientConfig = DefaultJedisClientConfig.builder()
                .password(password)
                .build();

        client = RedisClient.builder()
                .hostAndPort(endpoint)
                .poolConfig(poolConfig)
                .clientConfig(clientConfig)
                .build();
    }

    /**
     * Closes the shared pooled client.
     */
    public static void shutdown() {
        if (client != null) {
            client.close();
        }
    }

    /**
     * @return pooled Redis client for normal commands
     */
    public static RedisClient client() {
        if (client == null) {
            throw new IllegalStateException("RedisUtil not initialized. Call RedisUtil.init(...) first.");
        }
        return client;
    }

    /**
     * Creates a dedicated connection for Pub/Sub.
     *
     * @return Jedis connection (caller must close)
     */
    public static Jedis newPubSubConnection() {
        if (host == null) {
            throw new IllegalStateException("RedisUtil not initialized. Call RedisUtil.init(...) first.");
        }
        Jedis jedis = new Jedis(host, port);
        jedis.auth(password);
        return jedis;
    }

    public static void publish(String channel, String message) {
        client().publish(channel, message);
    }

    public static String get(String key) {
        return client().get(key);
    }

    public static void set(String key, String value) {
        client().set(key, value);
    }

    /**
     * Appends to a Redis stream (durable event log).
     *
     * @param stream stream key
     * @param values field map
     * @return entry id as a string (e.g. "1700000000000-0")
     */
    public static String xadd(String stream, Map<String, String> values) {
        XAddParams params = XAddParams.xAddParams(); // default behaves like "*" (server-generated ID)
        StreamEntryID id = client().xadd(stream, values, params);
        return id.toString();
    }
}
