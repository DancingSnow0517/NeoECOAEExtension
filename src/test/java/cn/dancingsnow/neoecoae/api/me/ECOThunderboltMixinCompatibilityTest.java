package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class ECOThunderboltMixinCompatibilityTest {
    @Test
    void legacyProviderFilterHasExactlyOneInjectionTarget() throws IOException {
        var cpu = readCpu();
        var targets = Set.of("executeCrafting", "collectAvailableProviders");
        long matches = cpu.methods.stream()
            .filter(method -> targets.contains(method.name))
            .flatMap(method -> java.util.Arrays.stream(method.instructions.toArray()))
            .filter(instruction -> instruction instanceof MethodInsnNode call
                && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && call.owner.equals("appeng/me/service/CraftingService")
                && call.name.equals("getProviders")
                && call.desc.equals("(Lappeng/api/crafting/IPatternDetails;)Ljava/lang/Iterable;"))
            .count();
        // Thunderbolt 1.0.6 specifies require=1 and allow=1 on these two CPU methods.
        assertEquals(1L, matches);
    }

    @Test
    void legacyBatchWrapperStillTargetsTheCpuExecuteCall() throws IOException {
        var cpu = readCpu();
        long matches = cpu.methods.stream()
            .filter(method -> method.name.equals("tickCraftingLogic"))
            .flatMap(method -> java.util.Arrays.stream(method.instructions.toArray()))
            .filter(instruction -> instruction instanceof MethodInsnNode call
                && call.owner.equals(cpu.name)
                && call.name.equals("executeCrafting")
                && call.desc.equals("(ILappeng/me/service/CraftingService;"
                    + "Lappeng/api/networking/energy/IEnergyService;Lnet/minecraft/world/level/Level;)I"))
            .count();
        assertEquals(1L, matches);
    }

    private static ClassNode readCpu() throws IOException {
        try (var input = ECOThunderboltMixinCompatibilityTest.class.getResourceAsStream("ECOCraftingCPULogic.class")) {
            assertNotNull(input);
            var node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
}
