package cn.dancingsnow.neoecoae.integration.xei.multiblock;

import cn.dancingsnow.neoecoae.api.workstation.ECOWorkstationModules;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Resolve preview choices from the cards actually present in this client. */
final class WorkstationPreviewModules {
    private WorkstationPreviewModules() {}

    static List<ECOWorkstationModules.Module> available() {
        var modules = new ArrayList<ECOWorkstationModules.Module>();
        modules.add(ECOWorkstationModules.builtin(LargeWorkstationModule.REGULAR));
        for (var builtin : LargeWorkstationModule.values()) {
            if (builtin != LargeWorkstationModule.REGULAR && builtin.hasUpgradeCard()
                && BuiltInRegistries.ITEM.containsKey(builtin.upgradeCardId())) {
                modules.add(ECOWorkstationModules.builtin(builtin));
            }
        }
        for (var custom : ECOWorkstationModules.registered()) {
            if (BuiltInRegistries.ITEM.containsKey(custom.upgradeCard())) modules.add(custom);
        }
        return List.copyOf(modules);
    }

    static ECOWorkstationModules.Module cycle(ResourceLocation current, int step) {
        var modules = available();
        int index = 0;
        for (int i = 0; i < modules.size(); i++) {
            if (modules.get(i).id().equals(current)) { index = i; break; }
        }
        return modules.get(Math.floorMod(index + step, modules.size()));
    }

    static ItemStack requiredCard(ECOWorkstationModules.Module module) {
        if (module.id().equals(ECOWorkstationModules.builtin(LargeWorkstationModule.REGULAR).id())
            || !BuiltInRegistries.ITEM.containsKey(module.upgradeCard())) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.get(module.upgradeCard()));
    }
}
