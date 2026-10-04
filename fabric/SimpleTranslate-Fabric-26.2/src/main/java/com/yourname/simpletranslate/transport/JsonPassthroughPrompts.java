package com.yourname.simpletranslate.transport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yourname.simpletranslate.core.TranslationTextDetector;

import java.util.List;
import java.util.Locale;

/**
 * System prompt for the JSON-passthrough component translation mode.
 *
 * <p>The model receives an ordered top-level array of semantic Minecraft
 * Components. Opaque visuals, format controls, custom-font positioning glyphs,
 * dynamic values and hidden hover payloads stay local. The response therefore
 * needs only the same top-level count and parseable translated Components; the
 * client binds their visible text back to the untouched source structure.</p>
 *
 * <h2>Prompt layout and provider prefix caching</h2>
 *
 * <p>DeepSeek caches a request prefix starting at the very first token (in
 * 64-token storage units) and bills a hit at roughly a tenth of the miss price;
 * a partial match in the middle of the prompt never hits. The prompt is
 * therefore laid out as two blocks:</p>
 *
 * <ol>
 *   <li>{@link #STATIC_RULES} — every byte is request-independent, so one
 *       warmed cache entry serves every surface, language pair and player
 *       instead of only the surface that happened to warm it first.</li>
 *   <li>the request-specific tail — source/target language, surface, its extra
 *       rules, retry notices, examples, terminology and local context
 *       metadata, in that order.</li>
 * </ol>
 *
 * <p>Anything request-dependent added to the static block would move the first
 * cache miss boundary to the top of the prompt, which is why the language,
 * surface, caller context and retry state are only ever read after it. The
 * regression test {@code transport.SystemPromptCachePrefixTest} guards this.</p>
 */
public final class JsonPassthroughPrompts {

    private JsonPassthroughPrompts() {
    }

    /**
     * Rules shared by every request. This block must stay byte-identical: it is
     * the part that provider prefix caching can reuse across calls.
     */
    static final String STATIC_RULES = """
            You are a professional Minecraft game localizer.
            The user message contains an ordered JSON array of semantic Minecraft Component slots from one screen region, message block or HUD area. Each top-level element is one translatable slot in reading order. Read the whole array as one coherent visible document before translating. Use neighboring slots to resolve sentence fragments, articles, pronouns, menu labels, and terminology; translate every slot into the target language, and keep each result at the same top-level index. Return ONLY a JSON array of Minecraft Components — no markdown, no explanation, no headers.
            The request-specific sections after these rules state the source language, the target language, the surface, the player's orders and the local context. They are authoritative for this request and override the generic wording; the rule sections themselves are identical for every request.

            CRITICAL STRUCTURAL RULES:
            - Preserve the exact top-level array length and order: one input slot → one output Component.
            - Every output element must be a valid Minecraft Component containing the complete translation of its corresponding slot. Never merge, split, reorder, or drop top-level slots.
            - A visual line or tooltip row may be only part of a sentence. Do not translate slots as isolated dictionary entries: carry grammar and meaning across adjacent slots, while still returning exactly one output Component at each original index.
            - These semantic slots normally use Minecraft's valid JSON string Component shorthand. Prefer one JSON string per output slot; do not expand one string into multiple top-level elements.
            - Do not invent icons, private-use characters, format controls, placeholders, custom fonts, coordinates, or spacing glyphs. The client preserves all opaque visuals and dynamic values locally.
            - Keep the JSON valid: proper quotes, commas, and brackets.

            TEXT TRANSLATION RULES:
            - Translate natural-language words and phrases in every "text" field.
            - Keep player names, /commands, @selectors, and ordinary format placeholders (%s, {0}) unchanged.
            - If a "text" field is empty (""), keep it empty.
            - If a "text" field has no natural language (only symbols/numbers), keep it unchanged.
            - Unless the player's orders or the mandatory terminology below say otherwise, for game content titles, item names, skill names, and invented Latin words, create a localized name or natural transliteration; do not copy the Latin unchanged. Only keep real player names unchanged.
            - Some entries are sentence fragments separated by a classified live value, coordinate, icon, key glyph, or other value that the client retains locally. The optional full source block shows those gaps. Translate the surrounding entries so their unchanged-order concatenation with every retained value is fluent in the target language. Never leave a dangling source-language article or preposition such as 'the', 'a', 'an', 'of', or 'to' beside a retained value.
            - In context metadata only, <number> marks a classified live number already owned by the client. Never emit <number>, a copied digit, a spelled-out replacement value, or any new placeholder. Translate only the requested words around that local gap; the client inserts the value once. All ordinary numbers present in the JSON request are semantic sentence content: preserve each exactly once and translate the grammar around it normally.

            """;

    public static String buildSystemPrompt(String sourceLanguage, String targetLanguage,
                                           List<com.yourname.simpletranslate.api.TranslationRequest.Term> termHints) {
        return buildSystemPrompt(sourceLanguage, targetLanguage, termHints, "");
    }

    public static String buildSystemPrompt(String sourceLanguage, String targetLanguage,
                                           List<com.yourname.simpletranslate.api.TranslationRequest.Term> termHints,
                                           String surface) {
        return buildSystemPrompt(sourceLanguage, targetLanguage, termHints, surface, "");
    }

    public static String buildSystemPrompt(String sourceLanguage, String targetLanguage,
                                           List<com.yourname.simpletranslate.api.TranslationRequest.Term> termHints,
                                           String surface, String promptContext) {
        String sourceCode = TranslationTextDetector.canonicalLanguageCode(sourceLanguage);
        String sourceClause = "auto".equals(sourceCode)
                ? "Auto-detect the source language and translate"
                : "Translate from " + TranslationTextDetector.displayLanguageName(sourceLanguage);
        String target = TranslationTextDetector.displayLanguageName(targetLanguage);
        String surfaceValue = surface == null ? "" : surface.toLowerCase(Locale.ROOT);
        boolean wynnSemanticSurface = surfaceValue.contains(".wynn.")
                || surfaceValue.startsWith("wynn.");
        boolean wynnNpcNameplateSurface = surfaceValue.startsWith("text_display.wynn.npc_label.");
        boolean wynnDialogueContentSurface = surfaceValue.contains(".wynn.dialogue.content.");
        boolean wholeGuiFrame = surfaceValue.startsWith("gui.component.visible_frame.")
                || surfaceValue.startsWith("hud.visible_frame.");
        boolean itemTooltipFrame = wholeGuiFrame
                && callerContextContains(promptContext, "frame_context_kind=item_tooltip");
        boolean itemTooltipSurface = surfaceValue.startsWith("tooltip.item_context")
                || surfaceValue.startsWith("tooltip.visible.item.")
                || itemTooltipFrame;
        boolean structuralRetry = promptContext != null
                && promptContext.contains("\"component_structure_retry\":true");
        boolean partitionRecovery = promptContext != null
                && promptContext.contains("\"component_partition_recovery\":true");

        StringBuilder prompt = new StringBuilder(STATIC_RULES.length() + 1536);
        prompt.append(STATIC_RULES);
        // Everything appended from here on is request-specific. The cacheable
        // prefix ends where STATIC_RULES ends, so nothing above may read the
        // source language, target language, surface, retry state or context.
        prompt.append("TRANSLATION TASK:\n");
        prompt.append("- ").append(sourceClause).append(" to ").append(target).append(".\n");
        prompt.append("- These slots come from ").append(describeSurface(surfaceValue, itemTooltipSurface,
                wholeGuiFrame, wynnNpcNameplateSurface)).append(".\n");
        if (itemTooltipSurface) {
            prompt.append("- This is an item tooltip: translate the title, lore, mechanic phrases, equipment labels, ")
                    .append("attribute names, rarity/category badges, all-caps headings, control hints, remaining-count labels, ")
                    .append("and sentence fragments coherently across all array entries. Short words ")
                    .append("around an icon or colour boundary still belong to the surrounding sentence; move their ")
                    .append("meaning between same-order slots when needed so the concatenated target text is natural. ")
                    .append("For quest objectives, preserve logical scope: a trailing phrase such as 'in N games/matches' ")
                    .append("normally applies to the whole preceding condition, not merely the nearest verb; express that scope explicitly.\n");
        } else if (surfaceValue.startsWith("hover.context")) {
            prompt.append("- This is a chat hover tooltip: understand the whole tooltip before translating titles, ")
                    .append("skill descriptions, lore, and mechanic lines. Keep commands and numeric values unchanged.\n");
        } else if (surfaceValue.startsWith("chat.outgoing")) {
            prompt.append("- This is a player's outgoing chat message: translate the whole message into the target ")
                    .append("language before it is sent. If the input mixes languages, convert every natural-language ")
                    .append("fragment into the target language while keeping names, commands, and placeholders unchanged.\n");
        } else if (surfaceValue.startsWith("chat.")) {
            prompt.append("- For consecutive chat/menu lines, understand the whole array as one server message block ")
                    .append("when possible, but still return one translated component per input component.\n");
        }
        if (wynnSemanticSurface) {
            prompt.append("- This is a Wynncraft semantic-layout request. Every entry contains only a safe ")
                    .append("natural-language phrase; translate every phrase, including short menu labels. ")
                    .append("Do not invent icons, keybinds, arrows, spacing, private-use glyphs, or legacy § format codes; ")
                    .append("the client restores those outside this JSON array.\n");
            prompt.append("- Interpret capitalized professions, merchants, and service names from the surrounding ")
                    .append("Wynncraft sentence as in-world roles, never as programming identifiers or item IDs. ")
                    .append("For Chinese, \"Item Identifier\"/\"Item Identifiers\" means \"物品鉴定师\", not \"物品标识符\".\n");
        }
        if (wynnNpcNameplateSurface) {
            prompt.append("- This is a short Wynncraft NPC, merchant, or service title, not a program identifier or item ID. ")
                    .append("Localize it as a natural in-world role/title; keep only genuine player names unchanged. ")
                    .append("For example, translate \"Item Identifier\" as \"物品鉴定师\" in Chinese, never \"物品标识符\".\n");
        }
        if (wynnDialogueContentSurface) {
            prompt.append("- This Wynncraft dialogue BODY slot is one complete spoken paragraph. Physical source rows, "
                    + "style fragments, icons, keycaps, controls, and format glyphs are presentation owned by the client. "
                    + "Return one fluent paragraph translation in this slot; never split the sentence around source visual "
                    + "boundaries or invent replacements for client-owned visuals. CONTROL prose may be translated, but "
                    + "protected keycap glyphs remain client-owned.\n");
        }
        if (wholeGuiFrame) {
            prompt.append("- GUI frame entries are physical draw rows, labels, or style fragments, not guaranteed logical sentences. ")
                    .append("First reconstruct every complete clause from the full ordered source block and caller_context. ")
                    .append("A visual wrap must not change which trailing quantity, location, or condition applies to the preceding clause. ")
                    .append("Then distribute the translated wording across the same ordered slots without duplicating or omitting meaning.\n");
        }
        if (surfaceValue.startsWith("entity.")) {
            prompt.append("- This is an entity name. Keep genuine player/account names unchanged. Localize NPC names, ")
                    .append("merchant/service roles, creature titles and other server-authored entity names according to ")
                    .append("the active scope and player translation profile; never interpret a service title as a ")
                    .append("programming identifier.\n");
        }
        if (structuralRetry) {
            prompt.append("STRUCTURAL CORRECTION RETRY: the previous non-empty answer violated the Component JSON ")
                    .append("shape. Recount the input top-level elements before translating, then recount the output ")
                    .append("before finishing. Do not merge adjacent phrases into one element and do not split one ")
                    .append("element into multiple top-level elements. Return only the corrected JSON array.\n");
        }
        if (partitionRecovery) {
            prompt.append("COMPONENT PARTITION RECOVERY: the user array is one contiguous partition of a larger ")
                    .append("source document. Preserve this partition's exact top-level array length. If one translated ")
                    .append("slot needs multiple styled fragments, place them inside that slot's nested extra array; ")
                    .append("never create another top-level element. The complete source document remains available ")
                    .append("in context for terminology and sentence meaning.\n");
        }
        if (isChineseTarget(targetLanguage)) {
            prompt.append("Examples:\n");
            prompt.append("Input: [\"Steve found a \",\"Diamond Sword\"]\n");
            prompt.append("Output: [\"Steve 找到了一把\",\"钻石剑\"]\n");
            prompt.append("Input: [\"Enemies will come from three directions:\"]\n");
            prompt.append("Output: [\"敌人将从三个方向进攻：\"]\n");
            if (itemTooltipSurface) {
                prompt.append("Input: [\"This item's power has been sealed,\",\"an\",\"Item Identifier\",\"can unlock\",\"its potential.\"]\n");
                prompt.append("Output: [\"这件物品的力量已被封印，\",\"将它带给\",\"物品鉴定师\",\"即可解锁\",\"其潜力。\"]\n");
                prompt.append("For a quest shaped as surviving for a duration or winning across a game count, the trailing game-count scope applies to the whole condition, not just the nearest verb. Keep every ordinary quantity exactly once in the translated objective.\n");
                prompt.append("Translate badge text such as COMMON / WEEKLY QUEST, and phrase a leading retained count plus 'remaining' as a natural number-first availability label, not the literal word order '数量 + 剩余'.\n");
            }
        }
        if (termHints != null && !termHints.isEmpty()) {
            StringBuilder mandatory = new StringBuilder();
            StringBuilder keepOriginal = new StringBuilder();
            for (com.yourname.simpletranslate.api.TranslationRequest.Term term : termHints) {
                if (term == null || term.source() == null || term.target() == null
                        || term.source().isBlank() || term.target().isBlank()) {
                    continue;
                }
                if (term.source().equals(term.target())) {
                    keepOriginal.append("- \"").append(term.source()).append("\"\n");
                } else {
                    mandatory.append("- \"").append(term.source()).append("\" -> \"")
                            .append(term.target()).append("\"\n");
                }
            }
            if (mandatory.length() > 0) {
                prompt.append("MANDATORY TERMINOLOGY (overrides all other translation rules): render each source ")
                        .append("term EXACTLY as its target wherever it appears; never transliterate, localize ")
                        .append("or paraphrase it differently.\n").append(mandatory);
            }
            if (keepOriginal.length() > 0) {
                prompt.append("Keep these terms in their original form, unchanged: the player explicitly wants ")
                        .append("them left as-is, never translated or transliterated.\n").append(keepOriginal);
            }
        }
        TranslationPromptPolicy.appendSharedSections(prompt, promptContext);
        return prompt.toString().trim();
    }

    /**
     * Human-readable form of the surface, used only in the request-specific
     * tail: the shared rule block must not name any surface.
     */
    private static String describeSurface(String surfaceValue, boolean itemTooltipSurface, boolean wholeGuiFrame,
                                          boolean wynnNpcNameplateSurface) {
        if (itemTooltipSurface) {
            return "one item tooltip";
        }
        if (wholeGuiFrame) {
            return "one visible GUI draw frame";
        }
        if (surfaceValue.startsWith("hover.context")) {
            return "one chat hover tooltip";
        }
        if (surfaceValue.startsWith("sign.manual")) {
            return "manually selected Minecraft signs";
        }
        if (surfaceValue.startsWith("sign.auto")) {
            return "one Minecraft sign";
        }
        if (surfaceValue.startsWith("chat.")) {
            return "chat messages";
        }
        if (wynnNpcNameplateSurface) {
            return "one Wynncraft NPC, merchant, or service nameplate";
        }
        return "one Minecraft text surface";
    }

    private static boolean isChineseTarget(String targetLanguage) {
        String code = TranslationTextDetector.canonicalLanguageCode(targetLanguage);
        return code != null && code.toLowerCase(Locale.ROOT).startsWith("zh");
    }

    private static boolean callerContextContains(String promptContext, String marker) {
        if (promptContext == null || promptContext.isBlank() || marker == null || marker.isEmpty()) {
            return false;
        }
        if (promptContext.contains(marker)) {
            return true;
        }
        try {
            JsonElement parsed = JsonParser.parseString(promptContext);
            if (!parsed.isJsonObject()) {
                return false;
            }
            JsonObject metadata = parsed.getAsJsonObject();
            JsonElement callerContext = metadata.get("caller_context");
            return callerContext != null
                    && callerContext.isJsonPrimitive()
                    && callerContext.getAsString().contains(marker);
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
