package cn.dancingsnow.neoecoae.integration.emi;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.integration.emi.recipe.CoolingEmiRecipe;
import cn.dancingsnow.neoecoae.integration.emi.recipe.IntegrationWorkingStationEmiRecipe;
import cn.dancingsnow.neoecoae.integration.emi.recipe.MultiblockEmiRecipe;
import cn.dancingsnow.neoecoae.client.gui.crafting.ECOCraftConfirmScreen;
import cn.dancingsnow.neoecoae.client.gui.graph.ECOCraftingGraphScreen;
import cn.dancingsnow.neoecoae.gui.computation.ComputationCpuStatusPanel;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.RetainedUIGuiContainer;
import com.lowdragmc.lowdraglib.emi.ModularUIEmiHandlers;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
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
import dev.emi.emi.api.forge.ForgeEmiStack;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import cn.dancingsnow.neoecoae.platform.recipe.SizedIngredient;
import cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient;
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
        // EMI's default container bounds require an item slot. ECO also has fluid-only menus,
        // such as smart cooling; their native ModularUI already supplies the correct bounds.
        // Class-specific providers match the exact screen class in EMI, not its subclasses.
        // ECO opens RetainedUIGuiContainer, so use the generic hook with our own type guard.
        registry.addGenericScreenBoundsProvider(screen -> {
            if (!(screen instanceof ModularUIGuiContainer container)) return null;
            var ui = container.modularUI;
            if (!(ui.mainGroup instanceof UIElement)) return null;
            float scale = container instanceof RetainedUIGuiContainer retained ? retained.getRenderScale() : 1;
            int left = (int) Math.floor(ui.getGuiLeft() * scale);
            int top = (int) Math.floor(ui.getGuiTop() * scale);
            int right = (int) Math.ceil((ui.getGuiLeft() + ui.getWidth()) * scale);
            int bottom = (int) Math.ceil((ui.getGuiTop() + ui.getHeight()) * scale);
            return new Bounds(left, top, right - left, bottom - top);
        });
        registry.addDragDropHandler(RetainedUIGuiContainer.class, ModularUIEmiHandlers.DRAG_DROP_HANDLER::dropStack);
        registry.addStackProvider(RetainedUIGuiContainer.class, (screen, x, y) -> {
            if (screen.modularUI.mainGroup instanceof ComputationCpuStatusPanel panel) {
                var key = panel.getRecipeViewerKeyOverMouse(x, y);
                if (key != null) return interaction(key);
            }
            return EmiStackInteraction.EMPTY;
        });
        registry.removeRecipes(recipe -> {
            if (cn.dancingsnow.neoecoae.integration.common.xei.HiddenEasterEggRecipes.isHidden(recipe.getId())) return true;
            var backing = recipe.getBackingRecipe();
            return backing != null
                && cn.dancingsnow.neoecoae.integration.common.xei.HiddenEasterEggRecipes.isHidden(backing.getId());
        });
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
        for (IntegratedWorkingStationRecipe holder : registry.getRecipeManager().getAllRecipesFor(NERecipeTypes.INTEGRATED_WORKING_STATION.get())) {
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
        for (CoolingRecipe holder : registry.getRecipeManager().getAllRecipesFor(NERecipeTypes.COOLING.get())) {
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

    public static EmiStackInteraction interaction(AEKey key) {
        if (key instanceof AEItemKey item) {
            return new EmiStackInteraction(EmiStack.of(item.toStack()), null, false);
        }
        if (key instanceof AEFluidKey fluid) {
            return new EmiStackInteraction(ForgeEmiStack.of(fluid.toStack(1)), null, false);
        }
        // EMI has no generic representation for AE2's optional/custom key types (energy, chemicals,
        // etc.). Leave those untouched so the screen's native tooltip remains authoritative.
        return EmiStackInteraction.EMPTY;
    }
}
