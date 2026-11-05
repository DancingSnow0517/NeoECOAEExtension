package cn.dancingsnow.neoecoae.client.gui.crafting;

import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;

/** Uses EAEP's configured shortcut and viewer lookups without linking its optional classes. */
final class EAEPQuickSearch {
    private EAEPQuickSearch() { }

    private static final class Binding {
        static final KeyMapping KEY = loadBinding();
    }

    static Optional<String> query(int keyCode, int scanCode, int modifiers) {
        var mods = ModList.get();
        if (mods == null || !mods.isLoaded("extendedae_plus")
                || Binding.KEY == null || !Binding.KEY.matches(keyCode, scanCode)) return Optional.empty();
        try {
            if (mods.isLoaded("emi")) {
                return itemQuery((ItemStack) Emi.HOVERED.invoke(null), modifiers);
            }
            if (!mods.isLoaded("jei")) return Optional.empty();
            var minecraft = Minecraft.getInstance();
            double mouseX = minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth()
                    / minecraft.getWindow().getScreenWidth();
            double mouseY = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight()
                    / minecraft.getWindow().getScreenHeight();
            return jeiQuery(mouseX, mouseY, modifiers);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    static Optional<String> jeiQuery(double mouseX, double mouseY, int modifiers) throws ReflectiveOperationException {
        Optional<?> hovered = (Optional<?>) Jei.HOVERED_AT.invoke(null, mouseX, mouseY);
        if (hovered.isEmpty()) hovered = (Optional<?>) Jei.HOVERED.invoke(null);
        if (hovered.isEmpty()) return Optional.empty();
        Object typed = hovered.get();
        if (Jei.VALUE.invoke(null, typed) instanceof ItemStack stack) return itemQuery(stack, modifiers);
        String name = (String) Jei.NAME.invoke(null, typed);
        return name == null || name.isEmpty() ? Optional.empty() : Optional.of(name);
    }

    private static Optional<String> itemQuery(ItemStack stack, int modifiers) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        return Optional.of((modifiers & GLFW.GLFW_MOD_ALT) != 0
                ? "@" + BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace()
                : stack.getHoverName().getString());
    }

    private static KeyMapping loadBinding() {
        try {
            return (KeyMapping) Class.forName("com.extendedae_plus.client.ModKeybindings")
                    .getField("FILL_SEARCH_KEY").get(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Method method(String className, String legacyClassName, String name, Class<?>... parameters) {
        try {
            Class<?> helper;
            try {
                helper = Class.forName(className);
            } catch (ClassNotFoundException ignored) {
                // EAEP 1.6.4 moved viewer helpers into separate JEI/EMI packages.
                helper = Class.forName(legacyClassName);
            }
            return helper.getMethod(name, parameters);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unavailable EAEP search helper: " + className + '.' + name, failure);
        }
    }

    private static final class Emi {
        static final Method HOVERED = method("com.extendedae_plus.compat.emi.EmiHelper",
                "com.extendedae_plus.compat.EmiHelper", "getIngredientUnderMouse");
    }

    private static final class Jei {
        private static final String HELPER = "com.extendedae_plus.compat.jei.JeiRuntimeCompat";
        private static final String LEGACY_HELPER = "com.extendedae_plus.compat.JeiRuntimeCompat";
        static final Method HOVERED_AT = method(HELPER, LEGACY_HELPER, "getIngredientUnderMouse", double.class, double.class);
        static final Method HOVERED = method(HELPER, LEGACY_HELPER, "getIngredientUnderMouse");
        static final Method VALUE = method(HELPER, LEGACY_HELPER, "getTypedIngredientValue", Object.class);
        static final Method NAME = method(HELPER, LEGACY_HELPER, "getTypedIngredientDisplayName", Object.class);
    }
}
