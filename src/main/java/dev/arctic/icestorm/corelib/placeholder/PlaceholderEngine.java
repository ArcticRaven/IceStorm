package dev.arctic.icestorm.corelib.placeholder;

import lombok.NonNull;

import java.util.List;
import java.util.Locale;

/**
 * Passive placeholder resolver used by {@code ParseUtil}.
 *
 * <p>This engine does not parse tag syntax. It resolves already-identified placeholder tokens.</p>
 */
public final class PlaceholderEngine {

    private final PlaceholderRegistry registry;
    private final String defaultNamespace;

    public PlaceholderEngine(@NonNull PlaceholderRegistry registry, @NonNull String defaultNamespace) {
        this.registry = registry;
        this.defaultNamespace = normalizeNamespace(defaultNamespace);
    }

    /**
     * Resolves a placeholder to a plain string.
     *
     * <p>If {@code keyOrQualifiedKey} is not namespaced, {@code defaultNamespace} is applied.</p>
     * <p>If {@code contextTag} is null/blank, {@link ContextSet#DEFAULT_CONTEXT} is used.</p>
     * <p>If the placeholder is unknown, {@code rawToken} is returned if non-null; otherwise "".</p>
     *
     * @return resolved string (never null)
     */
    public String resolve(String keyOrQualifiedKey,
                          String contextTag,
                          List<String> args,
                          String rawToken,
                          @NonNull ContextSet contextSet) {

        String qualifiedKey = qualify(keyOrQualifiedKey);
        if (qualifiedKey.isEmpty()) {
            return rawToken == null ? "" : rawToken;
        }

        Placeholder placeholder = registry.get(qualifiedKey);
        if (placeholder == null) {
            return rawToken == null ? "" : rawToken;
        }

        String ctx = normalizeContextName(contextTag);
        List<String> safeArgs = (args == null) ? List.of() : List.copyOf(args);
        String safeRaw = (rawToken == null) ? "" : rawToken;

        PlaceholderRequest request = new PlaceholderRequest(
                qualifiedKey,
                ctx,
                safeArgs,
                safeRaw
        );

        // Contract says "never null", but we guard anyway.
        String resolved = placeholder.resolve(request, contextSet);
        return resolved == null ? "" : resolved;
    }

    private String qualify(String keyOrQualifiedKey) {
        if (keyOrQualifiedKey == null || keyOrQualifiedKey.isBlank()) {
            return "";
        }

        String k = keyOrQualifiedKey.trim().toLowerCase(Locale.ROOT);
        if (k.indexOf(':') < 0) {
            return defaultNamespace + ":" + k;
        }
        return k;
    }

    private static String normalizeNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("defaultNamespace cannot be null/blank");
        }
        return namespace.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeContextName(String contextTag) {
        if (contextTag == null || contextTag.isBlank()) {
            return ContextSet.DEFAULT_CONTEXT;
        }
        return contextTag.trim().toLowerCase(Locale.ROOT);
    }
}
