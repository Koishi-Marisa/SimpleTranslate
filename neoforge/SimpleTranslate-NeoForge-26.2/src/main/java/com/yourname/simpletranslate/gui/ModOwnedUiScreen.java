package com.yourname.simpletranslate.gui;

import net.minecraft.client.Minecraft;

/**
 * Marks a screen as fully owned by this mod.
 *
 * <p>A server resource pack can repaint Chinese code points with server-side UI
 * icons. Translated text is protected by {@code ComponentJsonLayoutGuard}, but
 * the mod's own screens render their titles, labels and tooltips with
 * {@code minecraft:default} — the very font such a pack rewrites. Every screen
 * that extends {@link BaseSimpleTranslateScreen} implements this marker, so the
 * font fallback hook ({@code FontSetMixin}) can paint CJK code points of those
 * screens with the mod-owned {@code simple_translate:cjk} font instead of the
 * pack's replacement glyphs.</p>
 *
 * <p>Only CJK code points move. Latin, digits, legacy format pairs and
 * private-use positioning glyphs keep the pack's glyphs, so nothing else about
 * the client's appearance changes.</p>
 */
public interface ModOwnedUiScreen {
    /**
     * True while the screen the client is currently drawing is one of the mod's
     * own screens. Safe to call from any thread; never throws.
     */
    static boolean isActive() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) {
            return false;
        }
        return minecraft.gui.screen() instanceof ModOwnedUiScreen;
    }
}
