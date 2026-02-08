package dev.arctic.icestorm.corelib.text;

import dev.arctic.icestorm.corelib.placeholder.ContextSet;
import dev.arctic.icestorm.corelib.placeholder.PlaceholderEngine;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final String TAG_RESET = "reset";
    private static final String TAG_RESET_SHORT = "r";

    private static final String TAG_BOLD = "bold";
    private static final String TAG_BOLD_SHORT = "b";

    private static final String TAG_ITALIC = "italic";
    private static final String TAG_ITALICS = "italics";
    private static final String TAG_ITALIC_SHORT = "i";

    private static final String TAG_MONO = "mono";
    private static final String TAG_MONOSPACE = "monospace";
    private static final String TAG_CODE = "code";

    private static final String PREFIX_COLOR = "color:";
    private static final String PREFIX_COLOR_SHORT = "c:";

    private static final String PREFIX_LINK = "link:";
    private static final String PREFIX_LINK_SHORT = "l:";

    private static final String PREFIX_GRADIENT = "gradient:";
    private static final String PREFIX_GRADIENT_SHORT = "g:";

    private static final String TAG_PH_PREFIX = "ph:";

    private static final String OPENED_COLOR = "color";
    private static final String OPENED_LINK = "link";
    private static final String OPENED_GRADIENT = "gradient";

    public static final Map<String, String> COLORS = createDefaultColors();

    private static Map<String, String> createDefaultColors() {
        ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>(16);
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

        if (normalized.charAt(0) != '#') {
            return null;
        }

        int length = normalized.length();
        if (length != 7 && length != 9) {
            return null;
        }

        for (int i = 1; i < length; i++) {
            char c = normalized.charAt(i);
            boolean isHex =
                    (c >= '0' && c <= '9') ||
                            (c >= 'a' && c <= 'f');
            if (!isHex) {
                return null;
            }
        }

        return normalized;
    }

    /**
     * Fast check used by ParseUtil to optionally disable placeholder usage.
     */
    public static boolean isPlaceholderTag(String tagContent) {
        return tagContent != null && tagContent.regionMatches(true, 0, TAG_PH_PREFIX, 0, TAG_PH_PREFIX.length());
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

        if (tagContent.charAt(0) == '/') {
            if (styleStack.size() <= 1) {
                return false;
            }

            String closingRaw = tagContent.substring(1).trim().toLowerCase(Locale.ROOT);
            String closingName = canonicalizeTagName(closingRaw);
            if (closingName == null) {
                return false;
            }

            StyleState current = styleStack.peek();
            if (current.openedByTag() != null && current.openedByTag().equals(closingName)) {
                styleStack.pop();
                return true;
            }

            return false;
        }

        String normalized = tagContent.trim().toLowerCase(Locale.ROOT);

        if (normalized.equals(TAG_RESET) || normalized.equals(TAG_RESET_SHORT)) {
            styleStack.clear();
            styleStack.push(StyleState.defaultState());
            return true;
        }

        if (normalized.equals(TAG_BOLD) || normalized.equals(TAG_BOLD_SHORT)) {
            styleStack.push(styleStack.peek().withBold(true));
            return true;
        }

        if (normalized.equals(TAG_ITALIC) || normalized.equals(TAG_ITALICS) || normalized.equals(TAG_ITALIC_SHORT)) {
            styleStack.push(styleStack.peek().withItalic(true));
            return true;
        }

        if (normalized.equals(TAG_MONO) || normalized.equals(TAG_MONOSPACE) || normalized.equals(TAG_CODE)) {
            styleStack.push(styleStack.peek().withMonospace(true));
            return true;
        }

        if (normalized.startsWith(PREFIX_COLOR) || normalized.startsWith(PREFIX_COLOR_SHORT)) {
            int prefixLen = normalized.startsWith(PREFIX_COLOR_SHORT) ? PREFIX_COLOR_SHORT.length() : PREFIX_COLOR.length();
            String value = normalized.substring(prefixLen).trim();
            String resolvedColor = resolveColor(value);
            if (resolvedColor == null) {
                return false;
            }
            styleStack.push(styleStack.peek().withColor(resolvedColor));
            return true;
        }

        if (normalized.startsWith(PREFIX_LINK) || normalized.startsWith(PREFIX_LINK_SHORT)) {
            int prefixLen = normalized.startsWith(PREFIX_LINK_SHORT) ? PREFIX_LINK_SHORT.length() : PREFIX_LINK.length();
            String url = tagContent.substring(prefixLen).trim();
            if (url.isEmpty()) {
                return false;
            }
            styleStack.push(styleStack.peek().withLink(url));
            return true;
        }

        if (normalized.startsWith(PREFIX_GRADIENT) || normalized.startsWith(PREFIX_GRADIENT_SHORT)) {
            return false;
        }

        return false;
    }

    /**
     * ParseUtil calls this to detect and parse a gradient open tag (without consuming it).
     *
     * <p>Supported:</p>
     * <ul>
     *   <li>{@code <gradient:start:end>}</li>
     *   <li>{@code <g:start:end>}</li>
     * </ul>
     */
    public static GradientSpec tryParseGradientOpenTag(String tagContent) {
        if (tagContent == null || tagContent.isBlank()) {
            return null;
        }
        if (tagContent.charAt(0) == '/') {
            return null;
        }

        String normalized = tagContent.trim().toLowerCase(Locale.ROOT);
        String rest;

        if (normalized.startsWith(PREFIX_GRADIENT)) {
            rest = normalized.substring(PREFIX_GRADIENT.length()).trim();
        } else if (normalized.startsWith(PREFIX_GRADIENT_SHORT)) {
            rest = normalized.substring(PREFIX_GRADIENT_SHORT.length()).trim();
        } else {
            return null;
        }

        int splitIndex = rest.indexOf(':');
        if (splitIndex <= 0 || splitIndex >= rest.length() - 1) {
            return null;
        }

        String startToken = rest.substring(0, splitIndex).trim();
        String endToken = rest.substring(splitIndex + 1).trim();

        String startHex = resolveColor(startToken);
        String endHex = resolveColor(endToken);
        if (startHex == null || endHex == null) {
            return null;
        }

        return new GradientSpec(startHex, endHex);
    }

    private static String canonicalizeTagName(String rawLower) {
        if (rawLower == null || rawLower.isEmpty()) {
            return null;
        }

        return switch (rawLower) {
            case TAG_BOLD, TAG_BOLD_SHORT -> TAG_BOLD;
            case TAG_ITALIC, TAG_ITALICS, TAG_ITALIC_SHORT -> TAG_ITALIC;
            case TAG_MONO, TAG_MONOSPACE, TAG_CODE -> TAG_MONO;
            case OPENED_COLOR, "c" -> OPENED_COLOR;
            case OPENED_LINK, "l" -> OPENED_LINK;
            case OPENED_GRADIENT, "g" -> OPENED_GRADIENT;
            default -> null;
        };
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

        if (!isPlaceholderTag(tagContent)) {
            return null;
        }

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

    public record GradientSpec(String startHex, String endHex) {}

    /**
     * Placeholder token extracted from tag content (without the surrounding angle brackets).
     */
    public record PlaceholderToken(
            String keyOrQualifiedKey,
            String contextTag,
            List<String> args,
            String rawToken
    ) {
        static PlaceholderToken parse(String tagContent, String defaultContextTag) {
            String rawToken = "<" + tagContent + ">";

            String body = tagContent.substring(TAG_PH_PREFIX.length()).trim();
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

    public record StyleState(
            String colorHex,
            String linkUrl,
            boolean bold,
            boolean italic,
            boolean monospace,
            String openedByTag,
            Gradient gradient
    ) {

        public static StyleState defaultState() {
            return new StyleState("#ffffff", null, false, false, false, null, null);
        }

        public StyleState withBold(boolean enabled) {
            return new StyleState(colorHex, linkUrl, enabled, italic, monospace, TAG_BOLD, gradient);
        }

        public StyleState withItalic(boolean enabled) {
            return new StyleState(colorHex, linkUrl, bold, enabled, monospace, TAG_ITALIC, gradient);
        }

        public StyleState withMonospace(boolean enabled) {
            return new StyleState(colorHex, linkUrl, bold, italic, enabled, TAG_MONO, gradient);
        }

        public StyleState withColor(String colorHex) {
            return new StyleState(colorHex, linkUrl, bold, italic, monospace, OPENED_COLOR, gradient);
        }

        public StyleState withLink(String url) {
            return new StyleState(colorHex, url, bold, italic, monospace, OPENED_LINK, gradient);
        }

        public StyleState withGradient(Gradient gradient) {
            return new StyleState(colorHex, linkUrl, bold, italic, monospace, OPENED_GRADIENT, gradient);
        }
    }

    public static final class Gradient {

        private final String[] colors;
        private int index;

        private Gradient(String[] colors) {
            this.colors = colors;
            this.index = 0;
        }

        public static Gradient oklab(String startHex, String endHex, int steps) {
            OkColorUtil.RgbaColor start = OkColorUtil.parseHex(startHex);
            OkColorUtil.RgbaColor end = OkColorUtil.parseHex(endHex);

            OkColorUtil.OkLab startLab = OkColorUtil.srgbToOkLab(start.red(), start.green(), start.blue());
            OkColorUtil.OkLab endLab = OkColorUtil.srgbToOkLab(end.red(), end.green(), end.blue());

            boolean includeAlpha = start.hasAlpha() || end.hasAlpha();

            int clampedSteps = Math.max(1, steps);
            String[] colors = new String[clampedSteps];

            for (int i = 0; i < clampedSteps; i++) {
                double t = (clampedSteps == 1) ? 1.0 : (i / (double) (clampedSteps - 1));

                double L = lerp(startLab.lightness(), endLab.lightness(), t);
                double a = lerp(startLab.axisA(), endLab.axisA(), t);
                double b = lerp(startLab.axisB(), endLab.axisB(), t);

                int alpha = (int) Math.round(lerp(start.alpha(), end.alpha(), t));

                int[] rgb = OkColorUtil.okLabToSrgb(L, a, b);
                OkColorUtil.RgbaColor out = new OkColorUtil.RgbaColor(rgb[0], rgb[1], rgb[2], alpha, includeAlpha);

                colors[i] = OkColorUtil.toHex(out, includeAlpha);
            }

            return new Gradient(colors);
        }

        public String nextColorHex() {
            if (colors.length == 1) {
                return colors[0];
            }

            int current = index;
            if (index < colors.length - 1) {
                index++;
            }
            return colors[current];
        }

        private static double lerp(double start, double end, double t) {
            return start + (end - start) * t;
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
