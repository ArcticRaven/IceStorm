package dev.arctic.icestorm.corelib.text;

import com.hypixel.hytale.server.core.Message;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * Utility for parsing tagged strings into a Hytale {@link Message} tree.
 *
 * <p>This parser supports a small markup language intended for consistent formatting.</p>
 *
 * <p>Shorthands:</p>
 * <ul>
 *   <li>{@code <color:#ffffff>} or {@code <c:#ffffff>}</li>
 *   <li>{@code <reset>} or {@code <r>}</li>
 *   <li>{@code <bold>} or {@code <b>}</li>
 *   <li>{@code <italics>} / {@code <italic>} or {@code <i>}</li>
 *   <li>{@code <gradient:start:end>} or {@code <g:start:end>}</li>
 *   <li>{@code <link:https://...>} or {@code <l:https://...>}</li>
 * </ul>
 *
 * <h2>Gradient</h2>
 * <p>While a gradient is active, characters are emitted with a per-character color step.</p>
 * <p>Gradient interpolation uses OkLab for smoother perceptual transitions.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ParseUtil {

    public static Message parse(String input) {
        return parseInternal(input, true);
    }

    /**
     * Parses tags but does not allow {@code <ph:...>} resolution.
     * Placeholder tags are emitted literally.
     */
    public static Message parseNoPlaceholders(String input) {
        return parseInternal(input, false);
    }

    public static Message parse(String input, Map<String, String> replacements) {
        if (input == null || input.isEmpty()) {
            return Message.raw("");
        }
        if (replacements == null || replacements.isEmpty()) {
            return parse(input);
        }

        String replaced = input;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isEmpty()) {
                continue;
            }

            String value = entry.getValue();
            if (value == null) {
                value = "";
            }

            replaced = replaced.replace(key, value);
        }

        return parse(replaced);
    }

    public static Message parseNoPlaceholders(String input, Map<String, String> replacements) {
        if (input == null || input.isEmpty()) {
            return Message.raw("");
        }
        if (replacements == null || replacements.isEmpty()) {
            return parseNoPlaceholders(input);
        }

        String replaced = input;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isEmpty()) {
                continue;
            }

            String value = entry.getValue();
            if (value == null) {
                value = "";
            }

            replaced = replaced.replace(key, value);
        }

        return parseNoPlaceholders(replaced);
    }

    private static Message parseInternal(String input, boolean allowPlaceholders) {
        if (input == null || input.isEmpty()) {
            return Message.raw("");
        }

        Message root = Message.empty();

        Deque<TagUtil.StyleState> styleStack = new ArrayDeque<>(8);
        styleStack.push(TagUtil.StyleState.defaultState());

        StringBuilder textBuffer = new StringBuilder(Math.min(64, input.length()));

        int index = 0;
        int length = input.length();

        while (index < length) {
            char ch = input.charAt(index);

            if (ch != '<') {
                textBuffer.append(ch);
                index++;
                continue;
            }

            flushText(root, textBuffer, styleStack.peek());

            int closeBracketIndex = input.indexOf('>', index + 1);
            if (closeBracketIndex < 0) {
                textBuffer.append(input, index, length);
                break;
            }

            String tagContent = input.substring(index + 1, closeBracketIndex).trim();
            if (tagContent.isEmpty()) {
                textBuffer.append("<>");
                index = closeBracketIndex + 1;
                continue;
            }

            if (!allowPlaceholders && TagUtil.isPlaceholderTag(tagContent)) {
                textBuffer.append('<').append(tagContent).append('>');
                index = closeBracketIndex + 1;
                continue;
            }

            TagUtil.GradientSpec gradientSpec = TagUtil.tryParseGradientOpenTag(tagContent);
            if (gradientSpec != null) {
                int afterOpenIndex = closeBracketIndex + 1;
                int closeTagStartIndex = indexOfClosingGradientTagIgnoreCase(input, afterOpenIndex);
                int gradientSpanEndIndex = (closeTagStartIndex >= 0) ? closeTagStartIndex : length;

                int steps = countRenderableChars(input, afterOpenIndex, gradientSpanEndIndex);
                TagUtil.Gradient gradient = TagUtil.Gradient.oklab(
                        gradientSpec.startHex(),
                        gradientSpec.endHex(),
                        Math.max(1, steps)
                );

                styleStack.push(styleStack.peek().withGradient(gradient));
                index = closeBracketIndex + 1;
                continue;
            }

            boolean handled = TagUtil.handleStyleTag(tagContent, styleStack);
            if (!handled) {
                textBuffer.append('<').append(tagContent).append('>');
            }

            index = closeBracketIndex + 1;
        }

        flushText(root, textBuffer, styleStack.peek());
        return root;
    }

    private static void flushText(Message root, StringBuilder textBuffer, TagUtil.StyleState styleState) {
        if (textBuffer.length() == 0) {
            return;
        }

        String text = textBuffer.toString();
        textBuffer.setLength(0);

        if (styleState.gradient() != null) {
            TagUtil.Gradient gradient = styleState.gradient();
            for (int i = 0; i < text.length(); i++) {
                emitStyledNode(root, String.valueOf(text.charAt(i)), styleState, gradient.nextColorHex());
            }
            return;
        }

        emitStyledNode(root, text, styleState, styleState.colorHex());
    }

    private static void emitStyledNode(Message root, String text, TagUtil.StyleState styleState, String colorHex) {
        if (text == null || text.isEmpty()) {
            return;
        }

        Message node = Message.raw(text);

        if (colorHex != null) {
            node.color(colorHex);
        }
        if (styleState.bold()) {
            node.bold(true);
        }
        if (styleState.italic()) {
            node.italic(true);
        }
        if (styleState.monospace()) {
            node.monospace(true);
        }
        if (styleState.linkUrl() != null) {
            node.link(styleState.linkUrl());
        }

        root.insert(node);
    }

    private static int indexOfClosingGradientTagIgnoreCase(String input, int fromIndex) {
        int idxFull = indexOfIgnoreCase(input, "</gradient>", fromIndex);
        int idxShort = indexOfIgnoreCase(input, "</g>", fromIndex);

        if (idxFull < 0) {
            return idxShort;
        }
        if (idxShort < 0) {
            return idxFull;
        }
        return Math.min(idxFull, idxShort);
    }

    private static int countRenderableChars(String input, int start, int endExclusive) {
        int count = 0;

        int index = Math.max(0, start);
        int end = Math.min(input.length(), endExclusive);

        while (index < end) {
            char current = input.charAt(index);
            if (current != '<') {
                count++;
                index++;
                continue;
            }

            int close = input.indexOf('>', index + 1);
            if (close < 0 || close >= end) {
                count += (end - index);
                break;
            }

            index = close + 1;
        }

        return Math.max(0, count);
    }

    private static int indexOfIgnoreCase(String haystack, String needle, int fromIndex) {
        int haystackLen = haystack.length();
        int needleLen = needle.length();

        if (needleLen == 0) {
            return Math.max(0, fromIndex);
        }

        int startAt = Math.max(0, fromIndex);
        if (needleLen > haystackLen - startAt) {
            return -1;
        }

        for (int start = startAt; start <= haystackLen - needleLen; start++) {
            boolean match = true;
            for (int offset = 0; offset < needleLen; offset++) {
                char a = haystack.charAt(start + offset);
                char b = needle.charAt(offset);

                if (a == b) {
                    continue;
                }
                if (toLowerAscii(a) != toLowerAscii(b)) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return start;
            }
        }

        return -1;
    }

    private static char toLowerAscii(char c) {
        if (c >= 'A' && c <= 'Z') {
            return (char) (c + 32);
        }
        return c;
    }
}
