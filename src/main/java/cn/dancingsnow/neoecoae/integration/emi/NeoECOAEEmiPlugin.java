package cn.dancingsnow.neoecoae.integration.emi;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.integration.emi.recipe.IntegrationWorkingStationEmiRecipe;
import cn.dancingsnow.neoecoae.integration.emi.recipe.MultiblockEmiRecipe;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
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

    @Override
    public void register(EmiRegistry registry) {
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

        registry.addCategory(INTEGRATED_WORKING_STATION);
        registry.addWorkstation(INTEGRATED_WORKING_STATION, EmiStack.of(NEBlocks.INTEGRATED_WORKING_STATION));
        for (RecipeHolder<IntegratedWorkingStationRecipe> holder : registry.getRecipeManager().getAllRecipesFor(NERecipeTypes.INTEGRATED_WORKING_STATION.get())) {
            registry.addRecipe(new IntegrationWorkingStationEmiRecipe(holder));
        }
    }

    public static EmiIngredient of(@NotNull SizedIngredient ingredient) {
        return EmiIngredient.of(ingredient.ingredient(), ingredient.count());
    }

    public static EmiIngredient of(@NotNull SizedFluidIngredient ingredient) {
        List<EmiStack> list = Arrays.stream(ingredient.getFluids()).map(stack -> EmiStack.of(stack.getFluid())).toList();
        return EmiIngredient.of(list, ingredient.amount());
    }
}
