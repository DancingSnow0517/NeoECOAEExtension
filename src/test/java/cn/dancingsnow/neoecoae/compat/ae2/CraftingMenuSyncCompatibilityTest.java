package cn.dancingsnow.neoecoae.compat.ae2;

import static org.junit.jupiter.api.Assertions.*;

import appeng.menu.guisync.DataSynchronization;
import appeng.menu.guisync.GuiSync;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

class CraftingMenuSyncCompatibilityTest {
    @Test
    void ae2RejectsThePreviousGtlEcoCollisionBeforeTheMenuCanOpen() {
        var failure = assertThrows(IllegalStateException.class, () -> new DataSynchronization(new CollidingMenu()));
        assertTrue(failure.getMessage().contains("100"));
    }

    @Test
    void confirmationFieldsDoNotOverlapGtlOrAe2Fields() throws Exception {
        var used = new HashSet<Integer>();
        // GTLCore IConfirmStartMenu.GUI_SYNC_MISSING_CRAFT_AVAILABLE, verified in the installed jar.
        used.add(100);
        collectIds("appeng/menu/AEBaseMenu", used);
        collectIds("appeng/menu/me/crafting/CraftConfirmMenu", used);
        int previous = used.size();
        collectIds("cn/dancingsnow/neoecoae/mixins/CraftConfirmMenuMixin", used);
        assertEquals(previous + 8, used.size());
    }

    private static void collectIds(String name, HashSet<Integer> used) throws Exception {
        try (var input = CraftingMenuSyncCompatibilityTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input);
            var node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_CODE);
            for (var field : node.fields) {
                if (field.visibleAnnotations == null) continue;
                for (var annotation : field.visibleAnnotations) {
                    if (!annotation.desc.equals("Lappeng/menu/guisync/GuiSync;")) continue;
                    int id = ((Number) annotation.values.get(1)).intValue();
                    assertTrue(used.add(id), () -> "Duplicate sync ID " + id + " in " + name + "." + field.name);
                }
            }
        }
    }

    static class CollidingMenu {
        @GuiSync(100)
        public long ecoCalculationNanos;

        @GuiSync(100)
        public boolean gtlMissingCraftAvailable;
    }
}
