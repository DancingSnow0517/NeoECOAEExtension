package cn.dancingsnow.neoecoae.integration.jei;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.integration.xei.jei.LDLibJEIPlugin;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.integration.jei.categories.CoolingCategory;
import cn.dancingsnow.neoecoae.integration.jei.categories.IntegrationWorkingStationCategory;
import cn.dancingsnow.neoecoae.integration.jei.categories.multiblock.MultiBlockInfoCategory;
import cn.dancingsnow.neoecoae.integration.jei.multiblock.MultiBlockRecipeTransferHandler;
import cn.dancingsnow.neoecoae.integration.xei.multiblock.MultiBlockInfoWrapper;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationRecipes;
import cn.dancingsnow.neoecoae.integration.jei.categories.LargeWorkingStationCategory;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import net.minecraft.client.Minecraft;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;
import java.util.Objects;
import java.util.function.Supplier;

@JeiPlugin
public class NeoECOAEJeiPlugin implements IModPlugin {
    @Nullable
    private static volatile IJeiRuntime runtime;
    public static final RecipeType<RecipeHolder<CoolingRecipe>> COOLING_TYPE = createRecipeHolderType("cooling");
    public static final RecipeType<RecipeHolder<IntegratedWorkingStationRecipe>> INTEGRATED_WORKING_STATION_TYPE = createRecipeHolderType("integrated_working_station");
    public static final RecipeType<LargeWorkstationRecipe> LARGE_WORKING_STATION_TYPE = new RecipeType<>(
        NeoECOAE.id("large_integrated_working_station"), LargeWorkstationRecipe.class);

    public static final RecipeType<MultiBlockInfoWrapper> MULTIBLOCK_TYPE = new RecipeType<>(
        NeoECOAE.id("multiblock"),
        MultiBlockInfoWrapper.class
    );

    @Override
    public ResourceLocation getPluginUid() {
        return NeoECOAE.id("jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new CoolingCategory(guiHelper));
        registration.addRecipeCategories(new MultiBlockInfoCategory(guiHelper));
        registration.addRecipeCategories(new IntegrationWorkingStationCategory(guiHelper));
        registration.addRecipeCategories(new LargeWorkingStationCategory(guiHelper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // JEI guards startup with a client level. Bind this whole registration to that one world and recipe set.
        var level = Objects.requireNonNull(Minecraft.getInstance().level,
            "JEI recipe registration requires an active client level");
        var recipes = level.getRecipeManager();
        CoolingCategory.registerRecipes(registration, recipes);
        MultiBlockInfoCategory.registerRecipes(registration);
        IntegrationWorkingStationCategory.registerRecipes(registration, recipes);
        registration.addRecipes(LARGE_WORKING_STATION_TYPE, LargeWorkstationRecipes.getAll(level));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        CoolingCategory.registerRecipeCatalysts(registration);
        MultiBlockInfoCategory.registerRecipeCatalysts(registration);
        IntegrationWorkingStationCategory.registerRecipeCatalysts(registration);
        registration.addRecipeCatalysts(LARGE_WORKING_STATION_TYPE,
            NEBlocks.INTEGRATED_WORKING_STATION, NEBlocks.LARGE_INTEGRATED_WORKING_STATION_INTERFACE);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        NeoECOAEJeiPlugin.runtime = runtime;
    }

    public static @Nullable IJeiRuntime runtime() { return runtime; }

    /** Exposes custom AE-key hit regions through LDLib's normal JEI recipe/usage lookup. */
    public static void bindIngredient(UIElement element, Supplier<@Nullable AEKey> keySupplier) {
        LDLibJEIPlugin.clickableIngredient(element, () -> {
            var key = keySupplier.get();
            return key instanceof AEItemKey item
                ? LDLibJEIPlugin.createTypedIngredient(VanillaTypes.ITEM_STACK, item.toStack()).orElse(null) : null;
        });
        LDLibJEIPlugin.clickableIngredient(element, () -> {
            var key = keySupplier.get();
            return key instanceof AEFluidKey fluid
                ? LDLibJEIPlugin.createTypedIngredient(NeoForgeTypes.FLUID_STACK, fluid.toStack(1)).orElse(null) : null;
        });
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(MultiBlockRecipeTransferHandler.create(), MULTIBLOCK_TYPE);
    }

    public static <R extends Recipe<?>> RecipeType<RecipeHolder<R>> createRecipeHolderType(String name) {
        return RecipeType.createRecipeHolderType(NeoECOAE.id(name));
    }
}
