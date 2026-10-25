package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.api.me.attachment.ECOCraftingJobAttachmentRegistry;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobContext;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider.Target;
import cn.dancingsnow.neoecoae.api.me.lifecycle.ECOCraftingJobResult;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ECOProcessingMonitorTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void allThousandMachinesContributeAcrossBoundedStepsAndTooltipPagingDoesNotChangeTheAggregate() {
        var fixture = new Fixture();
        var monitor = monitor(1000);
        ECOProcessingProgress progress = ECOProcessingProgress.EMPTY;
        for (int tick = 0; tick < 40 && progress.machineCount() < 1000; tick++) {
            fixture.tick.set(tick);
            int before = fixture.reads.get();
            progress = monitor.sample(fixture.level, AEItemKey.of(Items.STONE), tick);
            assertTrue(fixture.reads.get() - before <= ECOProcessingMonitor.READS_PER_CPU_TICK);
            monitor.sample(fixture.level, AEItemKey.of(Items.STONE), tick);
            assertTrue(fixture.reads.get() - before <= ECOProcessingMonitor.READS_PER_CPU_TICK, "Repeated viewers share the tick budget");
        }
        assertEquals(1000, progress.machineCount());
        assertEquals(1000, fixture.reads.get());
        assertEquals(0.992F, progress.progress(), 0.00001F, "The first eight machines have zero progress; the others must contribute");
        var lastPage = progress.page(333);
        assertEquals(1000, lastPage.machineCount());
        assertEquals(1, lastPage.machines().size());
        assertEquals(progress.progress(), lastPage.progress());
        var decoded = ECOProcessingProgress.read(lastPage.write());
        assertEquals(1000, decoded.machineCount());
        assertEquals(progress.progress(), decoded.progress());
        assertTrue(progress.page(-1).machines().isEmpty());
    }

    @Test void differentCpuViewsAlsoShareTheServerBudget() {
        var fixture = new Fixture();
        for (int i = 0; i < 12; i++) monitor(100).sample(fixture.level, AEItemKey.of(Items.STONE), 0);
        assertEquals(ECOProcessingMonitor.READS_PER_SERVER_TICK, fixture.reads.get());
    }

    @Test void destinationPersistenceDoesNotTruncateAtEightOrSixtyFourAndFinishingReleasesState() {
        var original = monitor(1000);
        var restored = new ECOProcessingMonitor();
        restored.load(original.save());
        assertEquals(original.save(), restored.save());
        assertEquals(1000, restored.save().getList("entries", 10).getCompound(0).getList("targets", 10).size());
        restored.clear(new ECOCraftingJobResult(ECOCraftingJobResult.Status.CANCELLED, 1, 0, 1));
        assertTrue(restored.save().isEmpty());
    }

    @Test void attachmentBindingStartsFreshForEachJobOnTheSameCpu() {
        var id = new ResourceLocation("neoecoae", "processing_monitor");
        var cpu = mock(ECOCraftingCPU.class);
        var logic = mock(ECOCraftingCPULogic.class);
        var monitor = new ECOProcessingMonitor();
        when(cpu.getLogic()).thenReturn(logic);
        when(logic.getProcessingMonitor()).thenReturn(monitor);
        var first = new ECOCraftingJobContext(cpu, UUID.randomUUID(), null, 1, 1);
        boolean registeredHere = ECOCraftingJobAttachmentRegistry.create(id, first) == null;
        if (registeredHere) ECOProcessingMonitor.initialize();
        try {
            assertSame(monitor, ECOCraftingJobAttachmentRegistry.create(id, first));
            monitor.observe(AEItemKey.of(Items.STONE), new Target(Level.OVERWORLD.location(), BlockPos.ZERO));
            assertFalse(monitor.save().isEmpty());
            var next = new ECOCraftingJobContext(cpu, UUID.randomUUID(), null, 1, 1);
            assertSame(monitor, ECOCraftingJobAttachmentRegistry.create(id, next));
            assertTrue(monitor.save().isEmpty());
        } finally { if (registeredHere) ECOCraftingJobAttachmentRegistry.unregister(id); }
    }

    private static ECOProcessingMonitor monitor(int count) {
        var monitor = new ECOProcessingMonitor();
        for (int i = 0; i < count; i++) monitor.observe(AEItemKey.of(Items.STONE),
            new Target(Level.OVERWORLD.location(), new BlockPos(i, 64, 0)));
        return monitor;
    }

    private static final class Fixture {
        final ServerLevel level = mock(ServerLevel.class);
        final MinecraftServer server = mock(MinecraftServer.class);
        final AtomicInteger reads = new AtomicInteger(), tick = new AtomicInteger();
        Fixture() {
            when(level.getServer()).thenReturn(server);
            when(server.getLevel(Level.OVERWORLD)).thenReturn(level);
            when(server.getTickCount()).thenAnswer(ignored -> tick.get());
            when(level.hasChunkAt(any())).thenReturn(true);
            BlockEntity first = holder(0), rest = holder(200);
            when(level.getBlockEntity(any())).thenAnswer(invocation -> {
                reads.incrementAndGet();
                return invocation.<BlockPos>getArgument(0).getX() < 8 ? first : rest;
            });
        }
        BlockEntity holder(int progress) {
            var holder = mock(BlockEntity.class, withSettings().extraInterfaces(MetaHolder.class));
            when(holder.getBlockState()).thenReturn(Blocks.STONE.defaultBlockState());
            when(((MetaHolder) holder).getMetaMachine()).thenReturn(new Machine(progress));
            return holder;
        }
    }

    public interface MetaHolder { Object getMetaMachine(); }
    public record Machine(int progress) { public Logic getRecipeLogic() { return new Logic(progress); } }
    public record Logic(int progress) {
        public Recipe getLastRecipe() { return new Recipe(); }
        public int getProgress() { return progress; }
        public int getMaxProgress() { return 200; }
        public boolean isWorking() { return true; }
    }
    public static final class Recipe {
        public final Map<?, ?> outputs = Map.of("item", List.of(Ingredient.of(Items.STONE)));
        public final int parallels = 1;
    }
}
