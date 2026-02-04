package dev.arctic.icestorm.corelib.placeholder;

import java.util.List;

public record PlaceholderRequest(
        String qualifiedKey,
        String contextName,
        List<String> args,
        String rawToken
) {

    public PlaceholderRequest {
        if (qualifiedKey == null || qualifiedKey.isBlank()) {
            throw new IllegalArgumentException("qualifiedKey cannot be null/blank");
        }

        if (contextName == null || contextName.isBlank()) {
            contextName = ContextSet.DEFAULT_CONTEXT;
        }

        args = (args == null) ? List.of() : List.copyOf(args);
        rawToken = (rawToken == null) ? "" : rawToken;
    }
}
