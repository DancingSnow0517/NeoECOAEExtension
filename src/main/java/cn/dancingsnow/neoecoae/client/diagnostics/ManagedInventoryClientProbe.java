package cn.dancingsnow.neoecoae.client.diagnostics;

import appeng.util.inv.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.platform.sync.NEManagedBlockEntity;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.RetainedUIGuiContainer;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.ItemSlot;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import com.lowdragmc.lowdraglib.syncdata.AccessorOp;
import com.lowdragmc.lowdraglib.syncdata.IContentChangeAware;
import com.lowdragmc.lowdraglib.syncdata.TypedPayloadRegistries;
import com.lowdragmc.lowdraglib.syncdata.accessor.ReadonlyAccessor;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in regression for managed inventories, including client prediction and real menu packets. */
final class ManagedInventoryClientProbe {
    private static final Logger LOG = LoggerFactory.getLogger("neoecoae.inventorySmoke");
    private static int specimen;
    private static int stage;
    private static int ticks;
    private static BlockPos position;
    private static volatile boolean prepared;
    private static volatile boolean serverVerified;
    private static volatile Throwable serverFailure;
    private static boolean previousPauseOnLostFocus;

    private ManagedInventoryClientProbe() {}

    static void tick(Minecraft client) {
        if(client.screen instanceof net.minecraft.client.gui.screens.PauseScreen)client.setScreen(null);
        if (serverFailure != null) throw new IllegalStateException("Managed inventory server regression", serverFailure);
        if (++ticks > 400) throw new IllegalStateException("Managed inventory regression timed out at " + specimen + ":" + stage);
        List<Block> blocks = List.of(NEBlocks.STORAGE_SYSTEM_L4.get(), NEBlocks.STORAGE_SYSTEM_L6.get(),
            NEBlocks.STORAGE_SYSTEM_L9.get(), NEBlocks.STORAGE_INTERFACE.get(), NEBlocks.CRAFTING_INTERFACE.get(),
            NEBlocks.COMPUTATION_INTERFACE.get(), NEBlocks.CRAFTING_PATTERN_BUS.get(),
            NEBlocks.INTEGRATED_WORKING_STATION.get(),NEBlocks.COMPUTATION_SYSTEM_L4.get(),NEBlocks.CRAFTING_SYSTEM_L4.get());
        if (specimen == blocks.size()) {
            if (ticks == 20) {
                LOG.info("INVENTORY_SMOKE PASS: {} blocks placed on client/server, persisted and opened", blocks.size());
                client.options.pauseOnLostFocus=previousPauseOnLostFocus;
                client.stop();
            }
            return;
        }
        var block = blocks.get(specimen);
        var blockId = BuiltInRegistries.BLOCK.getKey(block);
        switch (stage) {
            case 0 -> {
                if (specimen == 0) {
                    verifySnapshotRoundTrip();
                    verifyPersistenceBridge(client);
                    client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                    previousPauseOnLostFocus=client.options.pauseOnLostFocus;
                    client.options.pauseOnLostFocus=false;
                }
                position = client.player.blockPosition().offset(2, 0, 0);
                prepared = false;
                serverVerified = false;
                var playerId = client.player.getUUID();
                client.getSingleplayerServer().execute(() -> {
                    var player = client.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                    player.serverLevel().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
                    player.serverLevel().setBlockAndUpdate(position.below(), Blocks.BEDROCK.defaultBlockState());
                    player.getInventory().clearContent();
                    for (int slot : new int[] {1, 4, 8, 9, 22, 35}) {
                        player.getInventory().setItem(slot, playerInventoryMarker(slot));
                    }
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block));
                    player.inventoryMenu.broadcastChanges();
                    prepared = true;
                });
                advance();
            }
            case 1 -> {
                if (!prepared || !client.player.getMainHandItem().is(block.asItem())
                        || !client.level.isEmptyBlock(position) || ticks < 10) return;
                var hit = new BlockHitResult(Vec3.atCenterOf(position.below()).add(0, 0.5, 0),
                    Direction.UP, position.below(), false);
                var result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
                require(result.consumesAction(), "client placement rejected for " + blockId);
                require(client.level.getBlockEntity(position) instanceof NEManagedBlockEntity,
                    "client prediction did not create " + blockId);
                LOG.info("INVENTORY_SMOKE predicted placement: {}", blockId);
                advance();
            }
            case 2 -> {
                if (ticks < 20) return;
                var playerId = client.player.getUUID();
                client.getSingleplayerServer().execute(() -> {
                    try {
                        var player = client.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                        var entity = player.serverLevel().getBlockEntity(position);
                        require(entity instanceof NEManagedBlockEntity, "server placement missing for " + blockId);
                        var managed = (NEManagedBlockEntity) entity;
                        var data = entity.saveWithFullMetadata();
                        int inventories = 0;
                        for (var field : managed.getRootStorage().getPersistedFields()) {
                            if (field.readRaw() instanceof AppEngInternalInventory) {
                                require(data.contains(field.getPersistedKey()), "missing saved inventory for " + blockId);
                                inventories++;
                            }
                        }
                        if(specimen<7)require(inventories > 0, "no managed inventory for " + blockId);
                        LOG.info("INVENTORY_SMOKE server placed/persisted: {}, inventories={}", blockId, inventories);
                        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        player.inventoryMenu.broadcastChanges();
                        serverVerified = true;
                    } catch (Throwable failure) {
                        serverFailure = failure;
                    }
                });
                advance();
            }
            case 3 -> {
                if (!serverVerified || !client.player.getMainHandItem().isEmpty() || ticks < 10) return;
                var hit = new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false);
                var result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
                require(result.consumesAction(), "client activation rejected for " + blockId);
                client.getToasts().clear();
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(client.getWindow().getWindow(),3,client.getWindow().getScreenHeight()-3);
                advance();
            }
            case 4 -> {
                if (!(client.screen instanceof ModularUIGuiContainer)) return;
                if(ticks<20){
                    client.getToasts().clear();
                    movePointerOutside(client);
                    return;
                }
                require(client.screen instanceof cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.RetainedUIGuiContainer,
                    "retained screen adapter missing for " + blockId);
                var screen=(cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.RetainedUIGuiContainer)client.screen;
                var root=(cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement)screen.modularUI.mainGroup;
                require(screen.toScreen(root.getPositionY())>=0
                    &&screen.toScreen(root.getPositionY()+root.getSizeHeight())<=client.getWindow().getGuiScaledHeight(),
                    "menu extends beyond the window for " + blockId);
                if(block==NEBlocks.COMPUTATION_INTERFACE.get()){
                    var grid=root.getElementsById("computation-fuzzy-grid").get(0);
                    var last=grid.getChildren().get(6).getChildren().get(8);
                    double x=screen.toScreen(last.getPositionX()+9),y=screen.toScreen(last.getPositionY()+9);
                    screen.mouseMoved(x,y);
                    require(root.getModularUI().getLastHoveredElement()==last,"scaled last-row hit test failed");
                    screen.mouseMoved(0,0);
                    LOG.info("INVENTORY_SMOKE fitted screen: scale={}, last filter slot reachable=true",screen.getRenderScale());
                }
                verifyPlayerInventory(client, root);
                for (int slot : new int[] {1, 4, 8, 9, 22, 35}) {
                    require(ItemStack.matches(playerInventoryMarker(slot), client.player.getInventory().getItem(slot)),
                        "player inventory marker missing at " + slot + " for " + blockId);
                }
                LOG.info("PLAYER_INVENTORY_SMOKE bound: {}, 36 native player slots, main inventory/hotbar counts and NBT", blockId);
                LOG.info("INVENTORY_SMOKE menu displayed: {}", blockId);
                Screenshot.grab(client.gameDirectory, "neoecoae-inventory-" + blockId.getPath() + ".png",
                    client.getMainRenderTarget(), message -> LOG.info("INVENTORY_SMOKE screenshot: {}", message.getString()));
                if (specimen < 3 || specimen >= 8) {
                    clickInventorySlot(screen, 22);
                    advance();
                } else {
                    client.player.closeContainer();
                    stage = 9;
                    ticks = 0;
                }
            }
            case 5 -> {
                if (ticks < 10) return;
                require(client.player.getInventory().getItem(22).isEmpty(), "screen pickup left the source occupied");
                require(ItemStack.matches(playerInventoryMarker(22), client.player.containerMenu.getCarried()),
                    "screen pickup lost the count or NBT");
                serverVerified = false;
                var playerId = client.player.getUUID();
                client.getSingleplayerServer().execute(() -> {
                    try {
                        var player = client.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                        require(player.getInventory().getItem(22).isEmpty(), "pickup packet did not reach the server");
                        require(ItemStack.matches(playerInventoryMarker(22), player.containerMenu.getCarried()),
                            "server pickup lost the count or NBT");
                        serverVerified = true;
                    } catch (Throwable failure) {
                        serverFailure = failure;
                    }
                });
                advance();
            }
            case 6 -> {
                if (!serverVerified || ticks < 5) return;
                clickInventorySlot((RetainedUIGuiContainer) client.screen, 23);
                advance();
            }
            case 7 -> {
                if (ticks < 10) return;
                require(client.player.containerMenu.getCarried().isEmpty(), "screen placement retained the carried stack");
                require(ItemStack.matches(playerInventoryMarker(22), client.player.getInventory().getItem(23)),
                    "screen placement lost the stack");
                serverVerified = false;
                var playerId = client.player.getUUID();
                client.getSingleplayerServer().execute(() -> {
                    try {
                        var player = client.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                        require(player.containerMenu.getCarried().isEmpty(), "server placement retained the carried stack");
                        require(ItemStack.matches(playerInventoryMarker(22), player.getInventory().getItem(23)),
                            "placement packet did not reach the server");
                        var updated = playerInventoryMarker(22);
                        updated.setCount(12);
                        updated.getOrCreateTag().putBoolean("server_sync_smoke", true);
                        player.getInventory().setItem(23, updated);
                        player.containerMenu.broadcastChanges();
                        serverVerified = true;
                    } catch (Throwable failure) {
                        serverFailure = failure;
                    }
                });
                advance();
            }
            case 8 -> {
                if (!serverVerified || ticks < 10) return;
                var updated = client.player.getInventory().getItem(23);
                require(updated.getCount() == 12 && updated.hasTag() && updated.getTag().getBoolean("server_sync_smoke"),
                    "server inventory update was not synchronized to the client");
                var screen = (RetainedUIGuiContainer) client.screen;
                verifyPlayerInventory(client, (UIElement) screen.modularUI.mainGroup);
                LOG.info("PLAYER_INVENTORY_SMOKE interaction PASS: {}, screen pickup/placement, server packets, incremental count/NBT sync", blockId);
                client.player.closeContainer();
                advance();
            }
            case 9 -> {
                if (client.screen != null || ticks < 10) return;
                specimen++;
                stage = 0;
                ticks = 0;
            }
            default -> throw new IllegalStateException("Unknown inventory regression stage " + stage);
        }
    }

    private static ItemStack playerInventoryMarker(int slot) {
        var item = switch (slot) {
            case 1 -> Items.DIAMOND;
            case 4 -> Items.EMERALD;
            case 8 -> Items.REDSTONE;
            case 9 -> Items.GOLD_INGOT;
            case 22 -> Items.IRON_INGOT;
            case 35 -> Items.LAPIS_LAZULI;
            default -> throw new IllegalArgumentException("Unknown player inventory marker slot " + slot);
        };
        var stack = new ItemStack(item, slot + 1);
        stack.getOrCreateTag().putInt("player_slot_smoke", slot);
        return stack;
    }

    private static void verifyPlayerInventory(Minecraft client, UIElement root) {
        var inventory = client.player.getInventory();
        var slots = client.player.containerMenu.slots.stream().filter(slot -> slot.container == inventory).toList();
        require(slots.size() == 36 && slots.stream().map(slot -> slot.getContainerSlot()).distinct().count() == 36,
            "menu does not expose 36 distinct native player slots");
        var elements = root.getModularUI().getElementsByType(InventorySlots.class);
        require(elements.size() == 1, "menu does not contain exactly one player inventory element");
        elements.get(0).apply(element -> {
            var slot = element.getSlot();
            require(slot.container == inventory, "display slot is not bound to the menu player's inventory");
            require(ItemStack.matches(inventory.getItem(slot.getContainerSlot()), element.getValue()),
                "display slot differs from the actual player stack");
        });
    }

    private static void clickInventorySlot(RetainedUIGuiContainer screen, int index) {
        var root = (UIElement) screen.modularUI.mainGroup;
        var inventory = root.getModularUI().getElementsByType(InventorySlots.class).get(0);
        ItemSlot slot = index < 9 ? inventory.hotbar.slots[index] : inventory.rows[index / 9 - 1].slots[index % 9];
        double x = screen.toScreen(slot.getPositionX() + 9), y = screen.toScreen(slot.getPositionY() + 9);
        screen.mouseMoved(x, y);
        require(screen.mouseClicked(x, y, 0), "player slot did not accept screen mouse input");
        screen.mouseReleased(x, y, 0);
    }

    private static void verifySnapshotRoundTrip() {
        var source = new AppEngInternalInventory(3);
        var first = new ItemStack(Items.STONE, 64);
        first.getOrCreateTag().putString("inventory_smoke", "NBT content survives the LDLib 1 bridge");
        source.setItemDirect(0, first);
        source.setItemDirect(2, new ItemStack(Items.DIAMOND, 7));
        var accessor = (ReadonlyAccessor) TypedPayloadRegistries.findByClass(AppEngInternalInventory.class);
        var snapshot = accessor.readFromReadonlyField(AccessorOp.PERSISTED, source);
        var restored = new AppEngInternalInventory(3);
        restored.setItemDirect(1, new ItemStack(Items.DIRT, 13));
        var dirtyCalls = new AtomicInteger();
        ((IContentChangeAware) restored).setOnContentsChanged(dirtyCalls::incrementAndGet);
        accessor.writeToReadonlyField(AccessorOp.PERSISTED, restored, snapshot);
        for (int slot = 0; slot < 3; slot++) {
            require(ItemStack.matches(source.getStackInSlot(slot), restored.getStackInSlot(slot)),
                "snapshot lost contents at slot " + slot);
        }
        require(dirtyCalls.get() == 0, "received snapshot marked itself dirty");
        restored.setItemDirect(1, new ItemStack(Items.DIRT));
        require(dirtyCalls.get() == 1, "local edit did not notify the dirty listener");
        LOG.info("INVENTORY_SMOKE snapshot PASS: counts, tags, sparse clearing, no receive echo, dirty listener");
    }

    private static void verifyPersistenceBridge(Minecraft client){
        var calls=new int[2];
        var fixture=new cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic(){
            @Override protected void readNativePersistentData(net.minecraft.nbt.CompoundTag data){
                require(data.getInt("native_persistence")==73,"registry-aware hook lost native persistence data");calls[0]++;
            }
            @Override protected void writeNativePersistentData(net.minecraft.nbt.CompoundTag data){
                data.putInt("native_persistence",73);calls[1]++;
            }
        };
        var data=new net.minecraft.nbt.CompoundTag();
        fixture.writeToNBT(data,client.level.registryAccess());fixture.readFromNBT(data,client.level.registryAccess());
        require(calls[0]==1&&calls[1]==1,"registry-aware persistence did not delegate exactly once");
        LOG.info("INVENTORY_SMOKE persistence bridge PASS: live addon target loaded, native save/load delegated once");
    }

    private static void advance() {
        stage++;
        ticks = 0;
    }

    private static void movePointerOutside(Minecraft client){
        double x=3,y=client.getWindow().getScreenHeight()-3;
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(client.getWindow().getWindow(),x,y);
        try{
            // An unfocused GLFW window may not dispatch its native callback; update the same game input path.
            var callback=net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove",long.class,double.class,double.class);
            callback.setAccessible(true);callback.invoke(client.mouseHandler,client.getWindow().getWindow(),x,y);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot move the development probe pointer",failure);}
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
