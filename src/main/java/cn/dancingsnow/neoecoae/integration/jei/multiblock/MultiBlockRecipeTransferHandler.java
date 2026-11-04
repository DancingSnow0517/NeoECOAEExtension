package cn.dancingsnow.neoecoae.integration.jei.multiblock;

import appeng.api.stacks.GenericStack;
import appeng.menu.me.items.PatternEncodingTermMenu;
import cn.dancingsnow.neoecoae.integration.xei.multiblock.MultiBlockInfoWrapper;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class MultiBlockRecipeTransferHandler
    implements IRecipeTransferHandler<PatternEncodingTermMenu, MultiBlockInfoWrapper> {

    /** Keep newer JEI types out of the legacy handler's descriptors and class initialization. */
    public static MultiBlockRecipeTransferHandler create() {
        try {
            var context = Class.forName("mezz.jei.api.recipe.transfer.IRecipeTransferContext", false,
                MultiBlockRecipeTransferHandler.class.getClassLoader());
            var result = Class.forName("mezz.jei.api.recipe.transfer.RecipeTransferResult", false,
                MultiBlockRecipeTransferHandler.class.getClassLoader());
            context.getMethod("completeRecipeTransfer", result);
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError unavailable) {
            return new MultiBlockRecipeTransferHandler();
        }
        return ContextApi.create();
    }

    @Override
    public Class<? extends PatternEncodingTermMenu> getContainerClass() {
        return PatternEncodingTermMenu.class;
    }

    @Override
    public Optional<net.minecraft.world.inventory.MenuType<PatternEncodingTermMenu>> getMenuType() {
        return Optional.empty();
    }

    @Override
    public mezz.jei.api.recipe.RecipeType<MultiBlockInfoWrapper> getRecipeType() {
        return cn.dancingsnow.neoecoae.integration.jei.NeoECOAEJeiPlugin.MULTIBLOCK_TYPE;
    }

    // Keep the pre-19.52 signature for older JEI and already compiled integrations.
    // The context adapter below reuses the same synchronous encoding implementation.
    @SuppressWarnings("removal")
    @Override
    public @Nullable IRecipeTransferError transferRecipe(
        PatternEncodingTermMenu menu,
        MultiBlockInfoWrapper recipe,
        IRecipeSlotsView recipeSlots,
        Player player,
        boolean maxTransfer,
        boolean doTransfer
    ) {
        if (doTransfer) {
            var inputs = recipe.getRequiredItems().stream()
                .filter(requiredItem -> !requiredItem.isEmpty())
                .map(requiredItem -> GenericStack.fromItemStack(requiredItem.stackWithCount()))
                .filter(stack -> stack != null)
                .map(List::of)
                .toList();
            var output = GenericStack.fromItemStack(
                recipe.getDefinition().getOwner().value().asItem().getDefaultInstance()
            );
            appeng.integration.modules.itemlists.EncodingHelper.encodeProcessingRecipe(
                menu,
                inputs,
                output == null ? List.of() : List.of(output)
            );
        }
        return null;
    }

    private static final class ContextApi {
        // Returning the legacy type prevents verification of create() from eagerly resolving ContextAware.
        private static MultiBlockRecipeTransferHandler create() { return new ContextAware(); }
    }

    private static final class ContextAware extends MultiBlockRecipeTransferHandler {
        @Override
        public @Nullable IRecipeTransferError transferRecipe(
            mezz.jei.api.recipe.transfer.IRecipeTransferContext<MultiBlockInfoWrapper, PatternEncodingTermMenu> context,
            boolean doTransfer
        ) {
            IRecipeTransferError error;
            try {
                error = super.transferRecipe(context.getContainer(), context.getRecipe(), context.getRecipeSlots(),
                    context.getPlayer(), context.isMaxTransfer(), doTransfer);
            } catch (RuntimeException failure) {
                if (doTransfer) context.completeRecipeTransfer(mezz.jei.api.recipe.transfer.RecipeTransferResult.REJECTED);
                throw failure;
            }
            if (doTransfer) context.completeRecipeTransfer(error == null
                ? mezz.jei.api.recipe.transfer.RecipeTransferResult.SUCCESS
                : mezz.jei.api.recipe.transfer.RecipeTransferResult.REJECTED);
            return error;
        }
    }
}
