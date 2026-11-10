package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.core.sync.packets.CraftingStatusPacket;
import appeng.menu.me.common.IncrementalUpdateHelper;
import appeng.menu.me.crafting.CraftingStatus;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPULogic;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class CraftingCpuMenuSynchronizationTest {
    @Test
    void ecoSelectionRunsNativeAndAddonCleanupBeforeBuildingItsOwnFullSnapshot() throws Exception {
        var menu = mock(CraftingCpuMenuMixin.class, CALLS_REAL_METHODS);
        var helper = new IncrementalUpdateHelper();
        Consumer<AEKey> listener = helper::addChange;
        var oldCpu = mock(ECOCraftingCPU.class);
        var oldLogic = mock(ECOCraftingCPULogic.class);
        when(oldCpu.getLogic()).thenReturn(oldLogic);
        var nextCpu = mock(ECOCraftingCPU.class);
        var nextLogic = mock(ECOCraftingCPULogic.class);
        when(nextCpu.getLogic()).thenReturn(nextLogic);
        AEKey stale = mock(AEKey.class), current = mock(AEKey.class);
        List<String> events = new ArrayList<>();
        set(menu, "neoecoae$cpu", oldCpu);
        set(menu, "incrementalUpdateHelper", helper);
        set(menu, "cpuChangeListener", listener);
        doAnswer(call -> {
            events.add("collect");
            ((KeyCounter) call.getArgument(0)).add(current, 1);
            return null;
        }).when(nextLogic).getAllItems(any());
        Operation<Void> original = args -> {
            assertSame(nextCpu, args[0]);
            assertNull(get(menu, "neoecoae$cpu"));
            verify(oldLogic).removeListener(listener);
            events.add("original");
            helper.addChange(stale);
            helper.getOrAssignSerial(stale);
            helper.commitChanges();
            return null;
        };

        select(menu, nextCpu, original);

        assertEquals(List.of("original", "collect"), events);
        assertSame(nextCpu, get(menu, "neoecoae$cpu"));
        assertTrue(helper.isFullUpdate());
        assertNull(helper.getSerial(stale));
        List<AEKey> keys = new ArrayList<>();
        helper.forEach(keys::add);
        assertEquals(List.of(current), keys);
        verify(nextLogic).addListener(listener);
    }

    @Test
    void leavingEcoDetachesItsListenerEvenWhenAnotherAddonHandlesTheNewCpu() throws Exception {
        var menu = mock(CraftingCpuMenuMixin.class, CALLS_REAL_METHODS);
        var oldCpu = mock(ECOCraftingCPU.class);
        var oldLogic = mock(ECOCraftingCPULogic.class);
        when(oldCpu.getLogic()).thenReturn(oldLogic);
        Consumer<AEKey> listener = ignored -> {};
        set(menu, "neoecoae$cpu", oldCpu);
        set(menu, "cpuChangeListener", listener);
        var foreign = mock(ICraftingCPU.class);
        List<Object> selections = new ArrayList<>();

        select(menu, foreign, args -> {
            assertNull(get(menu, "neoecoae$cpu"));
            verify(oldLogic).removeListener(listener);
            selections.add(args[0]);
            return null; // Models another addon's cancellable selection handler returning normally.
        });

        assertEquals(List.of(foreign), selections);
        assertNull(get(menu, "neoecoae$cpu"));
    }

    @Test
    void clearingEcoAlsoClearsClientRowsWhenTheNativeSelectionReturnsEarly() throws Exception {
        var menu = mock(CraftingCpuMenuMixin.class, CALLS_REAL_METHODS);
        doReturn(null).when(menu).getPlayer();
        var cpu = mock(ECOCraftingCPU.class);
        when(cpu.getLogic()).thenReturn(mock(ECOCraftingCPULogic.class));
        var helper = new IncrementalUpdateHelper();
        helper.commitChanges();
        set(menu, "neoecoae$cpu", cpu);
        set(menu, "incrementalUpdateHelper", helper);
        set(menu, "cpuChangeListener", (Consumer<AEKey>) ignored -> {});
        List<List<?>> packets = new ArrayList<>();
        try (var ignored = mockConstruction(CraftingStatusPacket.class,
                (packet, context) -> packets.add(context.arguments()))) {
            select(menu, null, args -> {
                assertNull(args[0]);
                assertNull(get(menu, "neoecoae$cpu"));
                return null;
            });
        }

        assertTrue(helper.isFullUpdate());
        assertEquals(1, packets.size());
        assertSame(CraftingStatus.EMPTY, packets.get(0).get(1));
    }

    private static void select(CraftingCpuMenuMixin menu, ICraftingCPU cpu, Operation<Void> original)
            throws Exception {
        Method method = CraftingCpuMenuMixin.class.getDeclaredMethod("onSetCPU", ICraftingCPU.class, Operation.class);
        method.setAccessible(true);
        method.invoke(menu, cpu, original);
    }

    private static Object get(Object object, String name) {
        try {
            var field = CraftingCpuMenuMixin.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void set(Object object, String name, Object value) throws Exception {
        var field = CraftingCpuMenuMixin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
}
