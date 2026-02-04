package dev.arctic.icestorm.corelib.placeholder;

import lombok.NonNull;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for all placeholders registered into the IceStorm ecosystem.
 *
 * <p>Placeholders are stored by fully-qualified key: {@code namespace:key}.</p>
 * <p>Keys are case-insensitive and normalized to lowercase.</p>
 */
public final class PlaceholderRegistry {

    private final Map<String, Placeholder> placeholders = new ConcurrentHashMap<>();

    /**
     * Registers a placeholder under the given namespace.
     *
     * @param namespace the owning plugin namespace (ex: "frosttowns")
     * @param placeholder the placeholder implementation
     */
    public void register(@NonNull String namespace, @NonNull Placeholder placeholder) {
        if (namespace.isBlank()) {
            throw new IllegalArgumentException("namespace cannot be blank");
        }

        String key = placeholder.key();
        if (key.isBlank()) {
            throw new IllegalArgumentException("placeholder key cannot be blank");
        }

        if (key.indexOf(':') >= 0) {
            throw new IllegalArgumentException(
                    "placeholder key must be unqualified (do not include ':'): " + key
            );
        }

        String qualified = normalize(namespace, key);

        Placeholder existing = placeholders.putIfAbsent(qualified, placeholder);
        if (existing != null) {
            throw new IllegalStateException("Placeholder already registered: " + qualified);
        }
    }

    /**
     * Looks up a placeholder by fully-qualified key.
     *
     * @param qualifiedKey ex: "frosttowns:town_name"
     * @return the placeholder, or null if not registered
     */
    public Placeholder get(String qualifiedKey) {
        if (qualifiedKey == null || qualifiedKey.isBlank()) {
            return null;
        }

        return placeholders.get(qualifiedKey.trim().toLowerCase(Locale.ROOT));
    }

    private String normalize(String namespace, String key) {
        return (namespace.trim() + ":" + key.trim()).toLowerCase(Locale.ROOT);
    }
}
