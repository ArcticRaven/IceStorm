package dev.arctic.icestorm.corelib.text;

import com.hypixel.hytale.server.core.Message;
import dev.arctic.icestorm.corelib.placeholder.ContextSet;
import dev.arctic.icestorm.corelib.placeholder.PlaceholderEngine;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * Utility for parsing tagged strings into a Hytale {@link Message} tree.
 *
 * <p>ParseUtil is responsible only for scanning input and emitting {@link Message} nodes.
 * Tag interpretation and placeholder resolution are delegated to {@link TagUtil}.</p>
 */
public final class ParseUtil {

    private ParseUtil() {}

    public static Message parse(String input) {
        if (input == null || input.isEmpty()) {
            return Message.raw("");
        }
        return parseInternal(input, null, null, ContextSet.DEFAULT_CONTEXT);
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
            if (key == null || key.isBlank()) {
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

    public static Message parse(String input, PlaceholderEngine placeholderEngine, ContextSet contextSet) {
        return parse(input, placeholderEngine, contextSet, ContextSet.DEFAULT_CONTEXT);
    }

    public static Message parse(String input,
                                PlaceholderEngine placeholderEngine,
                                ContextSet contextSet,
                                String defaultContextTag) {

        if (input == null || input.isEmpty()) {
            return Message.raw("");
        }

        String tag = (defaultContextTag == null || defaultContextTag.isBlank())
                ? ContextSet.DEFAULT_CONTEXT
                : defaultContextTag;

        return parseInternal(input, placeholderEngine, contextSet, tag);
    }

    private static Message parseInternal(String input,
                                         PlaceholderEngine placeholderEngine,
                                         ContextSet contextSet,
                                         String defaultContextTag) {

        Message root = Message.empty();
        Deque<TagUtil.StyleState> styleStack = new ArrayDeque<>();
        styleStack.push(TagUtil.StyleState.defaultState());

        int index = 0;
        while (index < input.length()) {
            int openBracketIndex = input.indexOf('<', index);
            if (openBracketIndex < 0) {
                emitText(root, input.substring(index), styleStack.peek());
                break;
            }

            if (openBracketIndex > index) {
                emitText(root, input.substring(index, openBracketIndex), styleStack.peek());
            }

            int closeBracketIndex = input.indexOf('>', openBracketIndex + 1);
            if (closeBracketIndex < 0) {
                emitText(root, input.substring(openBracketIndex), styleStack.peek());
                break;
            }

            String tagContent = input.substring(openBracketIndex + 1, closeBracketIndex).trim();

            // 1) Try resolve placeholder tag (if engine/context provided).
            String placeholderResolved = TagUtil.tryResolvePlaceholderTag(
                    tagContent,
                    placeholderEngine,
                    contextSet,
                    defaultContextTag
            );

            if (placeholderResolved != null) {
                emitText(root, placeholderResolved, styleStack.peek());
                index = closeBracketIndex + 1;
                continue;
            }

            // 2) Try handle style tag.
            boolean handled = TagUtil.handleStyleTag(tagContent, styleStack);

            if (!handled) {
                emitText(root, "<" + tagContent + ">", styleStack.peek());
            }

            index = closeBracketIndex + 1;
        }

        return root;
    }

    private static void emitText(Message root, String text, TagUtil.StyleState styleState) {
        if (text == null || text.isEmpty()) {
            return;
        }

        Message node = Message.raw(text);

        if (styleState.colorHex() != null) {
            node.color(styleState.colorHex());
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
}
