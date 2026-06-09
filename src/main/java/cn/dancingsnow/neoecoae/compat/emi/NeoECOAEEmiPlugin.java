package cn.dancingsnow.neoecoae.compat.emi;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.gui.ldlib.NEIntegratedWorkingStationLDLibUI;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.Minecraft;

@EmiEntrypoint
public class NeoECOAEEmiPlugin implements EmiPlugin {

    public static final EmiRecipeCategory INTEGRATED_WORKING_STATION = new EmiRecipeCategory(
            NeoECOAE.id("integrated_working_station"), EmiStack.of(NEBlocks.INTEGRATED_WORKING_STATION));

    public static final EmiRecipeCategory COOLING =
            new EmiRecipeCategory(NeoECOAE.id("cooling"), EmiStack.of(NEBlocks.CRAFTING_SYSTEM_L9));

    public static final EmiRecipeCategory MULTIBLOCK =
            new EmiRecipeCategory(NeoECOAE.id("multiblock"), EmiStack.of(NEBlocks.STORAGE_SYSTEM_L4));

    @Override
    public void register(EmiRegistry registry) {
        registry.addExclusionArea(
                NEIntegratedWorkingStationLDLibUI.class, (screen, consumer) -> screen.getJeiExtraAreas()
                        .forEach(area -> consumer.accept(
                                new Bounds(area.getX(), area.getY(), area.getWidth(), area.getHeight()))));

        registry.addCategory(INTEGRATED_WORKING_STATION);
        registry.addWorkstation(INTEGRATED_WORKING_STATION, EmiStack.of(NEBlocks.INTEGRATED_WORKING_STATION));

        registry.addCategory(COOLING);
        registry.addWorkstation(COOLING, EmiStack.of(NEBlocks.CRAFTING_SYSTEM_L4));
        registry.addWorkstation(COOLING, EmiStack.of(NEBlocks.CRAFTING_SYSTEM_L6));
        registry.addWorkstation(COOLING, EmiStack.of(NEBlocks.CRAFTING_SYSTEM_L9));

        registry.addCategory(MULTIBLOCK);
        registry.addWorkstation(MULTIBLOCK, EmiStack.of(NEBlocks.STORAGE_SYSTEM_L4));
        registry.addWorkstation(MULTIBLOCK, EmiStack.of(NEBlocks.STORAGE_SYSTEM_L6));
        registry.addWorkstation(MULTIBLOCK, EmiStack.of(NEBlocks.STORAGE_SYSTEM_L9));
        registry.addWorkstation(MULTIBLOCK, EmiStack.of(NEBlocks.CRAFTING_SYSTEM_L4));
        registry.addWorkstation(MULTIBLOCK, EmiStack.of(NEBlocks.CRAFTING_SYSTEM_L6));
        registry.addWorkstation(MULTIBLOCK, EmiStack.of(NEBlocks.CRAFTING_SYSTEM_L9));
        registry.addWorkstation(MULTIBLOCK, EmiStack.of(NEBlocks.COMPUTATION_SYSTEM_L4));
        registry.addWorkstation(MULTIBLOCK, EmiStack.of(NEBlocks.COMPUTATION_SYSTEM_L6));
        registry.addWorkstation(MULTIBLOCK, EmiStack.of(NEBlocks.COMPUTATION_SYSTEM_L9));
        for (MultiBlockDefinition definition : NEMultiBlocks.DEFINITIONS) {
            registry.addRecipe(new MultiblockEmiRecipe(definition));
        }

        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        for (IntegratedWorkingStationRecipe recipe :
                mc.level.getRecipeManager().getAllRecipesFor(NERecipeTypes.INTEGRATED_WORKING_STATION.get())) {
            registry.addRecipe(new IntegratedWorkingStationEmiRecipe(recipe));
        }

        for (CoolingRecipe recipe : mc.level.getRecipeManager().getAllRecipesFor(NERecipeTypes.COOLING.get())) {
            registry.addRecipe(new CoolingEmiRecipe(recipe));
        }
    }
}
