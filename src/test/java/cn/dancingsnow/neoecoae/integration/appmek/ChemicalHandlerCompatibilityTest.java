package cn.dancingsnow.neoecoae.integration.appmek;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import cn.dancingsnow.neoecoae.mixins.compat.appmek.MekanismExternalStorageStrategyMixin;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.StreamSupport;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import me.ramidzkh.mekae2.ae2.stack.ChemicalHandlerFacade;
import me.ramidzkh.mekae2.ae2.stack.MekanismExternalStorageStrategy;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.api.chemical.infuse.IInfusionHandler;
import mekanism.api.chemical.infuse.InfusionStack;
import mekanism.api.chemical.pigment.IPigmentHandler;
import mekanism.api.chemical.pigment.PigmentStack;
import mekanism.api.chemical.slurry.ISlurryHandler;
import mekanism.api.chemical.slurry.SlurryStack;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.injection.ModifyArg;

class ChemicalHandlerCompatibilityTest {
    private static final long OFFERED = 37;

    @BeforeAll
    static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        // Forge normally registers Mekanism's empty chemicals before any stack is loaded.
        var gas = registry(MekanismAPI.EMPTY_GAS);
        var infusion = registry(MekanismAPI.EMPTY_INFUSE_TYPE);
        var pigment = registry(MekanismAPI.EMPTY_PIGMENT);
        var slurry = registry(MekanismAPI.EMPTY_SLURRY);
        try (var api = mockStatic(MekanismAPI.class)) {
            api.when(MekanismAPI::gasRegistry).thenReturn(gas);
            api.when(MekanismAPI::infuseTypeRegistry).thenReturn(infusion);
            api.when(MekanismAPI::pigmentRegistry).thenReturn(pigment);
            api.when(MekanismAPI::slurryRegistry).thenReturn(slurry);
            for (Class<?> stack : List.of(GasStack.class, InfusionStack.class, PigmentStack.class, SlurryStack.class)) {
                Class.forName(stack.getName(), true, stack.getClassLoader());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <C extends Chemical<C>> IForgeRegistry<C> registry(C chemical) {
        IForgeRegistry<C> registry = mock(IForgeRegistry.class);
        Holder.Reference<C> holder = mock(Holder.Reference.class);
        when(holder.get()).thenReturn(chemical);
        when(registry.getKey(chemical)).thenReturn(new ResourceLocation("mekanism", "empty"));
        when(registry.getDelegateOrThrow(chemical)).thenReturn(holder);
        return registry;
    }

    @Test
    void unfilteredSourceBlockStyleCapabilityReproducesTheReportedInfusionCast() {
        var gas = new GasOnlyHandler();
        var facade = new ChemicalHandlerFacade(new IChemicalHandler[] {gas, gas, gas, gas}, false, null);
        var failure = assertThrows(ClassCastException.class,
            () -> facade.insert(key(MekanismKey.INFUSION), OFFERED, Actionable.MODULATE, IActionSource.empty()));
        assertTrue(failure.getMessage().contains("InfusionStack"));
        assertTrue(failure.getMessage().contains("GasStack"));
        assertEquals(0, gas.insertions, "the generated bridge fails before any resource can be accepted");
    }

    @Test
    void misadvertisedGasHandlerRejectsOtherFormsInSimulationAndCommit() throws Exception {
        var gas = new GasOnlyHandler();
        Runnable changes = mock(Runnable.class);
        var handlers = filter(new IChemicalHandler[] {gas, gas, gas, gas});
        assertSame(gas, handlers[MekanismKey.GAS]);
        var facade = new ChemicalHandlerFacade(handlers, false, changes);
        for (byte form : new byte[] {MekanismKey.INFUSION, MekanismKey.PIGMENT, MekanismKey.SLURRY}) {
            assertNull(handlers[form]);
            for (Actionable mode : Actionable.values()) {
                var key = key(form);
                assertEquals(0, facade.insert(key, OFFERED, mode, IActionSource.empty()));
                assertEquals(0, facade.extract(key, OFFERED, mode, IActionSource.empty()));
                verify(key, never()).withAmount(anyLong());
            }
        }
        assertEquals(0, gas.insertions);
        verifyNoInteractions(changes);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 11, OFFERED})
    void rejectedRefundContinuesToOtherStorageAndLeavesAnyOverflowWithTheCaller(long capacity) throws Exception {
        var gas = new GasOnlyHandler();
        var facade = new ChemicalHandlerFacade(filter(new IChemicalHandler[] {gas, gas, gas, gas}), false, null);
        AtomicLong stock = new AtomicLong();
        MEStorage fallback = mock(MEStorage.class);
        when(fallback.insert(any(), anyLong(), any(), any())).thenAnswer(call -> {
            long accepted = Math.min((long) call.getArgument(1), capacity - stock.get());
            if (call.getArgument(2) == Actionable.MODULATE) stock.addAndGet(accepted);
            return accepted;
        });
        var network = new NetworkStorage();
        network.mount(100, facade);
        network.mount(0, fallback);
        var key = key(MekanismKey.INFUSION);
        assertEquals(capacity, network.insert(key, OFFERED, Actionable.SIMULATE, IActionSource.empty()));
        assertEquals(0, stock.get());
        long inserted = network.insert(key, OFFERED, Actionable.MODULATE, IActionSource.empty());
        assertEquals(capacity, inserted);
        assertEquals(capacity, stock.get());
        assertEquals(OFFERED, stock.get() + (OFFERED - inserted), "accepted stock plus caller remainder is conserved");
        assertEquals(0, gas.insertions);
    }

    @Test
    void matchingHandlersKeepTheirNormalInsertExtractAndNotificationBehavior() throws Exception {
        IChemicalHandler<?, ?>[] handlers = {mock(IGasHandler.class), mock(IInfusionHandler.class),
            mock(IPigmentHandler.class), mock(ISlurryHandler.class)};
        var checked = filter(handlers);
        assertArrayEquals(handlers, checked);
        Runnable changes = mock(Runnable.class);
        var facade = new ChemicalHandlerFacade(checked, false, changes);
        for (byte form = 0; form < handlers.length; form++) {
            var key = key(form);
            ChemicalStack<?> empty = mock(ChemicalStack.class);
            ChemicalStack<?> extracted = mock(ChemicalStack.class);
            when(extracted.getAmount()).thenReturn(OFFERED);
            IChemicalHandler handler = handlers[form];
            doReturn(empty).when(handler).insertChemical(any(ChemicalStack.class), any());
            doReturn(extracted).when(handler).extractChemical(any(ChemicalStack.class), any());
            for (Actionable mode : Actionable.values()) {
                assertEquals(OFFERED, facade.insert(key, OFFERED, mode, IActionSource.empty()));
                assertEquals(OFFERED, facade.extract(key, OFFERED, mode, IActionSource.empty()));
            }
            verify(handler).insertChemical(any(ChemicalStack.class), eq(Action.SIMULATE));
            verify(handler).insertChemical(any(ChemicalStack.class), eq(Action.EXECUTE));
            verify(handler).extractChemical(any(ChemicalStack.class), eq(Action.SIMULATE));
            verify(handler).extractChemical(any(ChemicalStack.class), eq(Action.EXECUTE));
        }
        verify(changes, times(8)).run();
    }

    @Test
    void listingDoesNotVisitTheSameGasCapabilityAgainUnderWrongForms() throws Exception {
        IGasHandler gas = mock(IGasHandler.class);
        var offered = new IChemicalHandler[] {gas, gas, gas, gas};
        var checked = filter(offered);
        new ChemicalHandlerFacade(checked, false, null).getAvailableStacks(new KeyCounter());
        verify(gas).getTanks();
        assertArrayEquals(new IChemicalHandler[] {gas, gas, gas, gas}, offered, "the discovery array is not mutated");
    }

    @Test
    void hookMatchesTheActualPinnedAppliedMekanisticsConstructorCall() throws Exception {
        Method hook = hook();
        ModifyArg modify = hook.getAnnotation(ModifyArg.class);
        assertNotNull(modify);
        assertEquals(0, modify.index());
        var target = new ClassNode();
        try (var stream = MekanismExternalStorageStrategy.class.getResourceAsStream("MekanismExternalStorageStrategy.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(target, 0);
        }
        long calls = target.methods.stream().filter(method -> method.name.equals(modify.method()[0]))
            .flatMap(method -> StreamSupport.stream(method.instructions.spliterator(), false))
            .filter(instruction -> instruction instanceof MethodInsnNode call
                && ("L" + call.owner + ";" + call.name + call.desc).equals(modify.at().target())
                && Type.getArgumentTypes(call.desc)[modify.index()].getDescriptor()
                    .equals(Type.getDescriptor(hook.getReturnType())))
            .count();
        assertEquals(1, calls);
    }

    private static Method hook() throws Exception {
        return MekanismExternalStorageStrategyMixin.class.getDeclaredMethod(
            "neoecoae$validateHandlerTypes", IChemicalHandler[].class);
    }

    private static IChemicalHandler[] filter(IChemicalHandler[] handlers) throws Exception {
        Method hook = hook();
        hook.setAccessible(true);
        return (IChemicalHandler[]) hook.invoke(new MekanismExternalStorageStrategyMixin() {}, (Object) handlers);
    }

    private static MekanismKey key(byte form) {
        MekanismKey key = mock(MekanismKey.class);
        when(key.getForm()).thenReturn(form);
        ChemicalStack<?> stack = switch (form) {
            case MekanismKey.GAS -> mock(GasStack.class);
            case MekanismKey.INFUSION -> mock(InfusionStack.class);
            case MekanismKey.PIGMENT -> mock(PigmentStack.class);
            case MekanismKey.SLURRY -> mock(SlurryStack.class);
            default -> throw new IllegalArgumentException("Unknown chemical form");
        };
        when(stack.getAmount()).thenReturn(OFFERED);
        doReturn(stack).when(key).withAmount(anyLong());
        return key;
    }

    /** Uses Java's real GasStack bridge cast, just like Source Block's handler. */
    private static final class GasOnlyHandler implements IGasHandler {
        int insertions;
        @Override public int getTanks() { return 1; }
        @Override public GasStack getChemicalInTank(int tank) { return GasStack.EMPTY; }
        @Override public void setChemicalInTank(int tank, GasStack stack) {}
        @Override public long getTankCapacity(int tank) { return Long.MAX_VALUE; }
        @Override public boolean isValid(int tank, GasStack stack) { return true; }
        @Override public GasStack insertChemical(int tank, GasStack stack, Action action) {
            return insertChemical(stack, action);
        }
        @Override public GasStack insertChemical(GasStack stack, Action action) {
            insertions++;
            return GasStack.EMPTY;
        }
        @Override public GasStack extractChemical(int tank, long amount, Action action) { return GasStack.EMPTY; }
    }
}
