package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.computation.ComputationInterfaceUI;
import cn.dancingsnow.neoecoae.gui.storage.StorageMegaPanelUI;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
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
            Class.forName(StorageMegaPanelUI.class.getName());
            Class.forName(ItemSlot.class.getName());
            AccessorRegistries.init();
        }
    }

    @ParameterizedTest
    @CsvSource({
        "storage, dedicated", "computation, dedicated",
        "storage, integrated", "computation, integrated",
        "storage, client", "computation, client",
        "storage, no_level", "computation, no_level"
    })
    void gridsOnlyRegisterPhantomHandlersOnTheLogicalClient(String gridType, String side) throws Exception {
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

            UIElement grid = assertDoesNotThrow(() -> createGrid(gridType, level));
            List<ItemSlot> slots = new ArrayList<>();
            collectSlots(grid, slots);
            assertEquals(gridType.equals("storage") ? 25 : 63, slots.size());
            for (ItemSlot slot : slots) {
                assertNotNull(slot.getSlot());
                if (client) {
                    jei.verify(() -> ItemSlot.JEISupport.ghostIngredient(slot));
                    emi.verify(() -> ItemSlot.EMISupport.renderDragHandler(slot));
                    emi.verify(() -> ItemSlot.EMISupport.dropStackHandler(slot));
                }
            }
            if (!client) {
                jei.verifyNoInteractions();
                emi.verifyNoInteractions();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static UIElement createGrid(String gridType, Level level) throws Exception {
        if (gridType.equals("storage")) {
            var host = mock(ECOStorageSystemBlockEntity.class);
            when(host.getLevel()).thenReturn(level);
            var method = StorageMegaPanelUI.class.getDeclaredMethod("filterGrid",
                ECOStorageSystemBlockEntity.class, net.neoforged.neoforge.items.IItemHandlerModifiable.class);
            method.setAccessible(true);
            return (UIElement) method.invoke(null, host, new ItemStackHandler(25));
        }
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
        element.getChildren().forEach(child -> collectSlots(child, slots));
    }
}
