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
}
