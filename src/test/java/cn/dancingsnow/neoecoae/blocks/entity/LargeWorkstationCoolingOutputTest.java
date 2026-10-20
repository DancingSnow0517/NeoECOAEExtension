package cn.dancingsnow.neoecoae.blocks.entity;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LargeWorkstationCoolingOutputTest {
    private static final ResourceLocation FIRST = ResourceLocation.parse("test:first_coolant");
    private static final ResourceLocation SECOND = ResourceLocation.parse("test:second_coolant");
    private static final FluidStack OUTPUT = new FluidStack(Fluids.WATER, 1);

    @Test
    void fractionalByproductIsCarriedAcrossTicksWithoutLoss() {
        var state = new LargeWorkstationCoolingOutput();

        assertEquals(66, process(state, FIRST, 300, 200));
        assertEquals(67, process(state, FIRST, 300, 200));
        assertEquals(67, process(state, FIRST, 300, 200));
    }

    @Test
    void remainderIsIsolatedByRecipeAndSurvivesSaveReload() {
        var state = new LargeWorkstationCoolingOutput();
        assertEquals(0, process(state, FIRST, 300, 1));
        assertEquals(0, process(state, SECOND, 300, 2));

        var tag = new CompoundTag();
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        state.save(tag, registries);
        var restored = new LargeWorkstationCoolingOutput();
        restored.load(tag, registries);

        assertEquals(0, process(restored, FIRST, 300, 1));
        assertEquals(1, process(restored, FIRST, 300, 1));
        assertEquals(1, process(restored, SECOND, 300, 2));
    }

    @Test
    void invalidOrUnrepresentableConversionsAreRejectedBeforeConsumption() {
        var state = new LargeWorkstationCoolingOutput();
        assertNull(state.plan(FIRST, OUTPUT, 0, 1, 100));
        assertNull(state.plan(FIRST, OUTPUT, 1, Integer.MAX_VALUE, 100));
    }

    @Test
    void rejectedOutputDoesNotAdvanceRemainder() {
        var state = new LargeWorkstationCoolingOutput();
        var rejected = state.plan(FIRST, OUTPUT, 300, 200, 100);
        assertNotNull(rejected);

        assertEquals(66, process(state, FIRST, 300, 200));
        assertEquals(67, process(state, FIRST, 300, 200));
    }

    @Test
    void malformedSavedRemainderIsIgnored() {
        var tag = new CompoundTag();
        var entries = new ListTag();
        var invalid = new CompoundTag();
        invalid.putString("recipe", FIRST.toString());
        invalid.putInt("recipeInput", 300);
        invalid.putInt("recipeOutput", 200);
        invalid.putInt("amount", 100);
        entries.add(invalid);
        tag.put("coolingOutputRemainders", entries);

        var state = new LargeWorkstationCoolingOutput();
        state.load(tag, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        assertEquals(66, process(state, FIRST, 300, 200));
    }

    private static int process(LargeWorkstationCoolingOutput state, ResourceLocation id, int input, int output) {
        var plan = state.plan(id, OUTPUT, input, output, 100);
        assertNotNull(plan);
        int produced = plan.output().getAmount();
        state.commit(plan);
        return produced;
    }
}
