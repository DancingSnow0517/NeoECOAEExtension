package cn.dancingsnow.neoecoae.compat.ae2;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Verifies injection targets without initializing Minecraft or loading transformed classes. */
class ECOCraftingMixinContractTest {
    @Test
    void automaticUploadTargetsOnlyTheFinalEncodeReturn() throws Exception {
        var menu = read("appeng/menu/me/items/PatternEncodingTermMenu");
        var encode = menu.methods.stream()
                .filter(method -> method.name.equals("encode"))
                .findFirst()
                .orElseThrow();
        int returns = 0;
        int outputWrites = 0;
        for (var instruction : encode.instructions) {
            if (instruction instanceof org.objectweb.asm.tree.FieldInsnNode field
                    && field.name.equals("encodedPatternSlot")) outputWrites++;
            if (instruction.getOpcode() == org.objectweb.asm.Opcodes.RETURN) {
                if (returns == 3) assertEquals(2, outputWrites, "Final return follows the encoded output write");
                returns++;
            }
        }
        assertEquals(4, returns, "Update the automatic-upload injection when AE2 encode control flow changes");
        var mixin = read("cn/dancingsnow/neoecoae/mixins/PatternEncodingTermMenuMixin");
        var handler = mixin.methods.stream()
                .filter(method -> method.name.equals("neoecoae$uploadAfterEncode"))
                .findFirst()
                .orElseThrow();
        var inject = handler.visibleAnnotations.stream()
                .filter(annotation -> annotation.desc.endsWith("/Inject;"))
                .findFirst()
                .orElseThrow();
        var ats = (List<?>) inject.values.get(inject.values.indexOf("at") + 1);
        var at = (org.objectweb.asm.tree.AnnotationNode) ats.get(0);
        assertEquals("RETURN", at.values.get(at.values.indexOf("value") + 1));
        assertEquals(3, at.values.get(at.values.indexOf("ordinal") + 1));
    }

    @Test
    void cpuMergesWrapTheCompletedForeignMethodRatherThanCancelItsReturnHandlers() throws Exception {
        for (String[] entry : List.of(
                new String[] {"CraftingServiceCpuListMixin", "neoecoae$getCpus", "getCpus"},
                new String[] {"CraftingServiceMixin", "neoecoae$insertIntoCpus", "insertIntoCpus"},
                new String[] {"CraftingServiceMixin", "neoecoae$getRequestedAmount", "getRequestedAmount"})) {
            var mixin = read("cn/dancingsnow/neoecoae/mixins/" + entry[0]);
            var handler = mixin.methods.stream()
                    .filter(method -> method.name.equals(entry[1]))
                    .findFirst()
                    .orElseThrow();
            assertTrue(handler.visibleAnnotations.stream()
                    .anyMatch(annotation ->
                            annotation.desc.equals("Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;")));
            int originalCalls = 0;
            for (var instruction : handler.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    assertFalse(
                            call.owner.equals("org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable"));
                    if (call.owner.equals("com/llamalad7/mixinextras/injector/wrapoperation/Operation")
                            && call.name.equals("call")) originalCalls++;
                }
            }
            assertEquals(1, originalCalls, "Foreign CPU handlers must run exactly once");
            assertTrue(read("appeng/me/service/CraftingService").methods.stream()
                    .anyMatch(method -> method.name.equals(entry[2])));
        }
    }

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
        assertTrue(mixin.methods.stream()
                .anyMatch(method -> method.name.equals("neoecoae$autoSubmitAfterCompatibilityCpus")));
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
        assertTrue(mixin.methods.stream()
                .anyMatch(method -> method.name.equals("neoecoae$tickBeforeCompatibilityThrottle")
                        && method.visibleAnnotations.stream()
                                .anyMatch(value -> value.desc.equals(
                                        "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;"))));
    }

    @Test
    void submissionMetadataBindingDoesNotCompeteForCraftConfirmCallSite() throws Exception {
        var menuMixin = read("cn/dancingsnow/neoecoae/mixins/CraftConfirmMenuMixin");
        assertTrue(menuMixin.methods.stream()
                .flatMap(method -> method.visibleAnnotations == null
                        ? java.util.stream.Stream.empty()
                        : method.visibleAnnotations.stream())
                .noneMatch(annotation ->
                        annotation.desc.equals("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;")));

        var serviceMixin = read("cn/dancingsnow/neoecoae/mixins/CraftingServiceMixin");
        var submitHandler = serviceMixin.methods.stream()
                .filter(method -> method.name.equals("neoecoae$handleSubmitJob"))
                .findFirst()
                .orElseThrow();
        boolean bindsAlias = false;
        for (var instruction : submitHandler.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals("cn/dancingsnow/neoecoae/api/me/ECOPlanningResultRegistry")
                    && call.name.equals("withSubmissionAlias")) {
                bindsAlias = true;
            }
        }
        assertTrue(bindsAlias);
    }

    @Test
    void missingCraftingIsPreparedAtTheMenuEntryPoint() throws Exception {
        var menuMixin = read("cn/dancingsnow/neoecoae/mixins/CraftConfirmMenuMixin");
        var handler = menuMixin.methods.stream()
                .filter(method -> method.name.equals("neoecoae$prepareMissingCraft"))
                .findFirst()
                .orElseThrow();
        assertTrue(handler.visibleAnnotations.stream()
                .anyMatch(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")));
        boolean wrapsMissingPlan = false;
        for (var instruction : handler.instructions) {
            if (instruction instanceof TypeInsnNode type
                    && type.getOpcode() == org.objectweb.asm.Opcodes.NEW
                    && type.desc.equals("cn/dancingsnow/neoecoae/api/me/ECOMissingCraftingPlan")) {
                wrapsMissingPlan = true;
            }
        }
        assertTrue(wrapsMissingPlan);
    }

    @Test
    void automaticEcoFallbackWrapsTheCompletedSubmissionPath() throws Exception {
        var mixin = read("cn/dancingsnow/neoecoae/mixins/CraftingServiceMixin");
        var handler = mixin.methods.stream()
                .filter(method -> method.name.equals("neoecoae$submitJobFallback"))
                .findFirst()
                .orElseThrow();
        assertTrue(handler.visibleAnnotations.stream()
                .anyMatch(annotation ->
                        annotation.desc.equals("Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;")));

        int originalCalls = 0;
        for (var instruction : handler.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals("com/llamalad7/mixinextras/injector/wrapoperation/Operation")
                    && call.name.equals("call")) {
                originalCalls++;
            }
        }
        assertEquals(1, originalCalls, "The compatibility/native submission path must run exactly once");
    }

    private ClassNode read(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/" + name + ".class")) {
            assertNotNull(stream);
            var node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }
}
