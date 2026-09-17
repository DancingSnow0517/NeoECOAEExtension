package cn.dancingsnow.neoecoae.compat.ae2;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

class MEStorageScreenMixinCompatibilityTest {
    private static final String SLOT_DESCRIPTOR =
            "(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;)V";

    @Test
    void exactRenderingTargetsTheAe2SlotOverrideInDevelopmentAndProduction() throws Exception {
        var target = readClass("appeng/client/gui/me/common/MEStorageScreen");
        var mixin = readClass("cn/dancingsnow/neoecoae/mixins/client/MEStorageScreenMixin");
        var handler = mixin.methods.stream()
                .filter(method -> method.name.equals("neoecoae$renderExact"))
                .findFirst()
                .orElseThrow();
        var inject = handler.visibleAnnotations.stream()
                .filter(annotation ->
                        annotation.desc.equals("Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;"))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        var selectors = (List<String>) value(inject, "method");

        // Read AE2 bytecode without loading Minecraft's client classes in the test JVM.
        assertEquals(
                1,
                target.methods.stream()
                        .filter(method -> selectors.contains(method.name + method.desc))
                        .count());
        assertTrue(selectors.contains("renderSlot" + SLOT_DESCRIPTOR));
        // Forge 1.20.1's SRG name for AbstractContainerScreen.renderSlot.
        assertTrue(selectors.contains("m_280092_" + SLOT_DESCRIPTOR));
        assertEquals(false, value(inject, "remap"));
        assertEquals(1, value(inject, "require"));
        assertEquals(1, value(inject, "allow"));
        assertInvocation(target, "renderSlot", "appeng/api/stacks/AEKey", "formatAmount");
        assertInvocation(
                target, "renderGridInventoryEntryTooltip", "appeng/core/localization/Tooltips", "getAmountTooltip");
    }

    private static void assertInvocation(ClassNode target, String methodName, String owner, String name) {
        var method = target.methods.stream()
                .filter(candidate -> candidate.name.equals(methodName))
                .findFirst()
                .orElseThrow();
        long count = java.util.Arrays.stream(method.instructions.toArray())
                .filter(instruction -> instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                        && call.owner.equals(owner)
                        && call.name.equals(name))
                .count();
        assertEquals(1, count, methodName + " injection point");
    }

    private static Object value(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        }
        fail("Missing annotation property: " + key);
        return null;
    }

    private static ClassNode readClass(String name) throws Exception {
        try (var input = MEStorageScreenMixinCompatibilityTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, name);
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }
}
