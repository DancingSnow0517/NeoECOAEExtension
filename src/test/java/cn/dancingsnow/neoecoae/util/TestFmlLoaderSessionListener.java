package cn.dancingsnow.neoecoae.util;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 26.1 port: FML 11 resolves {@code FMLEnvironment}, {@code LoadingModList.get()} etc. through
 * {@code FMLLoader.getCurrent()}, and {@code SharedConstants}' static initializer already calls it. These
 * isolated-JVM tests never start FML, so a stub loader (client dist, development environment, no mods) is
 * installed once per JUnit launcher session, before any test class is initialized.
 */
public final class TestFmlLoaderSessionListener implements LauncherSessionListener {
    @Override
    public void launcherSessionOpened(LauncherSession session) {
        install();
        bootstrapGame();
    }

    /**
     * 26.1: {@code Bootstrap.bootStrap()} no longer binds the default data components of items/fluids; the game
     * does that when it loads registries ({@code DATA_COMPONENT_INITIALIZERS}). Do it once here against the
     * vanilla built-in + bootstrap (worldgen/enchantment/...) registries, so ItemStacks/FluidStacks work.
     */
    static synchronized void bootstrapGame() {
        try {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            HolderLookup.Provider vanilla = VanillaRegistries.createLookup();
            RegistryAccess.Frozen builtIn = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            Map<ResourceKey<? extends Registry<?>>, HolderLookup.RegistryLookup<?>> lookups = new LinkedHashMap<>();
            builtIn.listRegistryKeys().forEach(key -> lookups.put(key, builtIn.lookupOrThrow(key)));
            vanilla.listRegistryKeys().forEach(key -> lookups.putIfAbsent(key, vanilla.lookupOrThrow(key)));
            HolderLookup.Provider context = HolderLookup.Provider.create(lookups.values().stream());
            // The IDE-only component validation rejects the placeholder HolderSets of the (unloaded) tags.
            boolean ide = SharedConstants.IS_RUNNING_IN_IDE;
            SharedConstants.IS_RUNNING_IN_IDE = false;
            try {
                BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(context)
                    .forEach(DataComponentInitializers.PendingComponents::apply);
            } finally {
                SharedConstants.IS_RUNNING_IN_IDE = ide;
            }
            // Initialize AE2's built-in key types once, outside any test's mocks: their static initializers
            // otherwise run inside whichever test touches them first and, if that test has static mocks or an
            // unfinished stubbing open, poison the classes for the rest of the JVM.
            appeng.api.stacks.AEKeyType.items();
            appeng.api.stacks.AEKeyType.fluids();
        } catch (Throwable t) {
            // Keep the session alive; tests needing the game will report their own failure.
            System.err.println("[TestFmlLoaderSessionListener] game bootstrap failed: " + t);
            t.printStackTrace();
            try (var out = new java.io.PrintStream(java.nio.file.Files.newOutputStream(
                    Path.of("build", "test-bootstrap-failure.txt")))) {
                t.printStackTrace(out);
            } catch (java.io.IOException ignored) {
            }
        }
    }

    /** Replaces the stub loader/ModList after {@code Mockito.framework().clearInlineMocks()} disabled them. */
    static synchronized void reinstall() {
        try {
            Field field = FMLLoader.class.getDeclaredField("current");
            field.setAccessible(true);
            ((AtomicReference<?>) field.get(null)).set(null);
            Field instance = ModList.class.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            instance.set(null, null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        install();
    }

    @SuppressWarnings("unchecked")
    static synchronized void install() {
        try {
            Field field = FMLLoader.class.getDeclaredField("current");
            field.setAccessible(true);
            var current = (AtomicReference<FMLLoader>) field.get(null);
            if (current.get() != null) {
                return;
            }
            FMLLoader loader = mock(FMLLoader.class);
            LoadingModList mods = mock(LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            when(loader.getDist()).thenReturn(Dist.CLIENT);
            when(loader.isProduction()).thenReturn(false);
            when(loader.getLoadingModList()).thenReturn(mods);
            when(loader.getGameDir()).thenReturn(Path.of("build", "test-game-dir").toAbsolutePath());
            current.set(loader);
            // An empty ModList ("no other mods loaded"); tests that need specific mods still mockStatic(ModList).
            // LDLib2 26.1 static initializers (e.g. IGuiTexture's codec registry) scan ModList.get() eagerly.
            Field instance = ModList.class.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            if (instance.get(null) == null) {
                instance.set(null, mock(ModList.class));
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot install the test FML loader", e);
        }
    }
}
