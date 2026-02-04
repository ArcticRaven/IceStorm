package dev.arctic.icestorm.corelib.logging;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Centralized logging utility for IceStorm CoreLib.
 *
 * <p>Plugins emit log output by providing only a {@link Context} and a message string.
 * Context enablement is controlled by {@link ContextRegistry}.</p>
 *
 * <p>All output is written through {@link Logger#info(String)} to avoid severity-based
 * console formatting overrides. Visual distinction is handled purely through ANSI colors.</p>
 */
public final class LogUtil {

    private LogUtil() {}

    private static final String ANSI_RESET = "\u001B[0m";

    // ANSI 256-color foreground sequences (static map).
    // Theme:
    // - Non-warning/error: blue scale
    // - Warning: NASA amber
    // - Error: bright red
    private static final String ANSI_PLUGIN = fg256(75);
    private static final String ANSI_INFO = fg256(81);
    private static final String ANSI_DEBUG = fg256(111);
    private static final String ANSI_CUSTOM = fg256(69);
    private static final String ANSI_WARNING = fg256(214);
    private static final String ANSI_ERROR = fg256(196);

    private static final Logger LOGGER = Logger.getLogger("IceStorm");

    /**
     * Logs a message using the provided context.
     *
     * <p>If the context is not enabled in {@link ContextRegistry}, this call is a no-op.</p>
     *
     * @param context the context associated with the log message
     * @param message the message to log
     */
    public static void log(Context context, String message) {
        Objects.requireNonNull(context, "context");

        if (message == null || message.isEmpty()) {
            return;
        }

        String contextId = context.id();
        if (!ContextRegistry.isEnabled(contextId)) {
            return;
        }

        String color = resolveAnsiColor(context);
        String prefix = "[" + contextId + "]";

        LOGGER.info(color + prefix + ANSI_RESET + " " + message);
    }

    private static String resolveAnsiColor(Context context) {
        return switch (context.type) {
            case PLUGIN -> ANSI_PLUGIN;
            case INFO -> ANSI_INFO;
            case DEBUG -> ANSI_DEBUG;
            case CUSTOM -> ANSI_CUSTOM;
            case WARNING -> ANSI_WARNING;
            case ERROR -> ANSI_ERROR;
        };
    }

    private static String fg256(int color) {
        return "\u001B[38;5;" + color + "m";
    }
}
