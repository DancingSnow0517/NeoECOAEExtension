package cn.dancingsnow.neoecoae.compat.extendedaeplus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOForcedCraftingPlan;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EAEPForcedCraftingTest {
    private static final String MARKER = "com.extendedae_plus.api.crafting.IForcedCraftingPlan";
    private static final String WRAPPER = "com.extendedae_plus.crafting.ForcedCraftingPlan";

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void absentPlusDoesNotResolveItsClasses() {
        var loader = new ClassLoader(EAEPForcedCrafting.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) {
                throw new AssertionError("Unexpected optional class lookup: " + name);
            }
        };
        assertNull(EAEPForcedCrafting.load(loader, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {MARKER, WRAPPER})
    void missingOptionalProtocolClassIsTreatedAsLegacyPlus(String missingClass) {
        var loader = new ClassLoader(EAEPForcedCrafting.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(missingClass)) throw new ClassNotFoundException(name);
                return super.loadClass(name, resolve);
            }
        };
        assertNull(EAEPForcedCrafting.load(loader, true));
    }

    @Test void installedLegacyPlusKeepsOrdinaryChecksAndForcedFallbackWorkingAfterRepeatedCalls() throws Exception {
        var key = mock(AEKey.class, RETURNS_DEEP_STUBS);
        when(key.getPrimaryKey()).thenReturn(new Object());
        var missing = new KeyCounter(); missing.add(key, 7);
        var emitted = new KeyCounter(); emitted.add(key, 2);
        var ordinary = new CraftingPlan(new GenericStack(key, 1), 64, false, false,
            new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of());
        var simulation = new CraftingPlan(new GenericStack(key, 1), 64, true, false,
            new KeyCounter(), emitted, missing, Map.of());

        // Reload the bridge and its lazy holder with the old Plus class availability. This exercises
        // the public path with ModList reporting Plus installed, including repeated holder access.
        String bridgeName = EAEPForcedCrafting.class.getName();
        URL classes = EAEPForcedCrafting.class.getProtectionDomain().getCodeSource().getLocation();
        try (var loader = new URLClassLoader(new URL[] {classes}, EAEPForcedCrafting.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(MARKER) || name.equals(WRAPPER)) throw new ClassNotFoundException(name);
                if (name.equals(bridgeName) || name.startsWith(bridgeName + "$")) {
                    synchronized (getClassLoadingLock(name)) {
                        var type = findLoadedClass(name);
                        if (type == null) type = findClass(name);
                        if (resolve) resolveClass(type);
                        return type;
                    }
                }
                return super.loadClass(name, resolve);
            }
        }; var mods = mockStatic(ModList.class)) {
            var modList = mock(ModList.class);
            mods.when(ModList::get).thenReturn(modList);
            when(modList.isLoaded("extendedae_plus")).thenReturn(true);
            var bridge = Class.forName(bridgeName, true, loader);
            var isForced = bridge.getMethod("isForced", ICraftingPlan.class);
            var manualMissing = bridge.getMethod("manualMissing", ICraftingPlan.class);
            var force = bridge.getMethod("force", ICraftingPlan.class);

            for (int attempt = 0; attempt < 3; attempt++) {
                assertEquals(false, isForced.invoke(null, ordinary));
                assertTrue(((KeyCounter) manualMissing.invoke(null, ordinary)).isEmpty());
                var forced = assertInstanceOf(ECOForcedCraftingPlan.class, force.invoke(null, simulation));
                assertEquals(true, isForced.invoke(null, forced));
                assertFalse(forced.simulation());
                assertTrue(forced.missingItems().isEmpty());
                assertEquals(9, forced.emittedItems().get(key));
                assertTrue(((KeyCounter) manualMissing.invoke(null, forced)).isEmpty());
            }
            assertFalse(ordinary.simulation());
            assertTrue(simulation.simulation());
            assertEquals(7, simulation.missingItems().get(key));
            assertEquals(2, simulation.emittedItems().get(key));
        }
    }
}
