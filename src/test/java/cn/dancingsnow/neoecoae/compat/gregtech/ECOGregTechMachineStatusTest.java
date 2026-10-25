package cn.dancingsnow.neoecoae.compat.gregtech;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider.Target;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ECOGregTechMachineStatusTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void actualRecipeProgressAndParallelCountAreReadWithoutUsingTheDispatchBatchSize() {
        var logic = new Logic();
        var result = ECOGregTechMachineStatus.sampleMachine(logic, Component.literal("Machine"), "test", AEItemKey.of(Items.STONE));
        assertEquals(128, result.progressTicks());
        assertEquals(200, result.durationTicks());
        assertEquals(16, result.parallel());
        assertEquals("test:stone", result.recipe());
        assertEquals(State.RUNNING, result.state());
    }

    @Test void aDifferentProductDoesNotReceiveTheMachinesProgress() {
        var result = ECOGregTechMachineStatus.sampleMachine(new Logic(), Component.literal("Machine"), "test", AEItemKey.of(Items.DIRT));
        assertEquals(State.QUEUED, result.state());
        assertEquals(0, result.durationTicks());
        assertEquals("", result.recipe());
    }

    @Test void waitingReasonAndPausedProgressRemainVisible() {
        var logic = new Logic();
        logic.waiting = true;
        var waiting = ECOGregTechMachineStatus.sampleMachine(logic, Component.empty(), "test", AEItemKey.of(Items.STONE));
        assertEquals(State.WAITING, waiting.state());
        assertEquals("Not enough energy", waiting.reason().getString());
        assertEquals(128, waiting.progressTicks());
        logic.waiting = false;
        logic.paused = true;
        assertEquals(State.PAUSED, ECOGregTechMachineStatus.sampleMachine(logic, Component.empty(), "test", AEItemKey.of(Items.STONE)).state());
    }

    @Test void fluidOutputsAreMatchedThroughIngredientStacks() {
        var logic = new Logic();
        logic.recipe.outputs = Map.of("fluid", List.of(new Content(new FluidOutput())));
        var result = ECOGregTechMachineStatus.sampleMachine(logic, Component.empty(), "test", AEFluidKey.of(Fluids.WATER));
        assertEquals(State.RUNNING, result.state());
        assertEquals(200, result.durationTicks());
    }

    @Test void aSuccessfulProvidersExactDestinationIsUsedInsteadOfScanningNeighbors() {
        var provider = mock(ICraftingProvider.class, withSettings().extraInterfaces(ECOProcessingTargetProvider.class));
        var traced = (ECOProcessingTargetProvider) provider;
        var target = new Target(Level.OVERWORLD.location(), new BlockPos(10, 64, 20));
        when(traced.neoecoae$getProcessingTarget()).thenReturn(target);
        assertEquals(List.of(target), ECOGregTechMachineStatus.targets(provider));
        when(traced.neoecoae$getProcessingTarget()).thenReturn(null);
        assertTrue(ECOGregTechMachineStatus.targets(provider).isEmpty());
    }

    @Test void anUnloadedTargetNeverTriggersABlockEntityOrChunkLookup() {
        var level = mock(ServerLevel.class);
        var server = mock(MinecraftServer.class);
        when(level.getServer()).thenReturn(server);
        when(server.getLevel(Level.OVERWORLD)).thenReturn(level);
        var scan = new ECOGregTechMachineStatus.Scan(List.of(new Target(Level.OVERWORLD.location(), BlockPos.ZERO)), AEItemKey.of(Items.STONE));
        scan.step(level);
        assertTrue(scan.complete());
        assertEquals(State.UNAVAILABLE, scan.machines().get(0).state());
        verify(level, never()).getBlockEntity(any());
        verify(level, never()).getChunk(anyInt(), anyInt());
    }

    @Test void aRecordedPatternAssemblyTargetFollowsStoredControllerPositionsBeforeReadingItsCycle() {
        var level = mock(ServerLevel.class);
        var server = mock(MinecraftServer.class);
        when(level.getServer()).thenReturn(server);
        when(server.getLevel(Level.OVERWORLD)).thenReturn(level);
        when(level.hasChunkAt(any())).thenReturn(true);
        var hatch = new BlockPos(210, 35, -151);
        var controller = new BlockPos(210, 38, -152);
        var hatchHolder = holder(new Part(controller));
        var controllerHolder = holder(new Controller());
        when(level.getBlockEntity(hatch)).thenReturn(hatchHolder);
        when(level.getBlockEntity(controller)).thenReturn(controllerHolder);
        var scan = new ECOGregTechMachineStatus.Scan(List.of(new Target(Level.OVERWORLD.location(), hatch)), AEItemKey.of(Items.STONE));
        scan.step(level);
        assertFalse(scan.complete());
        assertTrue(scan.machines().isEmpty());
        verify(level, never()).getBlockEntity(controller);
        scan.step(level);
        assertTrue(scan.complete());
        assertEquals(1, scan.machines().size());
        assertEquals(128, scan.machines().get(0).progressTicks());
        assertEquals(200, scan.machines().get(0).durationTicks());
        verify(level, never()).getChunk(anyInt(), anyInt());
    }

    private static BlockEntity holder(Object machine) {
        var holder = mock(BlockEntity.class, withSettings().extraInterfaces(MetaHolder.class));
        when(((MetaHolder) holder).getMetaMachine()).thenReturn(machine);
        when(holder.getBlockState()).thenReturn(Blocks.STONE.defaultBlockState());
        return holder;
    }

    public interface MetaHolder { Object getMetaMachine(); }
    public static class PartBase {
        protected final java.util.Set<BlockPos> controllerPositions;
        PartBase(BlockPos controller) { controllerPositions = java.util.Set.of(controller); }
    }
    public static final class Part extends PartBase { Part(BlockPos controller) { super(controller); } }
    public static final class Controller { public Logic getRecipeLogic() { return new Logic(); } }

    public static final class Logic {
        public Recipe recipe = new Recipe();
        public boolean waiting, paused;
        public Recipe getLastRecipe() { return recipe; }
        public int getProgress() { return 128; }
        public int getMaxProgress() { return 200; }
        public boolean isWorking() { return !waiting && !paused; }
        public boolean isWaiting() { return waiting; }
        public boolean isSuspend() { return paused; }
        public Component getWaitingReason() { return Component.literal("Not enough energy"); }
    }

    public static final class Recipe {
        public ResourceLocation id = new ResourceLocation("test", "stone");
        public Map<?, ?> outputs = Map.of("item", List.of(new Content(Ingredient.of(Items.STONE))));
        public long getRealParallels() { return 16; }
    }
    public record Content(Object content) {}
    public static final class FluidOutput { public FluidStack[] getStacks() { return new FluidStack[]{new FluidStack(Fluids.WATER, 1000)}; } }
}
