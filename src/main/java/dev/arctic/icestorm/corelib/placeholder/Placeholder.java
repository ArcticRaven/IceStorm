package dev.arctic.icestorm.corelib.placeholder;

import lombok.NonNull;

/**
 * Represents a single resolvable placeholder within the IceStorm placeholder ecosystem.
 *
 * <p>Placeholders are registered under a namespace and always resolve to a plain string.</p>
 *
 * <p>Implementations must never return {@code null}. Return {@code ""} if unresolved.</p>
 */
public interface Placeholder {

    /**
     * The unqualified placeholder key.
     *
     * <p>Example: {@code "town_name"} registered as {@code "frosttowns:town_name"}.</p>
     */
    @NonNull
    String key();

    /**
     * Resolves this placeholder using the provided request and available contexts.
     *
     * @param request the parsed placeholder request (key, context tag, args)
     * @param contextSet the available named placeholder contexts
     * @return the resolved string value, or {@code ""} if unresolved
     */
    @NonNull
    String resolve(@NonNull PlaceholderRequest request, @NonNull ContextSet contextSet);
}
