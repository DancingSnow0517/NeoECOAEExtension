package cn.dancingsnow.neoecoae.items;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.recipes.game.StorageCellDisassemblyRecipe;
import appeng.util.InteractionUtil;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.integration.ae2omnicells.item.ECOUniversalStorageCellItem;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.wintercogs.ae2omnicells.common.me.AEUniversalCellData;
import com.wintercogs.ae2omnicells.common.me.AEUniversalCellInventory;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.util.thread.EffectiveSide;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StorageCellDisassemblyTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @ParameterizedTest
    @CsvSource({
        "false,false,client", "false,true,client", "true,false,client", "true,true,client",
        "false,false,occupied", "false,true,occupied", "true,false,occupied", "true,true,occupied",
        "false,false,unavailable", "false,true,unavailable", "true,false,unavailable", "true,true,unavailable",
        "false,false,empty", "false,true,empty", "true,false,empty", "true,true,empty"
    })
    void onlyServerConfirmedEmptyCellsCanBeDisassembled(boolean omni, boolean onBlock, String state) {
        Item item = omni ? mock(ECOUniversalStorageCellItem.class, CALLS_REAL_METHODS)
            : mock(ECOStorageCellItem.class, CALLS_REAL_METHODS);
        ItemStack stack = mock(ItemStack.class);
        when(stack.getItem()).thenReturn(item);
        when(stack.getCount()).thenReturn(1);
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(state.equals("client"));
        Player player = mock(Player.class);
        Inventory inventory = mock(Inventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getSelected()).thenReturn(stack);
        when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenAnswer(invocation -> inventory.getSelected());
        doAnswer(invocation -> {
            when(inventory.getSelected()).thenReturn(invocation.getArgument(1));
            return null;
        }).when(inventory).setItem(eq(0), any(ItemStack.class));

        IUpgradeInventory upgrades = mock(IUpgradeInventory.class);
        if (omni) {
            doReturn(upgrades).when((ECOUniversalStorageCellItem) item).getUpgrades(stack);
        } else {
            doReturn(upgrades).when((ECOStorageCellItem) item).getUpgrades(stack);
        }
        KeyCounter contents = new KeyCounter();
        if (state.equals("occupied")) {
            contents.add(AEItemKey.of(Items.DIAMOND), 1);
        }
        ECOStorageCell basicCell = mock(ECOStorageCell.class);
        when(basicCell.getAvailableStacks()).thenReturn(contents);
        ItemStack housing = new ItemStack(Items.IRON_INGOT);

        try (var interaction = mockStatic(InteractionUtil.class);
             var membership = mockStatic(ECOInfiniteStorageMember.class);
             var recipes = mockStatic(StorageCellDisassemblyRecipe.class);
             var basic = mockStatic(ECOStorageCellItem.class);
             var side = mockStatic(EffectiveSide.class);
             var data = mockStatic(AEUniversalCellData.class);
             var delegates = mockConstruction(AEUniversalCellInventory.class, (delegate, context) ->
                 doAnswer(invocation -> {
                     ((KeyCounter) invocation.getArgument(0)).addAll(contents);
                     return null;
                 }).when(delegate).getAvailableStacks(any(KeyCounter.class)))) {
            interaction.when(() -> InteractionUtil.isInAlternateUseMode(player)).thenReturn(true);
            recipes.when(() -> StorageCellDisassemblyRecipe.getDisassemblyResult(level, item))
                .thenReturn(List.of(housing));
            basic.when(() -> ECOStorageCellItem.getCellInventory(stack))
                .thenReturn(state.equals("unavailable") ? null : basicCell);
            side.when(EffectiveSide::get).thenReturn(state.equals("client") ? LogicalSide.CLIENT : LogicalSide.SERVER);
            data.when(() -> AEUniversalCellData.computeIfAbsentCellDataForItemStack(stack))
                .thenReturn(state.equals("unavailable") ? null : mock(AEUniversalCellData.class));

            if (onBlock) {
                UseOnContext context = mock(UseOnContext.class);
                when(context.getPlayer()).thenReturn(player);
                when(context.getLevel()).thenReturn(level);
                item.onItemUseFirst(stack, context);
            } else {
                var result = item.use(level, player, InteractionHand.MAIN_HAND);
                assertSame(inventory.getSelected(), result.getObject(), "Return the current held stack after disassembly");
            }

            if (state.equals("empty")) {
                verify(inventory).setItem(0, ItemStack.EMPTY);
                verify(inventory).placeItemBackInInventory(argThat(result -> result.is(Items.IRON_INGOT)));
                verify(upgrades).forEach(any());
            } else {
                verify(inventory, never()).setItem(anyInt(), any());
                verify(inventory, never()).placeItemBackInInventory(any());
                verify(upgrades, never()).forEach(any());
            }
            if (state.equals("client")) {
                recipes.verifyNoInteractions();
                data.verifyNoInteractions();
            }
        }
    }
}
