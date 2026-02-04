package dev.arctic.icestorm.corelib.text;

import dev.arctic.icestorm.corelib.placeholder.ContextSet;
import dev.arctic.icestorm.corelib.placeholder.PlaceholderEngine;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tag utilities used by CoreLib text parsing.
 *
 * <p>TagUtil owns all tag semantics:</p>
 * <ul>
 *   <li>Style tag handling and style stack mutation</li>
 *   <li>Placeholder token parsing + placeholder resolution via {@link PlaceholderEngine}</li>
 *   <li>Color alias registration and resolution</li>
 * </ul>
 */
public final class TagUtil {

    private TagUtil() {}

    public static final Map<String, String> COLORS = createDefaultColors();

    private static Map<String, String> createDefaultColors() {
        Map<String, String> map = new HashMap<>();
        map.put("success", "#34d399");
        map.put("info", "#60a5fa");
        map.put("warning", "#f59e0b");
        map.put("error", "#ef4444");
        map.put("white", "#ffffff");
        map.put("black", "#000000");
        map.put("gray", "#6b7280");
        return map;
    }

    public static void registerColor(String alias, String hexColor) {
        String newAlias = normalizeKey(alias);

        String resolved = resolveColor(hexColor);
        if (resolved == null || resolved.charAt(0) != '#') {
            throw new IllegalArgumentException("Invalid hex color: " + hexColor);
        }

        COLORS.put(newAlias, resolved);
    }

    public static String unregisterColor(String alias) {
        return COLORS.remove(normalizeKey(alias));
    }

    public static String resolveColor(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);

        String alias = COLORS.get(normalized);
        if (alias != null) {
            return alias;
        }

        if (normalized.charAt(0) == '#') {
            int length = normalized.length();
            if (length == 7 || length == 9) {
                return normalized;
            }
        }

        return null;
    }

    /**
     * Handles style tags and mutates the style stack.
     *
     * @return true if the tag was recognized and handled
     */
    public static boolean handleStyleTag(String tagContent, Deque<StyleState> styleStack) {
        if (tagContent == null || tagContent.isBlank()) {
            return false;
        }

        // Closing tags: </color>, </b>, </i>, </link>, etc.
        if (tagContent.charAt(0) == '/') {
            String closingName = tagContent.substring(1).trim().toLowerCase(Locale.ROOT);
            if (styleStack.size() <= 1) {
                return false;
            }

            StyleState current = styleStack.peek();
            if (current.openedByTag() != null && current.openedByTag().equals(closingName)) {
                styleStack.pop();
                return true;
            }

            return false;
        }

        String normalized = tagContent.toLowerCase(Locale.ROOT);

        if (normalized.equals("reset")) {
            styleStack.clear();
            styleStack.push(StyleState.defaultState());
            return true;
        }

        if (normalized.equals("bold") || normalized.equals("b")) {
            styleStack.push(styleStack.peek().withBold(true, normalized));
            return true;
        }

        if (normalized.equals("italic") || normalized.equals("i")) {
            styleStack.push(styleStack.peek().withItalic(true, normalized));
            return true;
        }

        if (normalized.equals("mono") || normalized.equals("monospace") || normalized.equals("code")) {
            styleStack.push(styleStack.peek().withMonospace(true, normalized));
            return true;
        }

        if (normalized.startsWith("color:")) {
            String value = normalized.substring("color:".length()).trim();
            String resolvedColor = resolveColor(value);
            if (resolvedColor == null) {
                return false;
            }
            styleStack.push(styleStack.peek().withColor(resolvedColor));
            return true;
        }

        if (normalized.startsWith("link:")) {
            String url = tagContent.substring("link:".length()).trim(); // preserve original case
            if (url.isEmpty()) {
                return false;
            }
            styleStack.push(styleStack.peek().withLink(url));
            return true;
        }

        return false;
    }

    /**
     * Attempts to resolve a placeholder tag.
     *
     * @return resolved string if this is a placeholder tag and can be resolved, otherwise null
     */
    public static String tryResolvePlaceholderTag(String tagContent,
                                                  PlaceholderEngine placeholderEngine,
                                                  ContextSet contextSet,
                                                  String defaultContextTag) {

        if (tagContent == null || tagContent.isBlank()) {
            return null;
        }

        // Only handle <ph:...>
        if (!tagContent.regionMatches(true, 0, "ph:", 0, 3)) {
            return null;
        }

        // If engine/context are missing, do not consume the tag (ParseUtil will emit literal)
        if (placeholderEngine == null || contextSet == null) {
            return null;
        }

        PlaceholderToken token = PlaceholderToken.parse(tagContent, defaultContextTag);
        if (token == null) {
            return null;
        }

        return placeholderEngine.resolve(
                token.keyOrQualifiedKey(),
                token.contextTag(),
                token.args(),
                token.rawToken(),
                contextSet
        );
    }

    /**
     * Placeholder token extracted from tag content (without the surrounding angle brackets).
     *
     * <p>Supported forms inside brackets:</p>
     * <ul>
     *   <li>{@code ph:key}</li>
     *   <li>{@code ph:namespace:key}</li>
     *   <li>{@code ph:namespace:key@context}</li>
     *   <li>{@code ph:namespace:key@context|arg1|arg2}</li>
     * </ul>
     */
    public record PlaceholderToken(
            String keyOrQualifiedKey,
            String contextTag,
            List<String> args,
            String rawToken
    ) {
        static PlaceholderToken parse(String tagContent, String defaultContextTag) {
            // rawToken includes brackets for debug/fallback
            String rawToken = "<" + tagContent + ">";

            String body = tagContent.substring(3).trim(); // after "ph:"
            if (body.isEmpty()) {
                return null;
            }

            String head = body;
            List<String> args = List.of();

            int firstPipe = body.indexOf('|');
            if (firstPipe >= 0) {
                head = body.substring(0, firstPipe).trim();
                args = splitArgs(body.substring(firstPipe + 1));
            }

            String keyPart = head;
            String contextTag = (defaultContextTag == null || defaultContextTag.isBlank())
                    ? ContextSet.DEFAULT_CONTEXT
                    : defaultContextTag;

            int at = head.indexOf('@');
            if (at >= 0) {
                keyPart = head.substring(0, at).trim();
                String ctx = head.substring(at + 1).trim();
                if (!ctx.isEmpty()) {
                    contextTag = ctx;
                }
            }

            if (keyPart.isEmpty()) {
                return null;
            }

            contextTag = contextTag.trim().toLowerCase(Locale.ROOT);

            return new PlaceholderToken(
                    keyPart,
                    contextTag,
                    args,
                    rawToken
            );
        }

        private static List<String> splitArgs(String argPart) {
            String s = argPart.trim();
            if (s.isEmpty()) {
                return List.of();
            }

            String[] pieces = s.split("\\|", -1);
            ArrayList<String> args = new ArrayList<>(pieces.length);
            for (String piece : pieces) {
                String p = piece.trim();
                if (!p.isEmpty()) {
                    args.add(p);
                }
            }
            return List.copyOf(args);
        }
    }

    /**
     * Immutable style state for nested tag parsing.
     */
    public record StyleState(
            String colorHex,
            String linkUrl,
            boolean bold,
            boolean italic,
            boolean monospace,
            String openedByTag
    ) {

        public static StyleState defaultState() {
            return new StyleState(null, null, false, false, false, null);
        }

        public StyleState withBold(boolean enabled, String openedByTag) {
            return new StyleState(colorHex, linkUrl, enabled, italic, monospace, openedByTag);
        }

        public StyleState withItalic(boolean enabled, String openedByTag) {
            return new StyleState(colorHex, linkUrl, bold, enabled, monospace, openedByTag);
        }

        public StyleState withMonospace(boolean enabled, String openedByTag) {
            return new StyleState(colorHex, linkUrl, bold, italic, enabled, openedByTag);
        }

        public StyleState withColor(String colorHex) {
            return new StyleState(colorHex, linkUrl, bold, italic, monospace, "color");
        }

        public StyleState withLink(String url) {
            return new StyleState(colorHex, url, bold, italic, monospace, "link");
        }
    }

    private static String normalizeKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null.");
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be blank.");
        }
        return normalized;
    }
}
