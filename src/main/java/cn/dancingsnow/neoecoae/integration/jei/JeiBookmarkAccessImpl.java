package cn.dancingsnow.neoecoae.integration.jei;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
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
        } catch (ReflectiveOperationException | RuntimeException ignored) {
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
            Field factoryField = list.getClass().getDeclaredField("bookmarkFactory");
            factoryField.setAccessible(true);
            Object factory = factoryField.get(list);
            Method create = factory.getClass().getMethod("create", ITypedIngredient.class);
            Method add = list.getClass().getMethod("add", Class.forName("mezz.jei.gui.bookmarks.IBookmark"));
            for (AEKey key : keys) {
                if (key instanceof AEItemKey itemKey) {
                    ItemStack stack = itemKey.toStack();
                    var typed = runtime.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, stack, false);
                    if (typed.isPresent()) add.invoke(list, create.invoke(factory, typed.get()));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // JEI is optional and its internal bookmark implementation may change between versions.
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
        var fluid = ingredient.getIngredient(NeoForgeTypes.FLUID_STACK);
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
