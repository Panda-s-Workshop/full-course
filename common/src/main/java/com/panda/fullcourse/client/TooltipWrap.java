package com.panda.fullcourse.client;

import com.panda.fullcourse.kitchen.RecipeHints;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Wraps a tooltip line at its spaces, but never inside a name marked {@link RecipeHints#whole} ("Baked Potato" stays on
 * one line), and indents each line after the first, so a wrapped list reads as one entry.
 */
public final class TooltipWrap {
    private static final String INDENT = "  ";

    private TooltipWrap() {}

    public static List<FormattedCharSequence> hanging(Font font, Component line, int room) {
        // words: styled pieces up to and including a space it may break at
        List<List<FormattedText>> words = new ArrayList<>();
        List<FormattedText> word = new ArrayList<>();
        line.visit((style, text) -> {
            boolean whole = RecipeHints.WHOLE.equals(style.getInsertion());
            int from = 0;
            for (int i = 0; i < text.length(); i++) {
                if (whole || text.charAt(i) != ' ') continue;
                word.add(FormattedText.of(text.substring(from, i + 1), style));
                words.add(List.copyOf(word));
                word.clear();
                from = i + 1;
            }
            if (from < text.length()) word.add(FormattedText.of(text.substring(from), style));
            return Optional.empty();
        }, Style.EMPTY);
        if (!word.isEmpty()) words.add(List.copyOf(word));

        List<FormattedCharSequence> out = new ArrayList<>();
        List<FormattedText> current = new ArrayList<>();
        int width = 0;
        int space = font.width(" ");
        for (List<FormattedText> w : words) {
            int full = font.width(FormattedText.composite(w));
            int shown = w.getLast().getString().endsWith(" ") ? full - space : full; // a line's last space takes no room
            int limit = out.isEmpty() ? room : room - font.width(INDENT);
            if (!current.isEmpty() && width + shown > limit) {
                out.add(line(current, out.isEmpty()));
                current = new ArrayList<>();
                width = 0;
            }
            current.addAll(w);
            width += full;
        }
        if (!current.isEmpty()) out.add(line(current, out.isEmpty()));
        return out;
    }

    private static FormattedCharSequence line(List<FormattedText> parts, boolean first) {
        List<FormattedText> all = new ArrayList<>();
        if (!first) all.add(FormattedText.of(INDENT));
        all.addAll(parts);
        return Language.getInstance().getVisualOrder(FormattedText.composite(all));
    }
}
