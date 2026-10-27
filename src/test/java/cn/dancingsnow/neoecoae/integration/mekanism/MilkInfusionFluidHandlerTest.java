package cn.dancingsnow.neoecoae.integration.mekanism;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalTankBuilder;
import mekanism.api.chemical.infuse.IInfusionTank;
import mekanism.api.chemical.infuse.InfuseType;
import mekanism.api.chemical.infuse.InfuseTypeBuilder;
import mekanism.api.chemical.infuse.InfusionStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.registries.RegistryBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MilkInfusionFluidHandlerTest {
    private static InfuseType milk;
    private static InfuseType carbon;
    private IInfusionTank tank;
    private MilkInfusionFluidHandler handler;
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicInteger saves = new AtomicInteger();

    @BeforeAll static void bootstrap() throws ReflectiveOperationException {
        InventoryTestBootstrap.initialize();
        var registry = chemicalRegistry(MekanismAPI.INFUSE_TYPE_REGISTRY_NAME, MekanismAPI.EMPTY_INFUSE_TYPE);
        // The real recipe deserializer initializes all four chemical ingredient families.
        chemicalRegistry(MekanismAPI.GAS_REGISTRY_NAME, MekanismAPI.EMPTY_GAS);
        chemicalRegistry(MekanismAPI.PIGMENT_REGISTRY_NAME, MekanismAPI.EMPTY_PIGMENT);
        chemicalRegistry(MekanismAPI.SLURRY_REGISTRY_NAME, MekanismAPI.EMPTY_SLURRY);
        milk = new InfuseType(InfuseTypeBuilder.builder());
        carbon = new InfuseType(InfuseTypeBuilder.builder());
        registry.register(new ResourceLocation("neoecoae", "milk"), milk);
        registry.register(new ResourceLocation("milk_test", "carbon"), carbon);
    }

    private static <T> net.minecraftforge.registries.IForgeRegistry<T> chemicalRegistry(
            net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<T>> name, T empty)
            throws ReflectiveOperationException {
        var builder = new RegistryBuilder<T>().setName(name.location())
            .setDefaultKey(new ResourceLocation("mekanism", "empty")).hasTags();
        // Forge normally creates custom chemical registries through NewRegistryEvent.
        var create = RegistryBuilder.class.getDeclaredMethod("create");
        create.setAccessible(true);
        @SuppressWarnings("unchecked")
        var registry = (net.minecraftforge.registries.IForgeRegistry<T>) create.invoke(builder);
        registry.register(new ResourceLocation("mekanism", "empty"), empty);
        return registry;
    }

    @BeforeEach void setup() {
        tank = newTank();
        // Water stands in for the optional Forge milk fluid; no game registry event is needed.
        handler = new MilkInfusionFluidHandler(tank, milk, Fluids.WATER, enabled::get, saves::incrementAndGet);
    }

    private static IInfusionTank newTank() {
        return ChemicalTankBuilder.INFUSION.create(1000, ChemicalTankBuilder.INFUSION.alwaysTrueBi,
            ChemicalTankBuilder.INFUSION.alwaysTrueBi, type -> true, () -> {});
    }

    @Test void simulationDoesNotConsumeFluidOrModifyInfusionOrSave() {
        FluidStack source = new FluidStack(Fluids.WATER, 1500);
        assertEquals(1000, handler.fill(source, FluidAction.SIMULATE));
        assertTrue(tank.isEmpty());
        assertEquals(1500, source.getAmount());
        assertEquals(0, saves.get());
    }

    @Test void executionClampsToCapacityAndPreservesTheCallerStack() {
        FluidStack source = new FluidStack(Fluids.WATER, 750);
        assertEquals(750, handler.fill(source, FluidAction.EXECUTE));
        assertEquals(250, handler.fill(source, FluidAction.EXECUTE));
        assertEquals(0, handler.fill(source, FluidAction.EXECUTE));
        assertEquals(1000, tank.getStored());
        assertEquals(1000, handler.getFluidInTank(0).getAmount());
        assertEquals(750, source.getAmount());
        assertEquals(2, saves.get());
    }

    @Test void foreignFluidOrExistingInfusionIsNeverReplaced() {
        assertEquals(0, handler.fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE));
        tank.setStack(new InfusionStack(carbon, 200));
        assertEquals(0, handler.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE));
        assertEquals(200, tank.getStored());
        assertSame(carbon, tank.getType());
        assertTrue(handler.getFluidInTank(0).isEmpty());
        assertEquals(0, saves.get());
    }

    @Test void cachedPipeHandlerHonorsDisabledSideAndCannotDrainMilkBackOut() {
        enabled.set(false);
        assertEquals(0, handler.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE));
        enabled.set(true);
        assertEquals(1000, handler.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE));
        assertTrue(handler.drain(1000, FluidAction.EXECUTE).isEmpty());
        assertTrue(handler.drain(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE).isEmpty());
        assertEquals(1000, tank.getStored());
    }

    @Test void milkSurvivesNativeTankSaveLoadAndCanBeConsumedByTheMachine() {
        handler.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        IInfusionTank restored = newTank();
        restored.deserializeNBT(tank.serializeNBT());
        assertSame(milk, restored.getType());
        assertEquals(1000, restored.getStored());
        assertEquals(1000, restored.extract(1000, Action.EXECUTE, AutomationType.INTERNAL).getAmount());
        assertTrue(restored.isEmpty());
    }

    @Test void milkInputFollowsTheInfusionInputSideAndRejectsOutputOnlySides() {
        var config = mock(mekanism.common.tile.component.TileComponentConfig.class);
        var slot = mock(mekanism.common.tile.component.config.slot.ISlotInfo.class);
        var side = net.minecraft.core.Direction.EAST;
        when(config.getSlotInfo(mekanism.common.lib.transmitter.TransmissionType.INFUSION, side)).thenReturn(slot);
        assertFalse(MilkInfusionIntegration.allowsInput(config, side));
        when(slot.canInput()).thenReturn(true);
        assertTrue(MilkInfusionIntegration.allowsInput(config, side));
        when(slot.canInput()).thenReturn(false);
        when(slot.canOutput()).thenReturn(true);
        assertFalse(MilkInfusionIntegration.allowsInput(config, side));
    }

    @Test void actualMekanismSerializerLoadsTheHiddenRecipeAndRequiresAFullBucket() throws Exception {
        var doll = net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.ITEM,
            "useless_stretcher:two_i_doll", new net.minecraft.world.item.Item(new net.minecraft.world.item.Item.Properties()));
        var disk = net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.ITEM,
            "neoecoae:eco_infinite_sand_cell", new net.minecraft.world.item.Item(new net.minecraft.world.item.Item.Properties()));
        var json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/resources/data/neoecoae/recipes/compat/useless_stretcher/infinite_sand_cell.json"))).getAsJsonObject();
        var serializer = new mekanism.common.recipe.serializer.MetallurgicInfuserRecipeSerializer<>(
            mekanism.common.recipe.impl.MetallurgicInfuserIRecipe::new);
        var recipe = serializer.fromJson(cn.dancingsnow.neoecoae.integration.xei.HiddenEasterEggRecipes.INFINITE_SAND, json);
        var input = new net.minecraft.world.item.ItemStack(doll);
        assertTrue(recipe.test(input, new InfusionStack(milk, 1000)));
        assertFalse(recipe.test(input, new InfusionStack(milk, 999)));
        assertFalse(recipe.test(input, new InfusionStack(carbon, 1000)));
        assertTrue(recipe.getOutput(input, new InfusionStack(milk, 1000)).is(disk));
    }
}
