package cn.dancingsnow.neoecoae.compat.ae2lt;

import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationThreadingCoreBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOAe2LtLegacyCpuCompatibilityTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void releasedCpuMixinUsesAValidAccessorContract() throws Exception {
        var accessor = new ClassNode();
        try (var stream = getClass().getResourceAsStream(
                "/com/moakiee/ae2lt/mixin/thunderbolt/accessor/ECOCraftingCpuAccessor.class")) {
            if (stream == null) {
                // Newer AE2LT releases replaced the invoker with optional reflection in the logic mixin.
                try (var config = getClass().getResourceAsStream("/ae2lt.mixins.json")) {
                    assertNotNull(config, "AE2 Lightning Tech mixin configuration is missing");
                    var mixins = JsonParser.parseReader(new InputStreamReader(config, StandardCharsets.UTF_8))
                            .getAsJsonObject().getAsJsonArray("mixins");
                    var registered = new java.util.ArrayList<String>();
                    mixins.forEach(mixin -> registered.add(mixin.getAsString()));
                    assertFalse(registered.contains("thunderbolt.accessor.ECOCraftingCpuAccessor"),
                            "AE2LT must not register the removed legacy accessor");
                    assertTrue(registered.contains("thunderbolt.ECOCraftingCpuLogicMixin"),
                            "AE2LT must retain its optional ECO logic integration");
                }
                try (var logic = getClass().getResourceAsStream(
                        "/com/moakiee/ae2lt/mixin/thunderbolt/ECOCraftingCpuLogicMixin.class")) {
                    assertNotNull(logic, "Registered ECO logic mixin is missing");
                }
                return;
            }
            new ClassReader(stream).accept(accessor, ClassReader.SKIP_CODE);
        }
        var mixin = accessor.invisibleAnnotations.stream()
                .filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))
                .findFirst().orElseThrow();
        int targets = mixin.values.indexOf("targets");
        assertTrue(targets >= 0);
        assertEquals(java.util.List.of(ECOCraftingCPU.class.getName()), mixin.values.get(targets + 1));

        var invoker = accessor.methods.stream().filter(method -> method.name.equals("ae2lt$markDirty"))
                .findFirst().orElseThrow();
        var annotation = invoker.visibleAnnotations.stream()
                .filter(value -> value.desc.equals("Lorg/spongepowered/asm/mixin/gen/Invoker;"))
                .findFirst().orElseThrow();
        int value = annotation.values.indexOf("value");
        assertTrue(value >= 0);
        // Mixin searches the target class itself, not its subclasses.
        var target = ECOCraftingCPU.class.getDeclaredMethod((String) annotation.values.get(value + 1));
        assertEquals(invoker.desc, Type.getMethodDescriptor(target));
        assertTrue(Modifier.isPublic(target.getModifiers()));
        assertFalse(Modifier.isStatic(target.getModifiers()));
    }

    @Test
    void legacyCallSavesTheCurrentCpuOwner() throws Exception {
        var owner = mock(ECOComputationThreadingCoreBlockEntity.class);
        when(owner.getTier()).thenReturn(mock(IECOTier.class));
        ECOCraftingCPU cpu = new cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU(
                mock(NEComputationCluster.class), null, owner);

        ECOCraftingCPU.class.getDeclaredMethod("markDirty").invoke(cpu);

        verify(owner).saveChanges();
    }

    @Test
    void legacyCallIsSafeForCapacityPlaceholder() {
        ECOCraftingCPU cpu = new cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU(
                mock(NEComputationCluster.class), mock(IECOTier.class));

        assertDoesNotThrow(cpu::markDirty);
    }
}
