package dev.arctic.icestorm.corelib.placeholder;

import lombok.NonNull;

/**
 * Typed key used to store and retrieve values from a {@link PlaceholderContext}.
 *
 * <p>Keys are intended to be declared as static singletons (e.g. in a Keys class)
 * and reused everywhere, not constructed ad-hoc.</p>
 */
public record ContextKey<T>(@NonNull String name, @NonNull Class<T> type) {

    T cast(Object value) {
        return type.cast(value);
    }
}
