package dev.arctic.icestorm.corelib.placeholder;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A typed, immutable bag of values used during placeholder resolution.
 *
 * <p>Values are stored by {@link ContextKey} instances. Keys should be reused (static singletons)
 * rather than created ad-hoc.</p>
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class PlaceholderContext {

    private final Map<ContextKey<?>, Object> values;

    public <T> Optional<T> get(ContextKey<T> key) {
        if (key == null) {
            return Optional.empty();
        }

        Object value = values.get(key);
        if (value == null) {
            return Optional.empty();
        }

        return Optional.of(key.cast(value));
    }

    public <T> T getOrThrow(@NonNull ContextKey<T> key) {
        Object value = values.get(key);
        if (value == null) {
            throw new IllegalStateException("Missing context key: " + key.name());
        }
        return key.cast(value);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final Map<ContextKey<?>, Object> values = new HashMap<>();

        public <T> Builder put(@NonNull ContextKey<T> key, T value) {
            if (value != null) {
                values.put(key, value);
            }
            return this;
        }

        public PlaceholderContext build() {
            if (values.isEmpty()) {
                return new PlaceholderContext(Map.of());
            }
            return new PlaceholderContext(Map.copyOf(values));
        }
    }
}
