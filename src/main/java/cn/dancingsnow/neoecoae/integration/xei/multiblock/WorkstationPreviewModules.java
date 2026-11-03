package cn.dancingsnow.neoecoae.integration.xei.multiblock;

import cn.dancingsnow.neoecoae.recipe.LargeWorkstationModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** Resolve preview choices from the upgrade cards actually registered in this client. */
final class WorkstationPreviewModules {
    private WorkstationPreviewModules() {}

    static List<LargeWorkstationModule> available() {
        var modules = new ArrayList<LargeWorkstationModule>();
        modules.add(LargeWorkstationModule.REGULAR);
        for (var builtin : LargeWorkstationModule.values()) {
            if (builtin != LargeWorkstationModule.REGULAR && builtin.hasUpgradeCard()
                && BuiltInRegistries.ITEM.containsKey(builtin.upgradeCardId())) {
                modules.add(builtin);
            }
        }
        return List.copyOf(modules);
    }

    static LargeWorkstationModule cycle(LargeWorkstationModule current, int step) {
        var modules = available();
        int index = Math.max(0, modules.indexOf(current));
        return modules.get(Math.floorMod(index + step, modules.size()));
    }

    static ItemStack requiredCard(LargeWorkstationModule module) {
        if (module == LargeWorkstationModule.REGULAR || !module.hasUpgradeCard()
            || !BuiltInRegistries.ITEM.containsKey(module.upgradeCardId())) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.get(module.upgradeCardId()));
    }
}
