package dev.arctic.icestorm.corelib.logging;

import java.util.Locale;
import java.util.Objects;

/**
 * Represents a logging context registered by a plugin.
 *
 * <p>A context is identified by {@code namespace:value}. Both fields are normalized to lowercase
 * for stable matching and command usage.</p>
 */
public final class Context {

    public final String namespace;
    public final String value;
    public final ContextType type;

    /**
     * Indicates whether the context should be enabled by default.
     *
     * <p>This flag is ignored for {@link ContextType#PLUGIN} contexts, which are always enabled by default.</p>
     */
    public final boolean doEnable;

    public Context(String namespace, String value, ContextType type, boolean doEnable) {
        this.namespace = normalizePart(namespace, "namespace");
        this.value = normalizePart(value, "value");
        this.type = Objects.requireNonNull(type, "type");
        this.doEnable = doEnable;
    }

    /**
     * @return a stable context id in the form {@code namespace:value}
     */
    public String id() {
        return namespace + ":" + value;
    }

    private static String normalizePart(String input, String fieldName) {
        if (input == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null.");
        }

        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }
        if (normalized.indexOf(':') >= 0) {
            throw new IllegalArgumentException(fieldName + " cannot contain ':'.");
        }

        return normalized;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Context other)) {
            return false;
        }
        return namespace.equals(other.namespace) && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, value);
    }

    @Override
    public String toString() {
        return "Context{id=" + id() + ", type=" + type + "}";
    }
}
