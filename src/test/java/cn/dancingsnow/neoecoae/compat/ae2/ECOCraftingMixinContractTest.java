package cn.dancingsnow.neoecoae.compat.ae2;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/** Verifies injection targets without initializing Minecraft or loading transformed classes. */
class ECOCraftingMixinContractTest {
    @Test
    void craftingServiceInjectionTargetsExistInTheActualAe2Dependency() throws Exception {
        var target = read("appeng/me/service/CraftingService");
        var mixin = read("cn/dancingsnow/neoecoae/mixins/CraftingServiceMixin");
        var names = new HashSet<String>();
        target.methods.forEach(method -> names.add(method.name));
        for (var method : mixin.methods) {
            if (method.visibleAnnotations == null) continue;
            for (var annotation : method.visibleAnnotations) {
                if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) continue;
                for (int i = 0; i < annotation.values.size(); i += 2) {
                    if (!annotation.values.get(i).equals("method")) continue;
                    for (var selector : (List<?>) annotation.values.get(i + 1)) {
                        String name = selector.toString().split("\\(", 2)[0];
                        assertTrue(names.contains(name), "Missing AE2 injection target: " + selector);
                    }
                }
            }
        }
        assertTrue(mixin.interfaces.contains("cn/dancingsnow/neoecoae/api/me/ECOCraftingOutputRouter"));
        assertTrue(mixin.interfaces.contains("cn/dancingsnow/neoecoae/api/me/ECOCraftingServiceTicker"));
        assertTrue(mixin.methods.stream().anyMatch(method -> method.name.equals("neoecoae$autoSubmitAfterCompatibilityCpus")));
        assertTrue(mixin.methods.stream()
                .anyMatch(method -> method.name.equals("saveNodeData")
                        && method.desc.equals("(Lappeng/api/networking/IGridNode;Lnet/minecraft/nbt/CompoundTag;)V")));
    }

    @Test
    void customCpuTickRunsBeforeCompatibilityThrottle() throws Exception {
        var mixin = read("cn/dancingsnow/neoecoae/mixins/CraftingServiceEarlyTickMixin");
        var annotation = mixin.invisibleAnnotations.stream()
                .filter(value -> value.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))
                .findFirst()
                .orElseThrow();
        int priority = 1000;
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals("priority")) {
                priority = (Integer) annotation.values.get(i + 1);
            }
        }
        assertEquals(1100, priority);
        assertTrue(mixin.methods.stream().anyMatch(method -> method.name.equals("neoecoae$tickBeforeCompatibilityThrottle")
                && method.visibleAnnotations.stream().anyMatch(value ->
                        value.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;"))));
    }

    private ClassNode read(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/" + name + ".class")) {
            assertNotNull(stream);
            var node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_CODE);
            return node;
        }
    }
}
