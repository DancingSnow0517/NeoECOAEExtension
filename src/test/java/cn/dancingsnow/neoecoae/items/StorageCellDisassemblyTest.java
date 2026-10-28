package cn.dancingsnow.neoecoae.items;

import appeng.api.upgrades.IUpgradeInventory;
import cn.dancingsnow.neoecoae.recipe.StorageCellDisassemblyRecipe;
import appeng.util.InteractionUtil;
import cn.dancingsnow.neoecoae.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.storage.persistence.ECOCellSavedData;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
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
        "false,client", "true,client", "false,occupied", "true,occupied",
        "false,unavailable", "true,unavailable", "false,read_only", "true,read_only",
        "false,empty", "true,empty", "false,queued", "true,queued"
    })
    void onlyServerConfirmedEmptyCellsCanBeDisassembled(boolean onBlock, String state) {
        ECOStorageCellItem item = mock(ECOStorageCellItem.class, CALLS_REAL_METHODS);
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
        doReturn(upgrades).when(item).getUpgrades(stack);
        // A queued bulk disk has no stored types but still owns conversion outputs.
        ECOStorageCell basicCell = state.equals("queued")
            ? mock(cn.dancingsnow.neoecoae.storage.bulk.ECOBulkStorageCell.class) : mock(ECOStorageCell.class);
        when(basicCell.getStoredItemTypes()).thenReturn(state.equals("occupied") ? 1L : 0L);
        var data = mock(ECOCellSavedData.class);
        when(data.canMutate()).thenReturn(!state.equals("read_only"));
        when(basicCell.storageData()).thenReturn(data);
        ItemStack housing = new ItemStack(Items.IRON_INGOT);

        try (var interaction = mockStatic(InteractionUtil.class);
             var membership = mockStatic(ECOInfiniteStorageMember.class);
             var recipes = mockStatic(StorageCellDisassemblyRecipe.class);
             var basic = mockStatic(ECOStorageCellItem.class)) {
            interaction.when(() -> InteractionUtil.isInAlternateUseMode(player)).thenReturn(true);
            recipes.when(() -> StorageCellDisassemblyRecipe.getDisassemblyResult(level, item))
                .thenReturn(List.of(housing));
            basic.when(() -> ECOStorageCellItem.getCellInventory(stack))
                .thenReturn(state.equals("unavailable") ? null : basicCell);

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
                verifyNoInteractions(data);
            }
        }
    }
}
