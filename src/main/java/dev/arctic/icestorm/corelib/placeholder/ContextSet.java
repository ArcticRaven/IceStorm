package dev.arctic.icestorm.corelib.placeholder;

import lombok.NonNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A named collection of {@link PlaceholderContext} objects.
 *
 * <p>Context names are case-insensitive and normalized to lowercase.</p>
 *
 * <p>The reserved context name {@code "default"} is used as the fallback context.</p>
 */
public final class ContextSet {

    public static final String DEFAULT_CONTEXT = "default";

    private final Map<String, PlaceholderContext> contexts;

    public ContextSet(@NonNull Map<String, PlaceholderContext> contexts) {
        Map<String, PlaceholderContext> normalized = new HashMap<>();

        for (Map.Entry<String, PlaceholderContext> entry : contexts.entrySet()) {
            String key = entry.getKey();
            PlaceholderContext value = entry.getValue();

            if (key == null || key.isBlank() || value == null) {
                continue;
            }

            normalized.put(key.toLowerCase(Locale.ROOT), value);
        }

        this.contexts = Map.copyOf(normalized);
    }

    public Optional<PlaceholderContext> get(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(contexts.get(name.toLowerCase(Locale.ROOT)));
    }

    public Optional<PlaceholderContext> getDefault() {
        return Optional.ofNullable(contexts.get(DEFAULT_CONTEXT));
    }
}
