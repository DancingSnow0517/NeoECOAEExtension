package cn.dancingsnow.neoecoae.integration.emi;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import dev.emi.emi.api.forge.ForgeEmiStack;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiFavorites;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

/** Loaded only after the optional EMI mod check. Favorites are not exposed by EMI's public API. */
public final class EmiBookmarkAccess {
    private EmiBookmarkAccess() {}

    public static List<AEKey> bookmarkedKeys() {
        // Only explicit favorites belong in a cell filter, not the generated recipe-tree materials.
        return EmiFavorites.favorites.stream().map(EmiBookmarkAccess::ingredientKey)
            .filter(Objects::nonNull).distinct().toList();
    }

    public static void addMissingToBookmarks(List<? extends AEKey> keys) {
        for (AEKey key : keys) {
            EmiStack stack = key instanceof AEItemKey item ? EmiStack.of(item.toStack())
                : key instanceof AEFluidKey fluid ? ForgeEmiStack.of(fluid.toStack(1)) : EmiStack.EMPTY;
            // Pass a plain stack: passing an EmiFavorite would toggle an existing bookmark off.
            if (!stack.isEmpty()) EmiFavorites.addFavorite(stack);
        }
    }

    static AEKey ingredientKey(EmiIngredient ingredient) {
        var stacks = ingredient.getEmiStacks();
        // A tag/list bookmark does not identify a single exact cell resource.
        if (stacks.size() != 1) return null;
        var stack = stacks.get(0);
        if (stack.isEmpty()) return null;
        if (stack.getKey() instanceof Fluid fluid) {
            var fluidStack = new FluidStack(fluid, 1);
            if (stack.getNbt() != null) fluidStack.setTag(stack.getNbt().copy());
            return AEFluidKey.of(fluidStack);
        }
        var item = stack.getItemStack();
        return item.isEmpty() ? null : AEItemKey.of(item);
    }
}
