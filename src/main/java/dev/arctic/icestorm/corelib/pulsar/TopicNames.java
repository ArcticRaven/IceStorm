package dev.arctic.icestorm.corelib.pulsar;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TopicNames {

    private static final Pattern SAFE_SEGMENT =
            Pattern.compile("[a-zA-Z0-9._-]+");

    public static String persistent(String tenant, String namespace, String topic) {
        return build(true, tenant, namespace, topic);
    }

    public static String nonPersistent(String tenant, String namespace, String topic) {
        return build(false, tenant, namespace, topic);
    }

    public static String moduleTopic(
            String tenant,
            String namespace,
            String module,
            String name
    ) {
        requireSegment(module, "module");
        requireSegment(name, "name");
        return persistent(tenant, namespace, module + "." + name);
    }

    public static String withSuffix(String fullTopic, String suffix) {
        Objects.requireNonNull(fullTopic, "fullTopic");
        requireSegment(suffix, "suffix");
        return fullTopic + "." + suffix;
    }

    private static String build(
            boolean persistent,
            String tenant,
            String namespace,
            String topic
    ) {
        requireSegment(tenant, "tenant");
        requireSegment(namespace, "namespace");
        requireTopic(topic);

        return (persistent ? "persistent://" : "non-persistent://")
                + tenant + "/" + namespace + "/" + topic;
    }

    private static void requireSegment(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank() || !SAFE_SEGMENT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
    }

    private static void requireTopic(String topic) {
        Objects.requireNonNull(topic, "topic");
        if (topic.isBlank()) {
            throw new IllegalArgumentException("Invalid topic: blank");
        }

        for (String part : topic.split("\\.")) {
            requireSegment(part, "topic segment");
        }
    }
}
