package cn.dancingsnow.neoecoae.compat.gregtech;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import cn.dancingsnow.neoecoae.mixins.compat.gregtech.GTMEPatternBufferMixin;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Static bytecode checks against supplied real releases, without loading a GT runtime into the unit-test JVM. */
class ECOGregTechNativeJarContractTest {
    private static final String GT_BUFFER =
            "com/gregtechceu/gtceu/integration/ae2/machine/MEPatternBufferPartMachine";
    private static final String GTL_BUFFER =
            "org/gtlcore/gtlcore/common/machine/multiblock/part/ae/MEPatternBufferPartMachineBase";
    private static final String GTL_MOLECULAR_ASSEMBLER =
            "org/gtlcore/gtlcore/common/machine/multiblock/part/ae/MEMolecularAssemblerIOPartMachine";
    private static final String INPUTS = "[Lappeng/api/stacks/KeyCounter;";
    private static final String PATTERN = "Lappeng/api/crafting/IPatternDetails;";

    @Test void nativeGtPhysicalInputInvocationMatchesTheOptionalMixinExactly() throws Exception {
        try (var jar = release("gtProcessingGtmJar")) {
            var target = read(jar, GT_BUFFER);
            var push = method(target, "pushPattern", "(" + PATTERN + INPUTS + ")Z");
            var calls = calls(push).stream().filter(call -> call.owner.equals(GT_BUFFER + "$InternalSlot")
                    && call.name.equals("pushPattern")).toList();
            assertFalse(calls.isEmpty(), "Real GT must retain its native physical-input boundary");
            for (var call : calls) assertEquals("(" + PATTERN + INPUTS + ")V", call.desc);

            var mixin = new ClassNode();
            try (var stream = GTMEPatternBufferMixin.class.getResourceAsStream("GTMEPatternBufferMixin.class")) {
                assertNotNull(stream);
                new ClassReader(stream).accept(mixin, 0);
            }
            assertTrue(mixin.interfaces.contains(Type.getInternalName(ECOGregTechProcessingDispatch.BatchInputProvider.class)));
            var handler = mixin.methods.stream().filter(candidate -> candidate.name.equals("neoecoae$pushCompleteBatch"))
                    .findFirst().orElseThrow();
            var annotation = annotations(handler).stream().filter(candidate -> candidate.desc.endsWith("/WrapOperation;"))
                    .findFirst().orElseThrow();
            var points = (List<?>) value(annotation, "at");
            assertEquals(1, points.size());
            var at = (AnnotationNode) points.get(0);
            assertEquals("L" + calls.get(0).owner + ";pushPattern" + calls.get(0).desc, value(at, "target"));
            assertTrue(calls(method(read(jar, GT_BUFFER + "$InternalSlot"), "pushPattern",
                    "(" + PATTERN + INPUTS + ")V")).stream()
                    .anyMatch(call -> call.name.equals("pushInputsToExternalInventory")));
        }
    }

    @Test void nativeGtlBufferAcceptsCounterTotalsAndExposesTheExpectedCapacityInterface() throws Exception {
        try (var jar = release("gtProcessingGtlJar")) {
            var buffer = read(jar, GTL_BUFFER);
            assertTrue(buffer.interfaces.contains("appeng/api/networking/crafting/ICraftingProvider"));
            var admission = method(buffer, "pushPattern", "(" + PATTERN + INPUTS + ")Z");
            assertTrue(calls(admission).stream().anyMatch(call -> call.owner.equals(GTL_BUFFER + "$InternalSlot")
                    && call.name.equals("pushPattern") && call.desc.equals("(" + INPUTS + ")V")));
            var slot = method(read(jar, GTL_BUFFER + "$InternalSlot"), "pushPattern", "(" + INPUTS + ")V");
            assertTrue(calls(slot).stream().anyMatch(call -> call.name.equals("pushInputsToMEPatternBufferInventory")));
            method(read(jar, "org/gtlcore/gtlcore/integration/ae2/crafting/IPatternProviderAutoExpand"),
                    "gtlcore$getMaxPatternOperations", "(" + PATTERN + "J)J");
        }
    }

    @Test void nativeGtlMolecularAssemblerCountsAggregateInputsUsingItsOriginalProcessingPattern() throws Exception {
        try (var jar = release("gtProcessingGtlJar")) {
            var assembler = read(jar, GTL_MOLECULAR_ASSEMBLER);
            var admission = method(assembler, "pushPattern", "(" + PATTERN + INPUTS + ")Z");
            assertTrue(calls(admission).stream().anyMatch(call -> call.name.equals("containsKey")),
                "The native provider validates its registered pattern before accepting a batch");
            assertTrue(calls(admission).stream().anyMatch(call -> call.name.equals("getLongValue")),
                "The native provider must consume long input totals rather than one-copy amounts");
            assertTrue(calls(admission).stream().anyMatch(call -> call.name.equals("addTo")),
                "Accepted crafts are added to the counted output queue");
            var capacity = method(assembler, "gtlcore$graphCapacity", "(" + PATTERN + "Ljava/util/Map;J)J");
            assertTrue(calls(capacity).stream().anyMatch(call -> call.name.equals("gtlcore$canProduceGraphPattern")),
                "Capacity preflight must enforce native pattern and tool requirements");
        }
    }

    @Test void realGtReleaseExposesTheReadOnlyMachineAndControllerProgressContracts() throws Exception {
        try (var jar = release("gtProcessingGtmJar")) {
            method(read(jar, "com/gregtechceu/gtceu/api/blockentity/MetaMachineBlockEntity"),
                "getMetaMachine", "()Lcom/gregtechceu/gtceu/api/machine/MetaMachine;");
            var machine = read(jar, "com/gregtechceu/gtceu/api/machine/MetaMachine");
            method(machine, "getLevel", "()Lnet/minecraft/world/level/Level;");
            method(machine, "getPos", "()Lnet/minecraft/core/BlockPos;");
            var part = read(jar, "com/gregtechceu/gtceu/api/machine/multiblock/part/MultiblockPartMachine");
            assertTrue(part.fields.stream().anyMatch(field -> field.name.equals("controllerPositions")
                && field.desc.equals("Ljava/util/Set;")));
            var recipe = read(jar, "com/gregtechceu/gtceu/api/recipe/GTRecipe");
            assertTrue(recipe.fields.stream().anyMatch(field -> field.name.equals("outputs") && field.desc.equals("Ljava/util/Map;")));
            var logic = read(jar, "com/gregtechceu/gtceu/api/machine/trait/RecipeLogic");
            method(logic, "getLastRecipe", "()Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;");
            method(logic, "getProgress", "()I");
            method(logic, "getMaxProgress", "()I");
            method(logic, "isWorking", "()Z");
            method(logic, "isWaiting", "()Z");
            method(logic, "isSuspend", "()Z");
        }
    }

    private static JarFile release(String property) throws Exception {
        String path = System.getProperty("neoecoae." + property, "");
        assumeTrue(!path.isBlank(), "Supply -P" + property + " to verify a real optional dependency release");
        return new JarFile(Path.of(path).toFile());
    }

    private static ClassNode read(JarFile jar, String name) throws Exception {
        var entry = jar.getJarEntry(name + ".class");
        assertNotNull(entry, name);
        try (var stream = jar.getInputStream(entry)) {
            var node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    private static MethodNode method(ClassNode node, String name, String descriptor) {
        return node.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
                .findFirst().orElseThrow(() -> new AssertionError(node.name + "." + name + descriptor));
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        var calls = new ArrayList<MethodInsnNode>();
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) calls.add(call);
        return calls;
    }

    private static List<AnnotationNode> annotations(MethodNode method) {
        var annotations = new ArrayList<AnnotationNode>();
        if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
        return annotations;
    }

    private static Object value(AnnotationNode annotation, String name) {
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(name)) return annotation.values.get(i + 1);
        }
        throw new AssertionError("Missing annotation value " + name);
    }
}
