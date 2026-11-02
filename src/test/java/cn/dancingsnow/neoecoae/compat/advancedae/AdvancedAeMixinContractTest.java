package cn.dancingsnow.neoecoae.compat.advancedae;

import static org.junit.jupiter.api.Assertions.*;

import cn.dancingsnow.neoecoae.crafting.execution.ECOExternalCpuJob;
import cn.dancingsnow.neoecoae.mixins.compat.advancedae.accessor.AdvancedAeCraftingJobAccessor;
import cn.dancingsnow.neoecoae.mixins.compat.advancedae.accessor.AdvancedAeTaskProgressAccessor;
import cn.dancingsnow.neoecoae.mixins.compat.advancedae.crafting.AdvancedAeCraftingCpuLogicMixin;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.gen.Accessor;

class AdvancedAeMixinContractTest {
    @Test void cpuHandlerDoesNotReferenceConcreteJobMixins() throws Exception {
        var forbidden = Set.of(Type.getInternalName(AdvancedAeCraftingJobAccessor.class),
                Type.getInternalName(AdvancedAeTaskProgressAccessor.class));
        var node = new ClassNode();
        try (var stream = AdvancedAeCraftingCpuLogicMixin.class.getResourceAsStream(
                "AdvancedAeCraftingCpuLogicMixin.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(node, 0);
        }
        for (var method : node.methods) {
            for (var name : forbidden) {
                assertFalse(method.desc.contains(name), method.name + " descriptor");
                if (method.localVariables != null) {
                    for (var local : method.localVariables) {
                        assertFalse(local.desc.contains(name), method.name + " local " + local.name);
                    }
                }
                for (var instruction : method.instructions) {
                    if (instruction instanceof TypeInsnNode type) assertNotEquals(name, type.desc);
                    if (instruction instanceof MethodInsnNode call) assertNotEquals(name, call.owner);
                }
            }
        }
    }

    @Test void bridgesAreMergedClassesAndMatchActualAdvancedAeFields() throws Exception {
        assertFalse(AdvancedAeCraftingJobAccessor.class.isInterface());
        assertFalse(AdvancedAeTaskProgressAccessor.class.isInterface());
        assertTrue(ECOExternalCpuJob.class.isAssignableFrom(AdvancedAeCraftingJobAccessor.class));
        assertTrue(ECOExternalCpuJob.Task.class.isAssignableFrom(AdvancedAeTaskProgressAccessor.class));
        for (var bridge : Set.of(AdvancedAeCraftingJobAccessor.class, AdvancedAeTaskProgressAccessor.class)) {
            var target = Class.forName(bridge == AdvancedAeCraftingJobAccessor.class
                    ? "net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob"
                    : "net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob$TaskProgress");
            for (var method : bridge.getDeclaredMethods()) {
                var accessor = method.getAnnotation(Accessor.class);
                if (accessor == null) continue;
                var field = target.getDeclaredField(accessor.value());
                assertEquals(field.getType(), method.getParameterCount() == 0
                        ? method.getReturnType() : method.getParameterTypes()[0]);
            }
        }
    }
}
