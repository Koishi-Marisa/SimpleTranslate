package com.yourname.simpletranslate.core;

import com.google.gson.JsonArray;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonPassthroughPipelineTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        // Decoding a Component through ComponentSerialization.CODEC initializes
        // Style -> ClickEvent -> Dialog -> BuiltInRegistries, and the registry
        // refuses to register anything until the vanilla bootstrap has run.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void wynnDialogueContentRecoversAtEachSemanticSlot() {
        assertEquals(List.of(1, 1, 1, 1),
                JsonPassthroughPipeline.recoveryAtomicGroupSizesForTest(
                        "hud.actionbar.wynn.dialogue.content.paragraph.v5", "",
                        4, List.of(4)));
    }

    @Test
    void ordinarySurfaceKeepsItsAtomicGroups() {
        assertEquals(List.of(2, 2),
                JsonPassthroughPipeline.recoveryAtomicGroupSizesForTest(
                        "gui.component.visible_frame.item_tooltip.v1", "",
                        4, List.of(2, 2)));
    }

    @Test
    void bookSurfaceRemainsOneAtomicRecoveryGroup() {
        assertEquals(List.of(4),
                JsonPassthroughPipeline.recoveryAtomicGroupSizesForTest(
                        "book.page.component.v1", "", 4, List.of(2, 2)));
    }

    @Test
    void semanticProjectionRejectsWrongTopLevelSlotCounts() {
        ComponentVisualProjection projection = ComponentVisualProjection.project(
                "[{\"text\":\"NPC\"},{\"text\":\"Complete dialogue paragraph.\"}]", "zh_cn");

        assertNotNull(projection);
        assertNull(projection.rebuildResponseJson("[{\"text\":\"NPC\"}]"));
        assertNull(projection.rebuildResponseJson(
                "[{\"text\":\"NPC\"},{\"text\":\"段落\"},{\"text\":\"extra\"}]"));

        JsonArray rebuilt = projection.rebuildResponseJson(
                "[{\"text\":\"NPC\"},{\"text\":\"完整的对话段落。\"}]");
        assertNotNull(rebuilt);
        assertEquals(2, rebuilt.size());
    }
}
