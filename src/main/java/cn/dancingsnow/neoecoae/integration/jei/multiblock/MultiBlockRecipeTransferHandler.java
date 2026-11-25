package cn.dancingsnow.neoecoae.integration.jei.multiblock;

import cn.dancingsnow.neoecoae.integration.ae2.keyreuse.ECOKeyFactory;

import appeng.menu.me.items.PatternEncodingTermMenu;
import cn.dancingsnow.neoecoae.integration.common.xei.multiblock.MultiBlockInfoWrapper;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.RecipeTransferResult;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public final class MultiBlockRecipeTransferHandler
    implements IRecipeTransferHandler<PatternEncodingTermMenu, MultiBlockInfoWrapper> {

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
        throw new UnsupportedOperationException("Recipe transfer requires IRecipeTransferContext");
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(
        IRecipeTransferContext<MultiBlockInfoWrapper, PatternEncodingTermMenu> context,
        boolean doTransfer
    ) {
        if (!doTransfer) return null;
        try {
            var recipe = context.getRecipe();
            var inputs = recipe.getRequiredItems().stream()
                .filter(requiredItem -> !requiredItem.isEmpty())
                .map(requiredItem -> ECOKeyFactory.fromItemStack(requiredItem.stackWithCount()))
                .filter(stack -> stack != null)
                .map(List::of)
                .toList();
            var output = ECOKeyFactory.fromItemStack(
                recipe.getDefinition().getOwner().value().asItem().getDefaultInstance()
            );
            appeng.integration.modules.itemlists.EncodingHelper.encodeProcessingRecipe(
                context.getContainer(),
                inputs,
                output == null ? List.of() : List.of(output)
            );
        } catch (RuntimeException failure) {
            context.completeRecipeTransfer(RecipeTransferResult.REJECTED);
            throw failure;
        }
        context.completeRecipeTransfer(RecipeTransferResult.SUCCESS);
        return null;
    }
}
