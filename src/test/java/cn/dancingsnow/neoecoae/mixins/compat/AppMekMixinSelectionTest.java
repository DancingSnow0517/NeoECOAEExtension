package cn.dancingsnow.neoecoae.mixins.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URL;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;

class AppMekMixinSelectionTest {
    private static final String PLUGIN = ECOOptionalAddonMixinPlugin.class.getName();
    private static final String PACKAGE = "cn.dancingsnow.neoecoae.mixins.compat.appmek";
    private static final String TARGET = "me.ramidzkh.mekae2.ae2.stack.MekanismExternalStorageStrategy";
    private static final String MEKANISM = "mekanism/api/chemical/IChemicalHandler.class";

    @Test
    void optionalChemistryHookRequiresBothModsWithoutLoadingTheirClasses() throws Exception {
        for (boolean appmek : new boolean[] {false, true}) {
            for (boolean mekanism : new boolean[] {false, true}) {
                var loader = new PluginLoader(appmek, mekanism);
                var plugin = (IMixinConfigPlugin) loader.loadClass(PLUGIN).getDeclaredConstructor().newInstance();
                plugin.onLoad(PACKAGE);
                assertEquals(appmek && mekanism,
                    plugin.shouldApplyMixin(TARGET, PACKAGE + ".MekanismExternalStorageStrategyMixin"));
                assertFalse(loader.loadedOptionalClass);
            }
        }
    }

    private static final class PluginLoader extends ClassLoader {
        private final boolean appmek;
        private final boolean mekanism;
        private boolean loadedOptionalClass;

        private PluginLoader(boolean appmek, boolean mekanism) {
            super(AppMekMixinSelectionTest.class.getClassLoader());
            this.appmek = appmek;
            this.mekanism = mekanism;
        }

        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("me.ramidzkh.mekae2.") || name.startsWith("mekanism.")) {
                loadedOptionalClass = true;
                throw new ClassNotFoundException(name);
            }
            if (!name.equals(PLUGIN)) return super.loadClass(name, resolve);
            Class<?> known = findLoadedClass(name);
            if (known != null) return known;
            try (var stream = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (stream == null) throw new ClassNotFoundException(name);
                byte[] bytes = stream.readAllBytes();
                Class<?> result = defineClass(name, bytes, 0, bytes.length);
                if (resolve) resolveClass(result);
                return result;
            } catch (IOException failure) {
                throw new ClassNotFoundException(name, failure);
            }
        }

        @Override public URL getResource(String name) {
            if (name.equals(TARGET.replace('.', '/') + ".class") && !appmek) return null;
            if (name.equals(MEKANISM) && !mekanism) return null;
            return super.getResource(name);
        }
    }
}
