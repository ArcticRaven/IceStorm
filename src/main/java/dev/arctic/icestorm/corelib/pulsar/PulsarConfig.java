package dev.arctic.icestorm.corelib.pulsar;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import org.apache.pulsar.client.api.*;
import org.apache.pulsar.client.impl.auth.AuthenticationDisabled;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Getter
@Builder(toBuilder = true)
public final class PulsarConfig {

    @NonNull private final String moduleName;
    @NonNull private final String serviceUrl;

    @Builder.Default
    private final Authentication authentication = AuthenticationDisabled.INSTANCE;

    @Builder.Default private final int ioThreads = 1;
    @Builder.Default private final int listenerThreads = 1;

    @Builder.Default
    private final Duration operationTimeout = Duration.ofSeconds(15);

    @Builder.Default
    private final Duration sendTimeout = Duration.ofSeconds(5);

    @Builder.Default
    private final Duration receiveTimeout = Duration.ofMillis(250);

    @Builder.Default private final boolean enableBatching = true;
    @Builder.Default private final int batchingMaxMessages = 100;

    @Builder.Default private final int receiverQueueSize = 1000;

    @Builder.Default
    private final SubscriptionType subscriptionType = SubscriptionType.Shared;

    @Builder.Default
    private final SubscriptionInitialPosition initialPosition =
            SubscriptionInitialPosition.Latest;

    @Builder.Default
    private final CompressionType compressionType = CompressionType.LZ4;

    /**
     * Optional executor. If null, PulsarUtil creates and owns a virtual-thread executor.
     */
    private final ExecutorService executor;

    /**
     * Default properties attached to every published message.
     */
    @Singular("property")
    private final Map<String, String> defaultProperties;
}
