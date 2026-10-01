package cn.dancingsnow.neoecoae.integration.emi;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.integration.emi.recipe.CoolingEmiRecipe;
import cn.dancingsnow.neoecoae.integration.emi.recipe.IntegrationWorkingStationEmiRecipe;
import cn.dancingsnow.neoecoae.integration.emi.recipe.MultiblockEmiRecipe;
import cn.dancingsnow.neoecoae.client.ECOCraftConfirmScreen;
import cn.dancingsnow.neoecoae.crafting.graph.client.ECOCraftingGraphScreen;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipes;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.client.Minecraft;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.neoforge.NeoForgeEmiStack;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

@EmiEntrypoint
public class NeoECOAEEmiPlugin implements EmiPlugin {

    public static final EmiRecipeCategory MULTIBLOCK = new EmiRecipeCategory(NeoECOAE.id("multiblock"), EmiStack.of(NEBlocks.STORAGE_SYSTEM_L4));
    public static final EmiRecipeCategory INTEGRATED_WORKING_STATION = new EmiRecipeCategory(NeoECOAE.id("integrated_working_station"), EmiStack.of(NEBlocks.INTEGRATED_WORKING_STATION));
    public static final EmiRecipeCategory COOLING = new EmiRecipeCategory(NeoECOAE.id("cooling"), EmiStack.of(NEBlocks.CRAFTING_SYSTEM_L9));
    public static final EmiRecipeCategory LARGE_WORKING_STATION = new EmiRecipeCategory(
        NeoECOAE.id("large_integrated_working_station"), EmiStack.of(NEBlocks.INTEGRATED_WORKING_STATION));

    @Override
    public void register(EmiRegistry registry) {
        // ECO's planner/report and graph screens draw AE keys directly on a canvas rather than in
        // vanilla slots. Expose those custom hit regions so EMI's recipe and usage actions work there.
        registry.addStackProvider(ECOCraftConfirmScreen.class,
            (screen, x, y) -> interaction(screen.getECOStackAt(x, y)));
        registry.addStackProvider(ECOCraftingGraphScreen.class,
            (screen, x, y) -> interaction(screen.getECOStackAt(x, y)));

        // multiblock
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

        // integrated working station
        registry.addCategory(INTEGRATED_WORKING_STATION);
        registry.addWorkstation(INTEGRATED_WORKING_STATION, EmiStack.of(NEBlocks.INTEGRATED_WORKING_STATION));
        for (RecipeHolder<IntegratedWorkingStationRecipe> holder : registry.getRecipeManager().getAllRecipesFor(NERecipeTypes.INTEGRATED_WORKING_STATION.get())) {
            registry.addRecipe(new IntegrationWorkingStationEmiRecipe(holder));
        }

        // large integrated working station
        registry.addCategory(LARGE_WORKING_STATION);
        registry.addWorkstation(LARGE_WORKING_STATION, EmiStack.of(NEBlocks.INTEGRATED_WORKING_STATION));
        registry.addWorkstation(LARGE_WORKING_STATION, EmiStack.of(NEBlocks.LARGE_INTEGRATED_WORKING_STATION_INTERFACE));
        var level = Minecraft.getInstance().level;
        if (level != null) {
            for (var recipe : LargeWorkstationRecipes.getAll(registry.getRecipeManager(), level.registryAccess())) {
                registry.addRecipe(new IntegrationWorkingStationEmiRecipe(recipe));
            }
        }

        // cooling
        registry.addCategory(COOLING);
        registry.addWorkstation(COOLING, EmiStack.of(NEBlocks.CRAFTING_SYSTEM_L4));
        registry.addWorkstation(COOLING, EmiStack.of(NEBlocks.CRAFTING_SYSTEM_L6));
        registry.addWorkstation(COOLING, EmiStack.of(NEBlocks.CRAFTING_SYSTEM_L9));
        for (RecipeHolder<CoolingRecipe> holder : registry.getRecipeManager().getAllRecipesFor(NERecipeTypes.COOLING.get())) {
            registry.addRecipe(new CoolingEmiRecipe(holder));
        }
    }

    public static EmiIngredient of(@NotNull SizedIngredient ingredient) {
        return EmiIngredient.of(ingredient.ingredient(), ingredient.count());
    }

    public static EmiIngredient of(@NotNull SizedFluidIngredient ingredient) {
        List<EmiStack> list = Arrays.stream(ingredient.getFluids()).map(stack -> EmiStack.of(stack.getFluid())).toList();
        return EmiIngredient.of(list, ingredient.amount());
    }

    private static EmiStackInteraction interaction(AEKey key) {
        if (key instanceof AEItemKey item) {
            return new EmiStackInteraction(EmiStack.of(item.toStack()), null, false);
        }
        if (key instanceof AEFluidKey fluid) {
            return new EmiStackInteraction(NeoForgeEmiStack.of(fluid.toStack(1)), null, false);
        }
        // EMI has no generic representation for AE2's optional/custom key types (energy, chemicals,
        // etc.). Leave those untouched so the screen's native tooltip remains authoritative.
        return EmiStackInteraction.EMPTY;
    }
}
