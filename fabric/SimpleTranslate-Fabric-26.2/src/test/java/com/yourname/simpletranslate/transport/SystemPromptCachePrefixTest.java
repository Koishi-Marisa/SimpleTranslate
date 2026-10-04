package com.yourname.simpletranslate.transport;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the prompt layout that provider prefix caching depends on.
 *
 * <p>DeepSeek (and comparable providers) only serve a cached prompt when the
 * request matches the cached entry from the very first token; a partial match
 * anywhere after that is billed as a full miss. The shared rule block must
 * therefore stay the leading part of the system prompt and must not absorb any
 * request-dependent value — language, surface, player orders, retry state or
 * local context metadata — or every distinct value would create a new cache
 * entry with a miss at token 0.</p>
 */
class SystemPromptCachePrefixTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        // Component/Style codecs initialize registries on first use; the vanilla
        // bootstrap must have run before this test class touches them.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final String[][] REQUESTS = {
            // sourceLanguage, targetLanguage, surface, promptContext
            {"en_us", "zh_cn", "tooltip.visible.item.component.v2", ""},
            {"en_us", "zh_cn", "chat.received", ""},
            {"en_us", "zh_cn", "gui.component.visible_frame.v1", ""},
            {"en_us", "zh_cn", "hud.actionbar", ""},
            {"auto", "zh_tw", "sign.auto", ""},
            {"en_us", "ja_jp", "text_display.wynn.npc_label.v1", ""},
            {"en_us", "zh_cn", "tooltip.visible.item.component.v2",
                    "{\"caller_context\":\"frame_context_kind=item_tooltip\",\"scope\":\"server-a\"}"},
            {"en_us", "zh_cn", "chat.received",
                    "{\"component_structure_retry\":true}"},
            {"en_us", "zh_cn", "chat.received",
                    "{\"component_partition_recovery\":true}"},
    };

    @Test
    void everySystemPromptStartsWithTheSharedRuleBlock() {
        for (String[] request : REQUESTS) {
            String prompt = JsonPassthroughPrompts.buildSystemPrompt(
                    request[0], request[1], List.of(), request[2], request[3]);
            assertTrue(prompt.startsWith(JsonPassthroughPrompts.STATIC_RULES),
                    "system prompt for surface " + request[2] + " / " + request[0] + " -> " + request[1]
                            + " does not begin with the shared rule block");
        }
    }

    @Test
    void sharedRuleBlockIsTheCommonPrefixOfEveryRequest() {
        String commonPrefix = null;
        for (String[] request : REQUESTS) {
            String prompt = JsonPassthroughPrompts.buildSystemPrompt(
                    request[0], request[1], List.of(), request[2], request[3]);
            commonPrefix = commonPrefix == null ? prompt : commonPrefixOf(commonPrefix, prompt);
        }
        assertTrue(commonPrefix != null && commonPrefix.length() >= JsonPassthroughPrompts.STATIC_RULES.length(),
                "cached prefix shrank below the shared rule block: "
                        + (commonPrefix == null ? 0 : commonPrefix.length()) + " chars");
        assertTrue(commonPrefix.contains("CRITICAL STRUCTURAL RULES"),
                "the cached prefix must contain the structural rules");
        assertTrue(commonPrefix.contains("TEXT TRANSLATION RULES"),
                "the cached prefix must contain the text translation rules");
    }

    @Test
    void sharedRuleBlockIsLargeEnoughToBeCachedAtAll() {
        // Providers cache in 64-token storage units; a shorter block would never
        // be stored. Four characters per token is a deliberately loose bound.
        assertTrue(JsonPassthroughPrompts.STATIC_RULES.length() >= 512,
                "shared rule block is too short to be worth caching: "
                        + JsonPassthroughPrompts.STATIC_RULES.length() + " chars");
    }

    @Test
    void sharedRuleBlockCarriesNoRequestSpecificValue() {
        String rules = JsonPassthroughPrompts.STATIC_RULES;
        for (String forbidden : List.of(
                "Translate from", "Chinese", "Japanese", "Wynncraft", "item tooltip",
                "MANDATORY TERMINOLOGY", "PLAYER'S HIGHEST-PRIORITY", "player profile",
                "OPTIONAL LOCAL CONTEXT METADATA", "STRUCTURAL CORRECTION RETRY",
                "COMPONENT PARTITION RECOVERY", "caller_context", "surface_role", "zh_cn")) {
            assertFalse(rules.contains(forbidden),
                    "shared rule block must stay request-independent but contains \"" + forbidden + "\"");
        }
    }

    private static String commonPrefixOf(String left, String right) {
        int limit = Math.min(left.length(), right.length());
        int index = 0;
        while (index < limit && left.charAt(index) == right.charAt(index)) {
            index++;
        }
        return left.substring(0, index);
    }
}
