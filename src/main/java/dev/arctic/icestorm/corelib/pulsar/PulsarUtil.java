package dev.arctic.icestorm.corelib.pulsar;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Slf4j
public final class PulsarUtil implements AutoCloseable {

    @Getter
    private final PulsarConfig config;

    private final PulsarClient client;
    private final ExecutorService executor;
    private final ProducerCache producerCache = new ProducerCache();

    public PulsarUtil(@NonNull PulsarConfig config) {
        this.config = config;

        this.executor = config.getExecutor() != null
                ? config.getExecutor()
                : Executors.newVirtualThreadPerTaskExecutor();

        try {
            this.client = PulsarClient.builder()
                    .serviceUrl(config.getServiceUrl())
                    .authentication(config.getAuthentication())
                    .ioThreads(config.getIoThreads())
                    .listenerThreads(config.getListenerThreads())
                    .operationTimeout(toIntMillis(config.getOperationTimeout()), TimeUnit.MILLISECONDS)
                    .build();
        } catch (PulsarClientException exception) {
            throw new IllegalStateException(
                    "Failed to initialize Pulsar client for module=" + config.getModuleName(),
                    exception
            );
        }
    }

    // ---------- Publish ----------

    public MessageId publish(String topic, byte[] payload) {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(payload, "payload");

        try {
            Producer<byte[]> producer =
                    producerCache.getOrCreate(topic, () -> newProducer(topic));

            Map<String, String> properties = mapOrEmpty(config.getDefaultProperties());

            return producer.newMessage()
                    .properties(properties)
                    .value(payload)
                    .send();

        } catch (Exception exception) {
            throw new RuntimeException("Publish failed topic=" + topic, exception);
        }
    }

    public CompletableFuture<MessageId> publishAsync(String topic, byte[] payload) {
        return CompletableFuture.supplyAsync(() -> publish(topic, payload), executor);
    }

    public MessageId publishText(String topic, String text) {
        Objects.requireNonNull(text, "text");
        return publish(topic, text.getBytes(StandardCharsets.UTF_8));
    }

    public CompletableFuture<MessageId> publishTextAsync(String topic, String text) {
        return CompletableFuture.supplyAsync(() -> publishText(topic, text), executor);
    }

    // ---------- Subscribe ----------

    public SubscriptionHandle subscribe(
            String topic,
            String subscription,
            Consumer<Message<byte[]>> handler
    ) {
        Objects.requireNonNull(handler, "handler");
        return subscribe(topic, subscription, (pulsarConsumer, message) -> handler.accept(message), null);
    }

    public SubscriptionHandle subscribe(
            String topic,
            String subscription,
            BiConsumer<org.apache.pulsar.client.api.Consumer<byte[]>, Message<byte[]>> handler,
            Consumer<Throwable> onError
    ) {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(subscription, "subscription");
        Objects.requireNonNull(handler, "handler");

        try {
            org.apache.pulsar.client.api.Consumer<byte[]> pulsarConsumer = client.newConsumer()
                    .topic(topic)
                    .subscriptionName(subscription)
                    .subscriptionType(config.getSubscriptionType())
                    .subscriptionInitialPosition(config.getInitialPosition())
                    .receiverQueueSize(config.getReceiverQueueSize())
                    .subscribe();

            AtomicBoolean running = new AtomicBoolean(true);

            CompletableFuture<Void> loop = CompletableFuture.runAsync(() -> {
                while (running.get()) {
                    try {
                        Message<byte[]> message = pulsarConsumer.receive(
                                toIntMillis(config.getReceiveTimeout()),
                                TimeUnit.MILLISECONDS
                        );

                        if (message != null) {
                            handler.accept(pulsarConsumer, message);
                        }
                    } catch (Throwable throwable) {
                        if (onError != null) {
                            onError.accept(throwable);
                        } else {
                            log.warn("Consume error topic={} sub={}", topic, subscription, throwable);
                        }
                    }
                }
            }, executor);

            return new SubscriptionHandle(topic, subscription, pulsarConsumer, running, loop);

        } catch (PulsarClientException exception) {
            throw new RuntimeException("Subscribe failed topic=" + topic, exception);
        }
    }

    public SubscriptionHandle subscribeAutoAck(
            String topic,
            String subscription,
            Consumer<Message<byte[]>> handler
    ) {
        Objects.requireNonNull(handler, "handler");

        return subscribe(topic, subscription, (pulsarConsumer, message) -> {
            handler.accept(message);
            try {
                pulsarConsumer.acknowledge(message);
            } catch (PulsarClientException exception) {
                throw new RuntimeException("Ack failed topic=" + topic + " sub=" + subscription, exception);
            }
        }, null);
    }

    // ---------- Lifecycle ----------

    @Override
    public void close() {
        producerCache.closeAll();

        try {
            client.close();
        } catch (PulsarClientException exception) {
            log.warn("Failed closing Pulsar client", exception);
        }

        if (config.getExecutor() == null) {
            executor.shutdown();
        }
    }

    // ---------- Internals ----------

    private Producer<byte[]> newProducer(String topic) throws PulsarClientException {
        return client.newProducer()
                .topic(topic)
                .enableBatching(config.isEnableBatching())
                .batchingMaxMessages(config.getBatchingMaxMessages())
                .sendTimeout(toIntMillis(config.getSendTimeout()), TimeUnit.MILLISECONDS)
                .compressionType(config.getCompressionType())
                .blockIfQueueFull(true)
                .create();
    }

    public record SubscriptionHandle(
            String topic,
            String subscription,
            org.apache.pulsar.client.api.Consumer<byte[]> consumer,
            AtomicBoolean running,
            CompletableFuture<Void> loop
    ) implements AutoCloseable {

        @Override
        public void close() {
            running.set(false);
            loop.cancel(true);

            try {
                consumer.close();
            } catch (PulsarClientException ignored) {
            }
        }
    }

    private static final class ProducerCache {
        private final ConcurrentHashMap<String, Producer<byte[]>> producers = new ConcurrentHashMap<>();

        Producer<byte[]> getOrCreate(String topic, Callable<Producer<byte[]>> factory)
                throws Exception {

            Producer<byte[]> existing = producers.get(topic);
            if (existing != null) {
                return existing;
            }

            Producer<byte[]> created = factory.call();
            Producer<byte[]> raced = producers.putIfAbsent(topic, created);

            if (raced != null) {
                created.close();
                return raced;
            }
            return created;
        }

        void closeAll() {
            for (Producer<byte[]> producer : producers.values()) {
                try {
                    producer.close();
                } catch (PulsarClientException ignored) {
                }
            }
            producers.clear();
        }
    }

    private static int toIntMillis(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        long millis = duration.toMillis();
        if (millis <= 0) {
            return 1;
        }
        if (millis > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) millis;
    }

    private static Map<String, String> mapOrEmpty(Map<String, String> map) {
        return map == null ? Map.of() : map;
    }
}
