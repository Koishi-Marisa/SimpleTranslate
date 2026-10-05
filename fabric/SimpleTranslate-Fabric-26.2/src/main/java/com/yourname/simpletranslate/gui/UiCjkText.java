package com.yourname.simpletranslate.gui;

import com.yourname.simpletranslate.config.ModConfig;
import com.yourname.simpletranslate.core.ActiveFontManager;
import com.yourname.simpletranslate.core.ComponentJsonLayoutGuard;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;

/**
 * Repaints the mod's own short status texts (actionbar notices) with the
 * mod-owned CJK font.
 *
 * <p>{@link ModOwnedUiScreen} covers text drawn while one of the mod's screens
 * is open. The mod's own actionbar notices are drawn on the HUD with no screen
 * open, so they cannot be scoped that way: a server resource pack that maps
 * Chinese code points to server-side UI icons would still paint its icons on
 * the mod's own status lines. Each such notice is therefore rebuilt as a
 * component whose CJK runs carry {@code simple_translate:cjk}, while Latin,
 * digits and punctuation keep the inherited font.</p>
 *
 * <p>Legacy {@code §} formatting is preserved run by run, so colours and
 * decorations survive the split. Explicit component styles are not carried
 * over; every caller passes a plain {@code Component.translatable(...)}.</p>
 */
public final class UiCjkText {
    private static final char FORMAT_CODE = '\u00a7';
    private static final FontDescription CJK_FONT =
            new FontDescription.Resource(ActiveFontManager.CJK_FALLBACK_FONT);

    private UiCjkText() {
    }

    /**
     * Returns {@code source} with every CJK run remounted on the mod-owned CJK
     * font, or {@code source} itself when the toggle is off or the text holds no
     * CJK at all.
     */
    public static Component own(Component source) {
        if (source == null || !ModConfig.OWN_UI_CJK_FONT_ENABLED.get()) {
            return source;
        }
        String text = source.getString();
        if (!containsCjk(text)) {
            return source;
        }

        MutableComponent result = Component.empty();
        StringBuilder activeCodes = new StringBuilder();
        StringBuilder runCodes = new StringBuilder();
        StringBuilder run = new StringBuilder();
        boolean runCjk = false;
        int index = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == FORMAT_CODE && index + 1 < text.length()) {
                // A legacy pair always starts its own run, so the colour or
                // decoration it introduces reaches exactly the text that
                // followed it in the source string.
                flush(result, run, runCodes, runCjk);
                char code = Character.toLowerCase(text.charAt(index + 1));
                if (code == 'r' || isLegacyColorCode(code)) {
                    activeCodes.setLength(0);
                }
                if (code != 'r') {
                    activeCodes.append(FORMAT_CODE).append(text.charAt(index + 1));
                }
                runCodes.setLength(0);
                runCodes.append(activeCodes);
                index += 2;
                continue;
            }
            int codePoint = text.codePointAt(index);
            boolean cjk = ComponentJsonLayoutGuard.isForcedCjkFontCodepoint(codePoint);
            if (run.length() > 0 && cjk != runCjk) {
                flush(result, run, runCodes, runCjk);
                runCodes.setLength(0);
                runCodes.append(activeCodes);
            }
            runCjk = cjk;
            run.appendCodePoint(codePoint);
            index += Character.charCount(codePoint);
        }
        flush(result, run, runCodes, runCjk);
        return result;
    }

    private static void flush(MutableComponent result, StringBuilder run, StringBuilder codes, boolean cjk) {
        if (run.length() == 0) {
            return;
        }
        MutableComponent segment = Component.literal(codes.toString() + run);
        if (cjk) {
            segment = segment.withStyle(style -> style.withFont(CJK_FONT));
        }
        result.append(segment);
        run.setLength(0);
    }

    private static boolean containsCjk(String text) {
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            if (ComponentJsonLayoutGuard.isForcedCjkFontCodepoint(codePoint)) {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean isLegacyColorCode(char code) {
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
    }
}
