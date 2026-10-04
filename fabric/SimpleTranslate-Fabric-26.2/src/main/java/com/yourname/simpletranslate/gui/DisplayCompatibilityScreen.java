package com.yourname.simpletranslate.gui;

import com.yourname.simpletranslate.config.ModConfig;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Generic display compatibility that is not tied to one server. */
public final class DisplayCompatibilityScreen extends ScrollableSettingsScreen {
    private boolean customFontCjkFix;
    private boolean forceTranslatedCjkFont;
    private boolean maskAllNumbers;

    public DisplayCompatibilityScreen(Screen parent) {
        super(Component.translatable("screen.simple_translate.display_compatibility"), parent);
        this.contentWidth = 320;
        this.customFontCjkFix = ModConfig.CUSTOM_FONT_CJK_FIX_ENABLED.get();
        this.forceTranslatedCjkFont = ModConfig.FORCE_TRANSLATED_CJK_FONT_ENABLED.get();
        this.maskAllNumbers = ModConfig.MASK_ALL_NUMBERS_ENABLED.get();
    }

    @Override
    protected void buildContent() {
        addSectionHeader(Component.translatable("screen.simple_translate.display_compatibility.section.fonts").getString());
        CycleButton<Boolean> customFont = CycleButton.onOffBuilder(this.customFontCjkFix)
                .create(0, 0, this.contentWidth, 20,
                        Component.translatable("screen.simple_translate.settings.custom_font_cjk_fix"),
                        (button, value) -> this.customFontCjkFix = value);
        withTooltip(customFont, "screen.simple_translate.settings.custom_font_cjk_fix.tooltip");
        addEntry(customFont);
        CycleButton<Boolean> translatedCjkFont = CycleButton.onOffBuilder(this.forceTranslatedCjkFont)
                .create(0, 0, this.contentWidth, 20,
                        Component.translatable("screen.simple_translate.settings.force_translated_cjk_font"),
                        (button, value) -> this.forceTranslatedCjkFont = value);
        withTooltip(translatedCjkFont, "screen.simple_translate.settings.force_translated_cjk_font.tooltip");
        addEntry(translatedCjkFont);
        addSectionHeader(Component.translatable("screen.simple_translate.display_compatibility.section.stable_numbers").getString());
        CycleButton<Boolean> maskNumbers = CycleButton.onOffBuilder(this.maskAllNumbers)
                .create(0, 0, this.contentWidth, 20,
                        Component.translatable("screen.simple_translate.settings.mask_all_numbers"),
                        (button, value) -> this.maskAllNumbers = value);
        withTooltip(maskNumbers, "screen.simple_translate.settings.mask_all_numbers.tooltip");
        addEntry(maskNumbers);
        addDescription(Component.translatable("screen.simple_translate.display_compatibility.note").getString());
    }

    @Override
    protected void saveSettings() {
        ModConfig.CUSTOM_FONT_CJK_FIX_ENABLED.set(this.customFontCjkFix);
        ModConfig.FORCE_TRANSLATED_CJK_FONT_ENABLED.set(this.forceTranslatedCjkFont);
        ModConfig.MASK_ALL_NUMBERS_ENABLED.set(this.maskAllNumbers);
    }
}
