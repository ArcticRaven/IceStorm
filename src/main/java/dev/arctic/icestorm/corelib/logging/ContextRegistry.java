package dev.arctic.icestorm.corelib.logging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registry for {@link Context} definitions and runtime enable/disable state.
 *
 * <p>Contexts are registered in batches by plugins during startup. Registration is locked after
 * server operational start begins.</p>
 */
public final class ContextRegistry {

    private ContextRegistry() {}

    private static final AtomicBoolean LOCKED = new AtomicBoolean(false);

    private static final Map<String, Context> CONTEXTS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicBoolean> ENABLED = new ConcurrentHashMap<>();

    /**
     * Prevents further context registration.
     */
    public static void lock() {
        LOCKED.set(true);
    }

    /**
     * Registers a batch of contexts for a plugin.
     *
     * <p>New contexts are enabled by default when either:</p>
     * <ul>
     *   <li>{@link ContextType#PLUGIN} (always enabled)</li>
     *   <li>{@link Context#doEnable} is true</li>
     * </ul>
     *
     * @param contexts contexts to register
     * @throws IllegalStateException if called after {@link #lock()}
     * @throws IllegalArgumentException if contexts is null or contains null entries
     */
    public static void registerAll(Set<Context> contexts) {
        if (LOCKED.get()) {
            throw new IllegalStateException("Context registry is locked.");
        }
        if (contexts == null) {
            throw new IllegalArgumentException("contexts cannot be null.");
        }

        for (Context ctx : contexts) {
            if (ctx == null) {
                throw new IllegalArgumentException("contexts cannot contain null entries.");
            }

            String id = ctx.id();

            // Keep the first registered definition; do not overwrite.
            Context existing = CONTEXTS.putIfAbsent(id, ctx);

            // Only set default enabled state on first registration.
            if (existing == null) {
                boolean defaultEnabled = (ctx.type == ContextType.PLUGIN) || ctx.doEnable;
                ENABLED.put(id, new AtomicBoolean(defaultEnabled));
            } else {
                // Ensure there is a flag even if a previous version registered contexts differently.
                ENABLED.putIfAbsent(id, new AtomicBoolean(false));
            }
        }
    }

    /**
     * Enables a registered context.
     *
     * @param contextId {@code namespace:value}
     * @return true if found and enabled
     */
    public static boolean enable(String contextId) {
        String id = normalizeId(contextId);
        AtomicBoolean flag = ENABLED.get(id);
        if (flag == null) {
            return false;
        }
        flag.set(true);
        return true;
    }

    /**
     * Disables a registered context.
     *
     * @param contextId {@code namespace:value}
     * @return true if found and disabled
     */
    public static boolean disable(String contextId) {
        String id = normalizeId(contextId);
        AtomicBoolean flag = ENABLED.get(id);
        if (flag == null) {
            return false;
        }
        flag.set(false);
        return true;
    }

    /**
     * @param contextId {@code namespace:value}
     * @return true if the context exists and is enabled
     */
    public static boolean isEnabled(String contextId) {
        String id = normalizeId(contextId);
        AtomicBoolean flag = ENABLED.get(id);
        return flag != null && flag.get();
    }

    /**
     * Returns the registered {@link Context} for the given id.
     *
     * @param contextId {@code namespace:value}
     * @return the context, or null if not registered
     */
    public static Context get(String contextId) {
        return CONTEXTS.get(normalizeId(contextId));
    }

    /**
     * Lists all registered context ids.
     *
     * @return immutable sorted list of ids
     */
    public static java.util.List<String> listIds() {
        ArrayList<String> out = new ArrayList<>(CONTEXTS.keySet());
        out.sort(Comparator.naturalOrder());
        return Collections.unmodifiableList(out);
    }

    private static String normalizeId(String contextId) {
        if (contextId == null) {
            throw new IllegalArgumentException("contextId cannot be null.");
        }
        String normalized = contextId.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.indexOf(':') < 0) {
            throw new IllegalArgumentException("contextId must be in the form namespace:value.");
        }
        return normalized;
    }
}
