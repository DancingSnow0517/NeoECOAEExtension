package cn.dancingsnow.neoecoae.compat.processing;

import static cn.dancingsnow.neoecoae.compat.processing.ProcessingTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingStatusReader.Context;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOWorkstationMachineStatus;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixBlockEntity;
import com.glodblock.github.extendedae.common.me.CraftingThread;
import com.glodblock.github.extendedae.common.me.matrix.ClusterAssemblerMatrix;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixCrafter;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AssemblerMatrixProcessingStatusTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }
    private final AssemblerMatrixProcessingStatus reader = new AssemblerMatrixProcessingStatus();
    private final AEKey key = AEItemKey.of(Items.STONE);
    private final Context context = new Context(key, UUID.randomUUID());

    @Test void ordinaryMatrixResolvesPatternCoreToEveryExecutionCore() {
        var provider = machine(TileAssemblerMatrixPattern.class);
        var first = machine(TileAssemblerMatrixCrafter.class);
        var second = machine(TileAssemblerMatrixCrafter.class);
        when(second.getBlockPos()).thenReturn(new BlockPos(1, 0, 0));
        var cluster = mock(ClusterAssemblerMatrix.class);
        when(provider.getCluster()).thenReturn(cluster);
        when(cluster.getBlockEntities()).thenReturn(List.<com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixBase>of(provider, first, second).iterator());
        assertTrue(reader.supports(provider));
        var observation = reader.read(provider, context);
        assertNull(observation.sample());
        assertEquals(2, observation.links().size());
        assertEquals(first.getBlockPos(), observation.links().getFirst().position());
        assertEquals(1, ECOWorkstationMachineStatus.targets(provider).size());
        verify(cluster, never()).pushCraftingJob(any(), any());
    }

    @Test void ordinaryMatrixIgnoresUnrelatedRecipesAndReadsThreadProgress() {
        var crafter = machine(TileAssemblerMatrixCrafter.class);
        var node = mock(IManagedGridNode.class);
        when(crafter.getMainNode()).thenReturn(node);
        when(node.isActive()).thenReturn(true);
        set(crafter, "threads", new CraftingThread[]{thread(key, 20), thread(AEItemKey.of(Items.DIRT), 99), thread(key, 80)});
        var sample = reader.read(crafter, context).sample();
        assertEquals(2, sample.parallel());
        assertEquals(State.RUNNING, sample.state());
        assertEquals(0.5, sample.progressTicks() / (double) sample.durationTicks(), 0.00001);
        when(node.isActive()).thenReturn(false);
        assertEquals(State.PAUSED, reader.read(crafter, context).sample().state());
    }

    @Test void superAndUltimateMatricesReadBatchProgressAtTheCanonicalController() {
        for (Class<? extends SuperAssemblerMatrixBlockEntity> type : List.of(SuperAssemblerMatrixBlockEntity.class, Ultimate.class)) {
            var core = machine(type);
            var cluster = new Cluster(core);
            cluster.activeBatch = new Batch(key, 128, 5);
            when(core.eap$getSuperMatrixCluster()).thenReturn(cluster);
            assertTrue(reader.supports(core));
            var sample = reader.read(core, context).sample();
            assertEquals(128, sample.parallel());
            assertEquals(0.25, sample.progressTicks() / (double) sample.durationTicks(), 0.00001);
            assertEquals(State.RUNNING, sample.state());
            var otherCore = machine(SuperAssemblerMatrixBlockEntity.class);
            when(otherCore.eap$getSuperMatrixCluster()).thenReturn(cluster);
            when(core.getBlockPos()).thenReturn(new BlockPos(7, 0, 0));
            var linked = reader.read(otherCore, context);
            assertNull(linked.sample());
            assertEquals(core.getBlockPos(), linked.links().getFirst().position());
        }
    }

    @Test void queuedRecipeNeverInheritsAnotherRecipeProgressAndBufferedOutputIsWaiting() {
        var core = machine(SuperAssemblerMatrixBlockEntity.class);
        var cluster = new Cluster(core);
        when(core.eap$getSuperMatrixCluster()).thenReturn(cluster);
        cluster.activeBatch = new Batch(AEItemKey.of(Items.DIRT), 100, 19);
        cluster.batchQueue = Map.of(key, new Queued(key, 100));
        var sample = reader.read(core, context).sample();
        assertEquals(0, sample.durationTicks());
        assertEquals(State.QUEUED, sample.state());
        cluster.outputBuffer = Map.of(key, 10L);
        sample = reader.read(core, context).sample();
        assertEquals(State.WAITING, sample.state());
        assertEquals(sample.durationTicks(), sample.progressTicks());
    }

    private static CraftingThread thread(AEKey output, int progress) {
        var thread = mock(CraftingThread.class);
        var pattern = mock(IMolecularAssemblerSupportedPattern.class);
        when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(output, 1)));
        set(thread, "myPlan", pattern);
        when(thread.isAwake()).thenReturn(true);
        when(thread.getCraftingProgress()).thenReturn(progress);
        return thread;
    }
    abstract static class Ultimate extends SuperAssemblerMatrixBlockEntity {}
    public record Stats(int singleCraftTicks) {}
    public record Batch(AEKey outputKey, long batchSize, int progress) {}
    public record Queued(AEKey outputKey, long pending) {}
    public static class Cluster {
        final BlockEntity core;
        Object activeBatch;
        List<Object> fallbackThreads = List.of();
        Map<?, ?> batchQueue = Map.of();
        Map<?, ?> outputBuffer = Map.of();
        Cluster(BlockEntity core) { this.core = core; }
        public BlockEntity getCore() { return core; }
        public Stats getStats() { return new Stats(20); }
        public boolean isDestroyed() { return false; }
    }
}
