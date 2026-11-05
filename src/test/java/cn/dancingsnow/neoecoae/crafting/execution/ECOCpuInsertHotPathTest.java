package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.mixins.ae2.crafting.CraftingServiceMixin;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOCpuInsertHotPathTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void networkWithoutEcoClustersDoesNotCreateAnEmptyIterator() throws Exception {
        var clusters = spy(new HashSet<NEComputationCluster>());
        assertEquals(0, route(clusters, mock(AEKey.class), 10, 0, Actionable.MODULATE));
        verify(clusters, never()).iterator();
    }

    @Test void vanillaAlreadyAcceptedEverythingDoesNotVisitEcoCpus() throws Exception {
        var cluster = mock(NEComputationCluster.class);
        assertEquals(10, route(Set.of(cluster), mock(AEKey.class), 10, 10, Actionable.MODULATE));
        verifyNoInteractions(cluster);
    }

    @Test void onlyTheRemainderIsOfferedAndTraversalStopsWhenFullyAccepted() throws Exception {
        AEKey key = mock(AEKey.class);
        for (Actionable mode : Actionable.values()) {
            var first = cpu(key, 6, 3, mode);
            var second = cpu(key, 3, 3, mode);
            var unused = mock(ECOCraftingCPU.class);
            assertEquals(10, route(Set.of(cluster(first, second, unused)), key, 10, 4, mode));
            verify(first.getLogic()).insert(key, 6, mode);
            verify(second.getLogic()).insert(key, 3, mode);
            verifyNoInteractions(unused);
        }
    }

    @Test void partialAcceptanceLeavesTheRemainderForNetworkStorage() throws Exception {
        AEKey key = mock(AEKey.class);
        var first = cpu(key, 8, 3, Actionable.MODULATE);
        var next = cpu(key, 5, 0, Actionable.MODULATE);
        assertEquals(5, route(new LinkedHashSet<>(List.of(cluster(first), cluster(next))),
            key, 10, 2, Actionable.MODULATE));
        verify(next.getLogic()).insert(key, 5, Actionable.MODULATE);
    }

    @Test void actualAe2FinalLocalLoadRemainsBeforeReturnAndOtherModsLocalCapture() throws Exception {
        var node = new ClassNode();
        try (var stream = CraftingService.class.getResourceAsStream("/appeng/me/service/CraftingService.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(node, 0);
        }
        var method = node.methods.stream().filter(value -> value.name.equals("insertIntoCpus")).findFirst().orElseThrow();
        int inserted = method.localVariables.stream().filter(value -> value.name.equals("inserted"))
                .findFirst().orElseThrow().index;
        var loads = java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false)
                .filter(value -> value instanceof VarInsnNode variable && variable.getOpcode() == Opcodes.LLOAD
                        && variable.var == inserted).toList();
        assertEquals(3, loads.size(), "review the primitive local hook if AE2 changes its loop");
        var next = loads.get(2).getNext();
        while (next != null && next.getOpcode() < 0) next = next.getNext();
        assertNotNull(next);
        assertEquals(Opcodes.LRETURN, next.getOpcode());
    }

    private static long route(Set<NEComputationCluster> clusters, AEKey key, long amount, long already,
            Actionable mode) throws Exception {
        var mixin = mock(CraftingServiceMixin.class, CALLS_REAL_METHODS);
        var field = CraftingServiceMixin.class.getDeclaredField("neoecoae$computationClusters");
        field.setAccessible(true);
        field.set(mixin, clusters);
        var hook = CraftingServiceMixin.class.getDeclaredMethod("neoecoae$earlyInsertIntoCpus", long.class, AEKey.class,
            long.class, Actionable.class);
        hook.setAccessible(true);
        return (long) hook.invoke(mixin, already, key, amount, mode);
    }

    private static NEComputationCluster cluster(ECOCraftingCPU... cpus) {
        var cluster = mock(NEComputationCluster.class);
        when(cluster.getActiveCPUs()).thenReturn(List.of(cpus));
        return cluster;
    }

    private static ECOCraftingCPU cpu(AEKey key, long offered, long accepted, Actionable mode) {
        var cpu = mock(ECOCraftingCPU.class);
        var logic = mock(ECOCraftingCPULogic.class);
        when(cpu.getLogic()).thenReturn(logic);
        when(logic.insert(key, offered, mode)).thenReturn(accepted);
        return cpu;
    }
}
