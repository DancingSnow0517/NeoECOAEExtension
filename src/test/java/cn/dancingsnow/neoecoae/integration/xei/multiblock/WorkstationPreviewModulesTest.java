package cn.dancingsnow.neoecoae.integration.xei.multiblock;

import cn.dancingsnow.neoecoae.api.workstation.ECOWorkstationModules;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationModule;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.Set;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkstationPreviewModulesTest {
    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test void onlyPresentCardsAreListedAndBothDirectionsWrapIncludingAddonModels() {
        var addon = module("present", Identifier.parse("minecraft:paper"));
        var absent = module("absent", Identifier.parse("preview_test:missing_card"));
        ECOWorkstationModules.register(0, addon);
        ECOWorkstationModules.register(0, absent);
        try {
            var modules = WorkstationPreviewModules.available();
            assertEquals(LargeWorkstationModule.REGULAR, modules.getFirst().legacy());
            assertTrue(modules.contains(addon));
            assertFalse(modules.contains(absent));
            assertTrue(modules.stream().noneMatch(m -> !m.custom() && m.legacy() == LargeWorkstationModule.OTHER));
            for (int i = 0; i < modules.size(); i++) {
                assertEquals(modules.get((i + 1) % modules.size()), WorkstationPreviewModules.cycle(modules.get(i).id(), 1));
                assertEquals(modules.get(Math.floorMod(i - 1, modules.size())), WorkstationPreviewModules.cycle(modules.get(i).id(), -1));
            }
            assertTrue(WorkstationPreviewModules.requiredCard(modules.getFirst()).isEmpty());
            assertSame(BuiltInRegistries.ITEM.get(addon.upgradeCard()), WorkstationPreviewModules.requiredCard(addon).getItem());
            assertTrue(WorkstationPreviewModules.requiredCard(absent).isEmpty());
            assertEquals(addon.idleModel(), WorkstationPreviewModules.cycle(
                modules.get(modules.indexOf(addon) - 1).id(), 1).idleModel());
        } finally {
            ECOWorkstationModules.unregister(addon.id());
            ECOWorkstationModules.unregister(absent.id());
        }
        assertFalse(WorkstationPreviewModules.available().contains(addon));
    }

    @Test void builtinChoicesFollowActualItemRegistration() {
        var builtin = LargeWorkstationModule.APPLIED_GENERATORS;
        var registry = (MappedRegistry<Item>) BuiltInRegistries.ITEM;
        if (!registry.containsKey(builtin.upgradeCardId())) {
            assertFalse(WorkstationPreviewModules.available().stream().anyMatch(m -> m.id().equals(ECOWorkstationModules.builtin(builtin).id())));
            registry.unfreeze(false);
            try {
                Registry.register(registry, builtin.upgradeCardId(), new Item(new Item.Properties()));
            } finally {
                registry.freeze();
            }
        }
        assertTrue(WorkstationPreviewModules.available().stream().anyMatch(m -> m.id().equals(ECOWorkstationModules.builtin(builtin).id())));
    }

    private static ECOWorkstationModules.Module module(String name, Identifier card) {
        return new ECOWorkstationModules.Module(Identifier.parse("preview_test:" + name), card, Set.of(),
            Component.literal(name), Component.literal(name), 0xffffff, null, null,
            Identifier.parse("preview_test:block/idle"), Identifier.parse("preview_test:block/working"));
    }
}
