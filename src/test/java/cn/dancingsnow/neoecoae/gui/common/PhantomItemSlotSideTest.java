package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.computation.ComputationInterfaceUI;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.lowdragmc.lowdraglib.LDLib;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.ItemSlot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.ItemStackHandler;
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
        try (var ldlib = mockStatic(LDLib.class)) {
            ldlib.when(LDLib::isClient).thenReturn(client || side.equals("integrated"));
            ldlib.when(LDLib::isRemote).thenReturn(client);
            // Recipe-viewer mods can be present on a dedicated server too.
            ldlib.when(LDLib::isJeiLoaded).thenReturn(true);
            ldlib.when(LDLib::isEmiLoaded).thenReturn(true);
            UIElement grid = assertDoesNotThrow(() -> createGrid(level));
            List<ItemSlot> slots = new ArrayList<>();
            collectSlots(grid, slots);
            assertEquals(63, slots.size());
            for (ItemSlot slot : slots) {
                assertNotNull(slot.getSlot());
                var ingredient=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,7);
                var targets=((com.lowdragmc.lowdraglib.gui.ingredient.IGhostIngredientTarget)slot.widgets.get(0)).getPhantomTargets(ingredient);
                assertEquals(client?1:0,targets.size());
                if(client){targets.get(0).accept(ingredient);assertEquals(7,ingredient.getCount());assertEquals(1,slot.getValue().getCount());}
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
        element.getChildren().forEach(child -> collectSlots(child, slots));
    }
}
