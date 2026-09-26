package cn.dancingsnow.neoecoae.integration.jei;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Small client-side bridge for the JEI bookmark list (the list itself is not exposed by JEI's public API). */
public final class JeiBookmarkAccess {
    private JeiBookmarkAccess() {}

    public static List<ItemStack> itemBookmarks() {
        try {
            var runtime = NeoECOAEJeiPlugin.runtime();
            if (runtime == null) return List.of();
            Object overlay = runtime.getBookmarkOverlay();
            Field listField = overlay.getClass().getDeclaredField("bookmarkList");
            listField.setAccessible(true);
            Object list = listField.get(overlay);
            Method elements = list.getClass().getMethod("getElements");
            List<?> result = (List<?>) elements.invoke(list);
            return result.stream().map(JeiBookmarkAccess::elementStack).filter(s -> !s.isEmpty()).toList();
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

    private static ItemStack elementStack(Object element) {
        try {
            Object typed = element.getClass().getMethod("getTypedIngredient").invoke(element);
            return ((ITypedIngredient<?>) typed).getIngredient(VanillaTypes.ITEM_STACK).orElse(ItemStack.EMPTY);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }
}
