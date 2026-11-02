package cn.dancingsnow.neoecoae.items;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.recipes.game.StorageCellDisassemblyRecipe;
import appeng.util.InteractionUtil;
import cn.dancingsnow.neoecoae.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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
    // 26.1 port: the ae2omnicells (ECOUniversalStorageCellItem) rows were removed; that integration is excluded from main.
    @CsvSource({
        "false,client", "true,client",
        "false,occupied", "true,occupied",
        "false,unavailable", "true,unavailable",
        "false,empty", "true,empty",
        "false,queued", "true,queued"
    })
    void onlyServerConfirmedEmptyCellsCanBeDisassembled(boolean onBlock, String state) {
        Item item = mock(ECOStorageCellItem.class, CALLS_REAL_METHODS);
        ItemStack stack = mock(ItemStack.class);
        when(stack.getItem()).thenReturn(item);
        when(stack.getCount()).thenReturn(1);
        // 26.1: the server path is selected by `level instanceof ServerLevel`.
        Level level = state.equals("client") ? mock(Level.class) : mock(ServerLevel.class);
        when(level.isClientSide()).thenReturn(state.equals("client"));
        Player player = mock(Player.class);
        Inventory inventory = mock(Inventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getSelectedItem()).thenReturn(stack);
        when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenAnswer(invocation -> inventory.getSelectedItem());
        doAnswer(invocation -> {
            ItemStack previous = inventory.getSelectedItem();
            when(inventory.getSelectedItem()).thenReturn(invocation.getArgument(0));
            return previous;
        }).when(inventory).setSelectedItem(any(ItemStack.class));

        IUpgradeInventory upgrades = mock(IUpgradeInventory.class);
        doReturn(upgrades).when((ECOStorageCellItem) item).getUpgrades(stack);
        KeyCounter contents = new KeyCounter();
        if (state.equals("occupied")) {
            contents.add(AEItemKey.of(Items.DIAMOND), 1);
        }
        ECOStorageCell basicCell = state.equals("queued")
            ? mock(cn.dancingsnow.neoecoae.storage.bulk.ECOBulkStorageCell.class) : mock(ECOStorageCell.class);
        when(basicCell.getAvailableStacks()).thenReturn(contents);
        ItemStack housing = new ItemStack(Items.IRON_INGOT);

        try (var interaction = mockStatic(InteractionUtil.class);
             var membership = mockStatic(ECOInfiniteStorageMember.class);
             var recipes = mockStatic(StorageCellDisassemblyRecipe.class);
             var basic = mockStatic(ECOStorageCellItem.class)) {
            interaction.when(() -> InteractionUtil.isInAlternateUseMode(player)).thenReturn(true);
            recipes.when(() -> StorageCellDisassemblyRecipe.getDisassemblyResult(any(ServerLevel.class), eq(item)))
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
                assertSame(inventory.getSelectedItem(), ((InteractionResult.Success) result).heldItemTransformedTo(),
                    "Return the current held stack after disassembly");
            }

            if (state.equals("empty")) {
                verify(inventory).setSelectedItem(ItemStack.EMPTY);
                verify(inventory).placeItemBackInInventory(argThat(result -> result.is(Items.IRON_INGOT)));
                verify(upgrades).forEach(any());
            } else {
                verify(inventory, never()).setSelectedItem(any());
                verify(inventory, never()).placeItemBackInInventory(any());
                verify(upgrades, never()).forEach(any());
            }
            if (state.equals("client")) {
                recipes.verifyNoInteractions();
            }
        }
    }
}
