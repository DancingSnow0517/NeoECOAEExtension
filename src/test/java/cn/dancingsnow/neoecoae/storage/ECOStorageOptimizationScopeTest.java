package cn.dancingsnow.neoecoae.storage;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import static org.junit.jupiter.api.Assertions.*;

/** Terminal/watcher sharing is global by design; FE factories and CPU return budgets remain ECO-owned. */
class ECOStorageOptimizationScopeTest {
    @Test void internalLayoutBridgesRequireTheCheckedAe2Version() {
        var plugin = new cn.dancingsnow.neoecoae.mixins.ae2.storage.StoragePerformanceMixinPlugin();
        try (var loading = org.mockito.Mockito.mockStatic(net.neoforged.fml.loading.LoadingModList.class)) {
            var mods = org.mockito.Mockito.mock(net.neoforged.fml.loading.LoadingModList.class);
            var ae2 = org.mockito.Mockito.mock(net.neoforged.fml.loading.moddiscovery.ModInfo.class);
            loading.when(net.neoforged.fml.loading.LoadingModList::get).thenReturn(mods);
            org.mockito.Mockito.when(mods.getMods()).thenReturn(List.of(ae2));
            org.mockito.Mockito.when(ae2.getModId()).thenReturn("ae2");
            org.mockito.Mockito.when(ae2.getVersion()).thenReturn(new org.apache.maven.artifact.versioning.DefaultArtifactVersion("26.1.13-beta"));
            assertTrue(plugin.shouldApplyMixin("appeng.api.stacks.KeyCounter", "example.KeyCounterSnapshotMixin"));
            org.mockito.Mockito.when(ae2.getVersion()).thenReturn(new org.apache.maven.artifact.versioning.DefaultArtifactVersion("26.1.14-beta"));
            assertFalse(plugin.shouldApplyMixin("appeng.api.stacks.KeyCounter", "example.KeyCounterSnapshotMixin"));
            assertTrue(plugin.shouldApplyMixin("appeng.me.storage.NetworkStorage", "example.NetworkStorageMixin"));
        }
    }

    private static final Set<String> SHARED_TARGETS = Set.of(
        "appeng.api.stacks.KeyCounter", "appeng.me.service.StorageService",
        "appeng.parts.automation.StorageLevelEmitterPart", "appeng.parts.automation.StorageLevelEmitterPart$1",
        "appeng.me.storage.NetworkStorage", "appeng.menu.me.common.MEStorageMenu");

    @Test void sharedTerminalAndWatcherOptimizationsDoNotRestoreTheGlobalFluxFactory() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        Set<String> shared = new HashSet<>();
        for (String resource : List.of("neoecoae.mixins.json", "neoecoae.key_reuse.mixins.json",
                "neoecoae.terminal_big_amount.mixins.json")) {
            try (var stream = loader.getResourceAsStream(resource)) {
                assertNotNull(stream, resource);
                var config = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                String prefix = config.get("package").getAsString();
                for (var entry : config.getAsJsonArray("mixins")) {
                    String name = prefix + "." + entry.getAsString();
                    try (var input = loader.getResourceAsStream(name.replace('.', '/') + ".class")) {
                        assertNotNull(input, name);
                        var node = new ClassNode();
                        new ClassReader(input).accept(node, ClassReader.SKIP_CODE);
                        if (name.endsWith(".StorageServiceMixin")) {
                            assertTrue(node.fields.stream().noneMatch(field -> field.desc.endsWith("/StorageReturnBudget;")),
                                    "the common AE2 service must not own the ECO CPU return budget");
                        }
                        if (node.invisibleAnnotations == null) continue;
                        for (var annotation : node.invisibleAnnotations) {
                            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) continue;
                            for (int i = 0; i < annotation.values.size(); i += 2) {
                                String attribute = (String) annotation.values.get(i);
                                if (!attribute.equals("value") && !attribute.equals("targets")) continue;
                                for (Object value : (List<?>) annotation.values.get(i + 1)) {
                                    String target = value instanceof Type type ? type.getClassName() : value.toString();
                                    assertNotEquals("com.glodblock.github.appflux.common.me.key.FluxKey", target,
                                            "Applied Flux's global factory must retain its native behavior");
                                    if (SHARED_TARGETS.contains(target)) shared.add(target);
                                }
                            }
                        }
                    }
                }
            }
        }
        assertEquals(SHARED_TARGETS, shared, "all terminal and watcher optimizations must stay registered");
    }
}
