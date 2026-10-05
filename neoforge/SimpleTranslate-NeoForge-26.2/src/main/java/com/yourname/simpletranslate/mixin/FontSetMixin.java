package com.yourname.simpletranslate.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.yourname.simpletranslate.config.ModConfig;
import com.yourname.simpletranslate.core.ActiveFontManager;
import com.yourname.simpletranslate.core.ComponentJsonLayoutGuard;
import com.yourname.simpletranslate.gui.ModOwnedUiScreen;
import net.minecraft.client.gui.font.FontSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * When any active {@link FontSet} has no glyph for a code point, borrow the glyph
 * (including advance) from the mod-owned {@code simple_translate:cjk} font.
 * This includes {@code minecraft:default}: server resource packs can replace
 * that font with ASCII-only providers. Private-use icons stay on the original
 * font, and the fallback set itself never recurses.
 *
 * <p>Inside the mod's own screens ({@link ModOwnedUiScreen}) the mod additionally
 * never accepts a pack-provided glyph for CJK code points, because such a pack
 * usually does provide one (a server-side UI icon) and the missing-glyph check
 * above would therefore never fire. Latin, digits and private-use icons keep the
 * pack's glyphs, so only Chinese is forced onto the built-in font.
 */
@Mixin(FontSet.class)
public abstract class FontSetMixin {
    @Unique
    private static final ThreadLocal<Boolean> simple_translate$fallingBack =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    @ModifyReturnValue(method = "computeGlyphInfo", at = @At("RETURN"))
    private FontSet.SelectedGlyphs simple_translate$fallbackMissingGlyph(
            FontSet.SelectedGlyphs original, int codePoint) {
        if (original == null
                || simple_translate$isPrivateUse(codePoint)
                || Boolean.TRUE.equals(simple_translate$fallingBack.get())) {
            return original;
        }

        FontSet self = (FontSet) (Object) this;
        FontSetAccessor selfAccess = (FontSetAccessor) self;
        // The mod's own screens always show real Chinese. A pack that repaints
        // CJK code points with server-side UI icons does provide a glyph for
        // them, so the missing-glyph fallback below can never fire: force the
        // mod-owned font for this scope instead.
        boolean ownUiCjk = ModConfig.OWN_UI_CJK_FONT_ENABLED.get()
                && ModOwnedUiScreen.isActive()
                && ComponentJsonLayoutGuard.isForcedCjkFontCodepoint(codePoint);
        if (!ownUiCjk
                && (!ModConfig.CUSTOM_FONT_CJK_FIX_ENABLED.get()
                        || original != selfAccess.simple_translate$getMissingSelectedGlyphs())) {
            return original;
        }

        FontSet cjkFallback = ActiveFontManager.getCjkFallbackFontSet();
        if (cjkFallback == null || cjkFallback == self) {
            return original;
        }

        simple_translate$fallingBack.set(Boolean.TRUE);
        try {
            FontSetAccessor fallbackAccess = (FontSetAccessor) cjkFallback;
            FontSet.SelectedGlyphs fallback =
                    fallbackAccess.simple_translate$invokeGetGlyph(codePoint);
            if (fallback == null
                    || fallback == fallbackAccess.simple_translate$getMissingSelectedGlyphs()) {
                return original;
            }
            return fallback;
        } catch (Throwable ignored) {
            return original;
        } finally {
            simple_translate$fallingBack.set(Boolean.FALSE);
        }
    }

    @Unique
    private static boolean simple_translate$isPrivateUse(int codePoint) {
        return (codePoint >= 0xE000 && codePoint <= 0xF8FF)
                // Wynn selector/dialogue packs use the otherwise-unassigned
                // planes 12 and 13 for positioned resource glyphs.
                || (codePoint >= 0xC0000 && codePoint <= 0xDFFFF)
                || (codePoint >= 0xF0000 && codePoint <= 0xFFFFD)
                || (codePoint >= 0x100000 && codePoint <= 0x10FFFD);
    }
}
