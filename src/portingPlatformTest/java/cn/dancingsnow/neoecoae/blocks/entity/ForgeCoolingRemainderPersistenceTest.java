package cn.dancingsnow.neoecoae.blocks.entity;

import static org.junit.jupiter.api.Assertions.*;

import cn.dancingsnow.neoecoae.platform.PlatformTestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ForgeCoolingRemainderPersistenceTest {
    private static final ResourceLocation RECIPE = ResourceLocation.fromNamespaceAndPath("test", "cooling");
    @BeforeAll static void bootstrap() { PlatformTestBootstrap.initialize(); }

    @Test void fractionalOutputAndFluidNbtSurviveSaveReload() {
        var state = new LargeWorkstationCoolingOutput();
        var output = new FluidStack(Fluids.WATER, 1);
        output.getOrCreateTag().putString("grade", "hot");
        var first = state.plan(RECIPE, output, 300, 200, 100);
        assertNotNull(first);
        assertEquals(66, first.output().getAmount());
        state.commit(first);
        var saved = new CompoundTag();
        state.save(saved);
        var reopened = new LargeWorkstationCoolingOutput();
        reopened.load(saved);
        var second = reopened.plan(RECIPE, output, 300, 200, 100);
        assertNotNull(second);
        assertEquals(67, second.output().getAmount());
        assertEquals("hot", second.output().getTag().getString("grade"));
        reopened.commit(second);
        assertEquals(67, reopened.plan(RECIPE, output, 300, 200, 100).output().getAmount());
    }

    @Test void changedFluidMetadataResetsRemainderAndRejectedPlanDoesNotCommit() {
        var state = new LargeWorkstationCoolingOutput();
        var output = new FluidStack(Fluids.WATER, 1);
        var rejected = state.plan(RECIPE, output, 300, 200, 100);
        assertEquals(66, rejected.output().getAmount());
        var accepted = state.plan(RECIPE, output, 300, 200, 100);
        assertEquals(66, accepted.output().getAmount());
        state.commit(accepted);
        output.getOrCreateTag().putString("grade", "changed");
        assertEquals(66, state.plan(RECIPE, output, 300, 200, 100).output().getAmount());
    }
}
