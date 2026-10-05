package com.yourname.simpletranslate.gui;

import com.yourname.simpletranslate.core.ActiveFontManager;
import com.yourname.simpletranslate.core.ComponentJsonLayoutGuard;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the mod-owned-UI font protection: a server resource pack
 * that maps Chinese code points to its own UI icons must not be able to repaint
 * the mod's own screens or its own actionbar notices.
 */
class UiCjkTextTest {
    private static final FontDescription CJK_FONT =
            new FontDescription.Resource(ActiveFontManager.CJK_FALLBACK_FONT);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void cjkPredicateAcceptsRealChineseAndRejectsIconsAndLatin() {
        assertTrue(ComponentJsonLayoutGuard.isForcedCjkFontCodepoint('\u4e2d'), "Han");
        assertTrue(ComponentJsonLayoutGuard.isForcedCjkFontCodepoint('\u3042'), "Hiragana");
        assertTrue(ComponentJsonLayoutGuard.isForcedCjkFontCodepoint('\ud55c'), "Hangul");
        assertTrue(ComponentJsonLayoutGuard.isForcedCjkFontCodepoint('\uff21'), "fullwidth latin");
        assertFalse(ComponentJsonLayoutGuard.isForcedCjkFontCodepoint('A'), "ascii letter");
        assertFalse(ComponentJsonLayoutGuard.isForcedCjkFontCodepoint('5'), "digit");
        assertFalse(ComponentJsonLayoutGuard.isForcedCjkFontCodepoint('\u00a7'), "legacy format pair");
        assertFalse(ComponentJsonLayoutGuard.isForcedCjkFontCodepoint(0xE000), "private-use icon");
    }

    @Test
    void textWithoutCjkIsReturnedUnchanged() {
        Component source = Component.literal("Simple Translate 5.0");
        assertSame(source, UiCjkText.own(source));
    }

    @Test
    void onlyTheChineseRunMovesToTheModFont() {
        Component result = UiCjkText.own(Component.literal("Simple Translate \u5df2\u542f\u7528"));

        assertEquals("Simple Translate \u5df2\u542f\u7528", result.getString());
        List<Component> children = result.getSiblings();
        assertEquals(2, children.size(), "latin run plus chinese run");
        assertNotEquals(CJK_FONT, children.get(0).getStyle().getFont(), "latin keeps the pack glyphs");
        assertEquals(CJK_FONT, children.get(1).getStyle().getFont(), "chinese uses the mod font");
    }

    @Test
    void legacyFormatPairsSurviveTheSplit() {
        Component result = UiCjkText.own(Component.literal("\u00a7aLevel 5 \u00a7f\u5b8c\u6210"));

        assertEquals("\u00a7aLevel 5 \u00a7f\u5b8c\u6210", result.getString());
        List<Component> children = result.getSiblings();
        assertEquals(2, children.size());
        assertEquals("\u00a7aLevel 5 ", children.get(0).getString(), "colour pair stays with its own run");
        assertEquals("\u00a7f\u5b8c\u6210", children.get(1).getString(), "reintroduced colour reaches the chinese run");
        assertEquals(CJK_FONT, children.get(1).getStyle().getFont());
    }

    @Test
    void pureChineseBecomesOneModFontRun() {
        Component result = UiCjkText.own(Component.literal("\u5f00\u542f\u7ffb\u8bd1"));

        assertEquals("\u5f00\u542f\u7ffb\u8bd1", result.getString());
        List<Component> children = result.getSiblings();
        assertEquals(1, children.size());
        assertEquals(CJK_FONT, children.get(0).getStyle().getFont());
    }
}
