package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.computation.ComputationInterfaceUI;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.integration.xei.jei.JEIUIEvents;
import com.lowdragmc.lowdraglib2.integration.xei.jei.handler.JEITargetsTypedHandler;
import com.lowdragmc.lowdraglib2.integration.xei.emi.EMIUIEvents;
import com.lowdragmc.lowdraglib2.integration.xei.emi.handler.EMIDragDropHandler;
import dev.emi.emi.api.stack.EmiStack;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PhantomItemSlotSideTest {
    @BeforeAll
    static void bootstrap() throws ClassNotFoundException {
        InventoryTestBootstrap.initialize();
        try (var mods = mockStatic(ModList.class);
             var registrate = mockStatic(NERegistrate.class)) {
            ModList modList = mock(ModList.class);
            when(modList.getAllScanData()).thenReturn(List.of());
            mods.when(ModList::get).thenReturn(modList);
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName(ItemSlot.class.getName());
            AccessorRegistries.init();
        }
    }

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void jeiCanMarkEmptyAndOccupiedFiltersWithoutMovingRealItems(boolean occupied, boolean tool) throws Exception {
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(true);
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isClient).thenReturn(true);
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            ldlib.when(LDLib2::isJeiLoaded).thenReturn(true);
            ldlib.when(() -> LDLib2.id(anyString())).thenAnswer(invocation ->
                ResourceLocation.fromNamespaceAndPath("ldlib2", invocation.getArgument(0)));

            UIElement grid = createGrid(level);
            List<ItemSlot> slots = new ArrayList<>();
            collectSlots(grid, slots);
            ItemSlot first = slots.getFirst();
            if (occupied) first.setValue(new ItemStack(Items.STONE), false);
            ItemStack dragged = new ItemStack(tool ? Items.DIAMOND_PICKAXE : Items.DIAMOND, tool ? 1 : 47);
            @SuppressWarnings("unchecked")
            ITypedIngredient<ItemStack> typed = mock(ITypedIngredient.class);
            when(typed.cast(VanillaTypes.ITEM_STACK)).thenReturn(typed);
            when(typed.getIngredient()).thenReturn(dragged);
            var targets = new JEITargetsTypedHandler<>(true, typed);
            UIEvent event = UIEvent.create(JEIUIEvents.GHOST_INGREDIENT);
            event.target = grid;
            event.customData = targets;
            UIEventDispatcher.dispatchAllChildren(event);

            assertEquals(63, targets.targets.size(), "JEI must expose every filter slot despite native insertion being disabled");
            targets.targets.getFirst().accept(dragged);
            assertEquals(dragged.getItem(), first.getValue().getItem());
            assertEquals(1, first.getValue().getCount());
            assertEquals(tool ? 1 : 47, dragged.getCount());
            assertNotSame(dragged, first.getValue());
            assertFalse(first.getSlot().mayPlace(dragged));
            assertFalse(first.getSlot().mayPickup(null));
            ItemStack carried = dragged.copy();
            assertEquals(dragged.getCount(), first.getSlot().safeInsert(carried).getCount());
            assertTrue(first.getSlot().remove(1).isEmpty());
            assertEquals(1, first.getValue().getCount());
        }
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void emiShowsFilterTargetsWithoutEnablingNativeInsertion(boolean tool) throws Exception {
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(true);
        try (var ldlib = mockStatic(LDLib2.class)) {
            ldlib.when(LDLib2::isClient).thenReturn(true);
            ldlib.when(LDLib2::isRemote).thenReturn(true);
            ldlib.when(LDLib2::isEmiLoaded).thenReturn(true);
            ldlib.when(() -> LDLib2.id(anyString())).thenAnswer(invocation ->
                ResourceLocation.fromNamespaceAndPath("ldlib2", invocation.getArgument(0)));
            UIElement grid = createGrid(level);
            ItemStack dragged = new ItemStack(tool ? Items.DIAMOND_PICKAXE : Items.DIAMOND);
            var targets = new EMIDragDropHandler(EmiStack.of(dragged));
            UIEvent event = UIEvent.create(EMIUIEvents.RENDER_DRAG_HANDLER);
            event.target = grid;
            event.customData = targets;
            UIEventDispatcher.dispatchAllChildren(event);
            assertEquals(63, targets.bounds.size());
            List<ItemSlot> slots = new ArrayList<>();
            collectSlots(grid, slots);
            for (ItemSlot slot : slots) {
                assertFalse(slot.getSlot().mayPlace(dragged));
                assertTrue(slot.getValue().isEmpty());
            }
            assertEquals(1, dragged.getCount());
        }
    }

    @ParameterizedTest
    @CsvSource({
        "dedicated", "integrated", "client", "no_level"
    })
    void computationGridOnlyRegistersPhantomHandlersOnTheLogicalClient(String side) throws Exception {
        boolean client = side.equals("client");
        Level level = side.equals("no_level") ? null : mock(Level.class);
        if (level != null) {
            when(level.isClientSide()).thenReturn(client);
        }
        try (var ldlib = mockStatic(LDLib2.class);
             var jei = mockStatic(ItemSlot.JEISupport.class);
             var emi = mockStatic(ItemSlot.EMISupport.class)) {
            ldlib.when(LDLib2::isClient).thenReturn(client || side.equals("integrated"));
            ldlib.when(LDLib2::isServer).thenReturn(!client);
            ldlib.when(LDLib2::isRemote).thenReturn(client);
            ldlib.when(() -> LDLib2.id(anyString())).thenAnswer(invocation ->
                ResourceLocation.fromNamespaceAndPath("ldlib2", invocation.getArgument(0)));
            // Recipe-viewer mods can be present on a dedicated server too.
            ldlib.when(LDLib2::isJeiLoaded).thenReturn(true);
            ldlib.when(LDLib2::isEmiLoaded).thenReturn(true);
            if (!client) {
                emi.when(() -> ItemSlot.EMISupport.renderDragHandler(any())).thenThrow(
                    new NoClassDefFoundError("net/minecraft/client/gui/components/Renderable"));
            }

            UIElement grid = assertDoesNotThrow(() -> createGrid(level));
            List<ItemSlot> slots = new ArrayList<>();
            collectSlots(grid, slots);
            assertEquals(63, slots.size());
            for (ItemSlot slot : slots) {
                assertNotNull(slot.getSlot());
                assertEquals(client ? 1 : 0, slot.getBubbleListeners(JEIUIEvents.GHOST_INGREDIENT).size());
                assertEquals(client ? 1 : 0, slot.getBubbleListeners(EMIUIEvents.RENDER_DRAG_HANDLER).size());
                assertEquals(client ? 1 : 0, slot.getBubbleListeners(EMIUIEvents.DROP_STACK_HANDLER).size());
            }
            if (!client) {
                jei.verifyNoInteractions();
                emi.verifyNoInteractions();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static UIElement createGrid(Level level) throws Exception {
        ECOMachineInterfaceBlockEntity<NEComputationCluster> host = mock(ECOMachineInterfaceBlockEntity.class);
        when(host.getLevel()).thenReturn(level);
        when(host.getFuzzyPlanningItemHandler()).thenReturn(new ItemStackHandler(63));
        var method = ComputationInterfaceUI.class.getDeclaredMethod("fuzzyItemSlots",
            ECOMachineInterfaceBlockEntity.class);
        method.setAccessible(true);
        return (UIElement) method.invoke(null, host);
    }

    private static void collectSlots(UIElement element, List<ItemSlot> slots) {
        if (element instanceof ItemSlot slot) {
            slots.add(slot);
        }
        for (UIElement child : element.getSafeSortedChildren()) {
            collectSlots(child, slots);
        }
    }
}
