package cn.dancingsnow.neoecoae.compat.thunderbolt;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URL;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;

class ECOThunderboltMixinSelectionTest {
    private static final String PACKAGE = "cn.dancingsnow.neoecoae.mixins.compat.thunderbolt.";
    private static final String PLUGIN = PACKAGE + "ECOThunderboltMixinPlugin";

    @Test void oldApiIsExplicitlyUnsupported() throws Exception { check(false, true, false, false); }
    @Test void modernApiOnlySelectsModernProvider() throws Exception { check(true, false, true, false); }
    @Test void absentModSelectsNeither() throws Exception { check(false, false, false, false); }
    @Test void modernApiTakesPrecedenceOverLegacyResources() throws Exception { check(true, true, true, false); }

    private void check(boolean modern, boolean legacy, boolean expectModern, boolean expectLegacy) throws Exception {
        ClassLoader parent = getClass().getClassLoader();
        var loader = new ClassLoader(parent) {
            @Override public URL getResource(String name) {
                if (name.startsWith("com/moakiee/thunderbolt/")) {
                    boolean exists = name.contains("/api/crafting/batch/") ? modern : legacy;
                    return exists ? parent.getResource(PLUGIN.replace('.', '/') + ".class") : null;
                }
                return super.getResource(name);
            }
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(PLUGIN)) return super.loadClass(name, resolve);
                Class<?> result = findLoadedClass(name);
                if (result == null) {
                    try (var input = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                        byte[] bytes = input.readAllBytes();
                        result = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException failure) {
                        throw new ClassNotFoundException(name, failure);
                    }
                }
                if (resolve) resolveClass(result);
                return result;
            }
        };
        var plugin = (IMixinConfigPlugin) loader.loadClass(PLUGIN).getConstructor().newInstance();
        assertEquals(expectModern, plugin.shouldApplyMixin("unused", PACKAGE + "ECOThunderboltProviderMixin"));
        assertEquals(expectLegacy, plugin.shouldApplyMixin("unused", PACKAGE + "ECOLegacyThunderboltBridgeMixin"));
    }
}
