package cn.dancingsnow.neoecoae.integration.jei;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.forge.ForgeTypes;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Small client-side bridge for the JEI bookmark list (the list itself is not exposed by JEI's public API). */
final class JeiBookmarkAccessImpl {
    private JeiBookmarkAccessImpl() {}

    public static boolean isAvailable() {
        return NeoECOAEJeiPlugin.runtime() != null;
    }

    public static List<AEKey> bookmarkedKeys() {
        try {
            var runtime = NeoECOAEJeiPlugin.runtime();
            if (runtime == null) return List.of();
            Object overlay = runtime.getBookmarkOverlay();
            Field listField = overlay.getClass().getDeclaredField("bookmarkList");
            listField.setAccessible(true);
            Object list = listField.get(overlay);
            Method elements = list.getClass().getMethod("getElements");
            List<?> result = (List<?>) elements.invoke(list);
            return result.stream().map(JeiBookmarkAccessImpl::elementKey).filter(java.util.Objects::nonNull).distinct()
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger().warn("Failed to read JEI bookmarks", failure);
            return List.of();
        }
    }

    public static void addMissingToBookmarks(List<? extends AEKey> keys) {
        try {
            var runtime = NeoECOAEJeiPlugin.runtime();
            if (runtime == null) return;
            Object overlay = runtime.getBookmarkOverlay();
            Field listField = overlay.getClass().getDeclaredField("bookmarkList");
            listField.setAccessible(true);
            Object list = listField.get(overlay);
            // JEI 15 exposes this operation on BookmarkList itself; the newer bookmarkFactory field
            // does not exist on 1.20.1. This path also notifies the overlay and saves the bookmark file.
            Method add = list.getClass().getMethod("addIngredientBookmark", ITypedIngredient.class);
            for (AEKey key : keys) {
                if (key instanceof AEItemKey itemKey) {
                    ItemStack stack = itemKey.toStack();
                    var typed = runtime.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, stack, false);
                    if (typed.isPresent()) add.invoke(list, typed.get());
                } else if (key instanceof AEFluidKey fluidKey) {
                    var typed = runtime.getIngredientManager().createTypedIngredient(
                        ForgeTypes.FLUID_STACK, fluidKey.toStack(1), false);
                    if (typed.isPresent()) add.invoke(list, typed.get());
                }
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger().warn("Failed to save missing ingredients to JEI bookmarks", failure);
        }
    }

    private static AEKey elementKey(Object element) {
        try {
            Object typed = element.getClass().getMethod("getTypedIngredient").invoke(element);
            return ingredientKey((ITypedIngredient<?>) typed);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    static AEKey ingredientKey(ITypedIngredient<?> ingredient) {
        var item = ingredient.getIngredient(VanillaTypes.ITEM_STACK);
        if (item.isPresent()) {
            var stack = GenericStack.fromItemStack(item.get());
            return stack == null ? null : stack.what();
        }
        var fluid = ingredient.getIngredient(ForgeTypes.FLUID_STACK);
        if (fluid.isPresent()) {
            var stack = GenericStack.fromFluidStack(fluid.get());
            return stack == null ? null : stack.what();
        }

        // The optional AE2 JEI addon owns the converters registered by custom key providers
        // (for example Applied Mekanistics). Keep it optional, just like JEI itself.
        try {
            Class<?> registry = Class.forName("tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverters");
            Class<?> converterType = Class.forName("tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverter");
            var converters = (List<?>) registry.getMethod("getConverters").invoke(null);
            for (Object converter : converters) {
                if (converterType.getMethod("getIngredientType").invoke(converter) == ingredient.getType()) {
                    var stack = (GenericStack) converterType.getMethod("getStackFromIngredient", Object.class)
                        .invoke(converter, ingredient.getIngredient());
                    return stack == null ? null : stack.what();
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // Unsupported JEI ingredients do not have a corresponding AE key.
        }
        return null;
    }
}
