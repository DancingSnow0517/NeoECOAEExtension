package cn.dancingsnow.neoecoae.compat.emi;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;

@EmiEntrypoint
public class NeoECOAEEmiPlugin implements EmiPlugin {

    public static final EmiRecipeCategory INTEGRATED_WORKING_STATION = new EmiRecipeCategory(
        NeoECOAE.id("integrated_working_station"),
        EmiStack.of(NEBlocks.INTEGRATED_WORKING_STATION));

    @Override
    public void register(EmiRegistry registry) {
        // ── Integrated Working Station ──
        registry.addCategory(INTEGRATED_WORKING_STATION);
        registry.addWorkstation(INTEGRATED_WORKING_STATION, EmiStack.of(NEBlocks.INTEGRATED_WORKING_STATION));

        var mc = Minecraft.getInstance();
        if (mc.level == null) return;

        for (IntegratedWorkingStationRecipe recipe :
             mc.level.getRecipeManager().getAllRecipesFor(NERecipeTypes.INTEGRATED_WORKING_STATION.get())) {
            registry.addRecipe(new IntegratedWorkingStationEmiRecipe(recipe));
        }
    }
}
