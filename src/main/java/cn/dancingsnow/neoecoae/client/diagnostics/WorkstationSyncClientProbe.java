package cn.dancingsnow.neoecoae.client.diagnostics;

import appeng.core.definitions.AEItems;
import appeng.menu.SlotSemantics;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.client.gui.workstation.LargeIntegratedWorkingStationPatternProviderScreen;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.RetainedUIGuiContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in isolated-world acceptance using real Forge menus, pointer routing and server packets. */
final class WorkstationSyncClientProbe {
    private static final Logger LOG = LoggerFactory.getLogger("neoecoae.upstreamSyncSmoke");
    private static int stage;
    private static int ticks;
    private static BlockPos controller;
    private static BlockPos communication;
    private static volatile boolean serverVerified;
    private static volatile Throwable failure;
    private static int originalTaskY;
    private static int hoverWaitTicks;

    private WorkstationSyncClientProbe() {}

    static void tick(Minecraft client) {
        if (failure != null) throw new IllegalStateException("Upstream sync client acceptance failed", failure);
        if (++ticks > 600) throw new IllegalStateException("Upstream sync client timeout at stage " + stage);
        client.options.pauseOnLostFocus = false;
        if (client.screen instanceof net.minecraft.client.gui.screens.PauseScreen) client.setScreen(null);
        switch (stage) {
            case 0 -> {
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1280, 720);
                client.options.guiScale().set(2);
                client.resizeDisplay();
                controller = client.player.blockPosition().offset(2, 0, 0);
                communication = controller.offset(0, -1, 1);
                onServer(client, player -> {
                    var wrapped = new appeng.api.storage.SupplierStorage(() -> null);
                    require((Object) wrapped instanceof cn.dancingsnow.neoecoae.mixins.ae2.accessor.SupplierStorageAccessor accessor
                        && accessor.neoecoae$getDelegate() != null, "AE2 SupplierStorage invoker was not applied");
                    WorkstationModelClientProbe.build(player.serverLevel(), controller, Direction.NORTH);
                    seedSavedTasks((ECOLargeIntegratedWorkingStationBlockEntity) player.serverLevel().getBlockEntity(controller));
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    player.getInventory().setItem(9, AEItems.CAPACITY_CARD.stack(6));
                    player.inventoryMenu.broadcastChanges();
                });
                advance(1);
            }
            case 1 -> {
                if (!serverVerified || ticks < 40 || client.level.getBlockEntity(communication) == null) return;
                open(client, communication);
                advance(2);
            }
            case 2 -> {
                if (!(client.screen instanceof LargeIntegratedWorkingStationPatternProviderScreen screen) || ticks < 25) return;
                var menu = screen.getMenu();
                require(menu.getPatternSlotCount() == 36, "Initial unlocked pattern inventory differs");
                client.getToasts().clear();
                movePointer(client, 1, 1);
                capture(client, "neoecoae-upstream-sync-reference.png");
                int source = menu.slots.stream().filter(slot -> slot.container == menu.getPlayerInventory()
                    && slot.getContainerSlot() == 9).findFirst().orElseThrow().index;
                client.gameMode.handleInventoryMouseClick(menu.containerId, source, 0, ClickType.QUICK_MOVE, client.player);
                advance(3);
            }
            case 3 -> {
                if (ticks < 20) return;
                onServer(client, player -> {
                    var bus = (ECOLargeIntegratedWorkingStationInterfaceBlockEntity)
                        player.serverLevel().getBlockEntity(communication);
                    require(bus.getLogic().getPatternInv().size() == 72, "Native Shift-click did not install four capacity cards");
                    require(player.getInventory().getItem(9).getCount() == 2, "Native Shift-click lost its remainder");
                    require(bus.getLogic().getReturnInv().isEmpty(), "Capacity cards entered the return buffer");
                });
                advance(4);
            }
            case 4 -> {
                if (!serverVerified || ticks < 20) return;
                var screen = (LargeIntegratedWorkingStationPatternProviderScreen) client.screen;
                require(screen.getMenu().getPatternSlotCount() == 72, "Expanded inventory did not reach the client");
                var slots = screen.getMenu().getSlots(SlotSemantics.ENCODED_PATTERN);
                require(slots.stream().filter(slot -> slot.isActive()).count() == 63, "Viewport must contain seven rows");
                int left = screen.getGuiLeft(), top = screen.getGuiTop();
                int scrollbarX = left + LargeIntegratedWorkingStationPatternProviderScreen.SCROLLBAR_LEFT;
                require(screen.mouseClicked(scrollbarX, top + 24, 0), "Scrollbar left edge rejected the press");
                require(screen.mouseDragged(scrollbarX, top + 145, 0, 0, 121), "Scrollbar rejected dragging");
                screen.mouseReleased(scrollbarX, top + 145, 0);
                for (var slot : screen.getMenu().getSlots(SlotSemantics.UPGRADE)) {
                    require(slot.x >= 4 && slot.x + 16 <= LargeIntegratedWorkingStationPatternProviderScreen.PANEL_WIDTH - 4,
                        "Upgrade slots must stay inside the shared main panel: " + slot.x + "," + slot.y);
                }
                require(!slots.get(0).isActive() && slots.get(71).isActive(), "Scrollbar did not reach the eighth row");
                LOG.info("UPSTREAM_SYNC_CLIENT pattern PASS: native Shift-click, 4 cards, remainder 2, seven rows, left-edge drag");
                advance(5);
            }
            case 5 -> {
                var screen = (LargeIntegratedWorkingStationPatternProviderScreen) client.screen;
                var buttons = screen.children().stream().filter(appeng.client.gui.widgets.IconButton.class::isInstance)
                    .map(appeng.client.gui.widgets.IconButton.class::cast)
                    .filter(button -> button.getX() < screen.getGuiLeft() + LargeIntegratedWorkingStationPatternProviderScreen.CONTENT_OFFSET_X).toList();
                require(!buttons.isEmpty(), "Native toolbar buttons are missing");
                require(buttons.stream().allMatch(button -> button.getX() >= screen.getGuiLeft() + 4),
                    "Toolbar buttons must stay inside the shared main panel");
                var sample = buttons.get(Math.min(1, buttons.size() - 1));
                if (ticks == 5) movePointer(client, 1, 1);
                if (ticks == 10) {
                    client.getToasts().clear();
                    capture(client, "neoecoae-upstream-sync-seven-rows.png");
                }
                if (ticks >= 11 && ticks <= 16) movePointer(client, sample.getX() + 8, sample.getY() + 8);
                if (ticks == 16) {
                    // World loading can run several client ticks before the next rendered GUI frame.
                    if (!sample.isHoveredOrFocused()) {
                        require(++hoverWaitTicks < 200, "Toolbar hover fixture did not reach native pointer routing");
                        ticks = 15;
                        return;
                    }
                    capture(client, "neoecoae-upstream-sync-buttons-hover.png");
                }
                if (ticks == 17) { movePointer(client, 1, 1); sample.setFocused(true); }
                if (ticks == 22) capture(client, "neoecoae-upstream-sync-buttons-focus.png");
                if (ticks == 23) { sample.setFocused(false); sample.active = false; }
                if (ticks == 28) {
                    require(!sample.active, "Disabled toolbar fixture was reset before capture");
                    capture(client, "neoecoae-upstream-sync-buttons-disabled.png");
                    sample.active = true;
                    var priority = screen.children().stream().filter(appeng.client.gui.widgets.TabButton.class::isInstance)
                        .map(appeng.client.gui.widgets.TabButton.class::cast).filter(button -> button.visible)
                        .findFirst().orElseThrow();
                    require(priority.getX() == screen.getGuiLeft() + 184 && priority.getY() == screen.getGuiTop() - 5,
                        "Priority tab must retain the raised header position from the reference");
                    require(screen.mouseClicked(priority.getX() + 10, priority.getY() + 10, 0),
                        "Raised priority button rejected the native pointer press");
                    screen.mouseReleased(priority.getX() + 10, priority.getY() + 10, 0);
                    advance(15);
                }
            }
            case 15 -> {
                if (!(client.screen instanceof appeng.client.gui.implementations.PriorityScreen) || ticks < 10) return;
                LOG.info("UPSTREAM_SYNC_CLIENT priority PASS: raised tab opens native settings");
                client.player.closeContainer();
                advance(14);
            }
            case 14 -> {
                if (client.screen != null || ticks < 10) return;
                open(client, controller);
                advance(6);
            }
            case 6 -> {
                if (!(client.screen instanceof RetainedUIGuiContainer screen) || ticks < 20) return;
                var root = screen.modularUI.mainGroup;
                require(root instanceof cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement, "Missing retained root");
                var element = (cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement) root;
                var overclock = element.selectId("large-workstation-overclock").findFirst().orElseThrow();
                double x = screen.toScreen(overclock.getPositionX() + 7), y = screen.toScreen(overclock.getPositionY() + 12);
                require(screen.mouseClicked(x, y, 1), "Preheat rejected right press");
                screen.mouseReleased(x, y, 1);
                advance(7);
            }
            case 7 -> {
                if (ticks < 20) return;
                onServer(client, player -> {
                    var host = (ECOLargeIntegratedWorkingStationBlockEntity) player.serverLevel().getBlockEntity(controller);
                    require(host.isOverclocked() && !host.isActiveCooling(), "Right-click did not preserve the preheat controls");
                    require(host.getPreheatText().getContents() instanceof TranslatableContents text
                        && text.getKey().endsWith(".on"), "Right-click did not enable server preheat");
                });
                advance(8);
            }
            case 8 -> {
                if (!serverVerified || ticks < 10) return;
                capture(client, "neoecoae-upstream-sync-preheat.png");
                var screen = (RetainedUIGuiContainer) client.screen;
                var root = (cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement) screen.modularUI.mainGroup;
                var overclock = root.selectId("large-workstation-overclock").findFirst().orElseThrow();
                double x = screen.toScreen(overclock.getPositionX() + 7), y = screen.toScreen(overclock.getPositionY() + 12);
                require(screen.mouseClicked(x, y, 0), "Overclock rejected left press");
                screen.mouseReleased(x, y, 0);
                advance(9);
            }
            case 9 -> {
                if (ticks < 20) return;
                onServer(client, player -> {
                    var host = (ECOLargeIntegratedWorkingStationBlockEntity) player.serverLevel().getBlockEntity(controller);
                    require(!host.isOverclocked(), "Left-click did not disable server overclock");
                    require(host.getPreheatText().getContents() instanceof TranslatableContents text
                        && text.getKey().endsWith(".off"), "Disabling overclock did not stop preheat");
                });
                advance(10);
            }
            case 10 -> {
                if (!serverVerified || ticks < 20) return;
                var screen = (RetainedUIGuiContainer) client.screen;
                var root = (cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement) screen.modularUI.mainGroup;
                var row = root.selectId("large-workstation-task-0").findFirst().orElseThrow();
                var viewport = root.selectId("large-workstation-task-viewport").findFirst().orElseThrow();
                originalTaskY = row.getPositionY();
                require(screen.mouseScrolled(screen.toScreen(viewport.getPositionX() + 10),
                    screen.toScreen(viewport.getPositionY() + 15), -0.5), "Task viewport rejected a fractional wheel gesture");
                advance(11);
            }
            case 11 -> {
                if (ticks < 4) return;
                var screen = (RetainedUIGuiContainer) client.screen;
                var root = (cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement) screen.modularUI.mainGroup;
                var row = root.selectId("large-workstation-task-0").findFirst().orElseThrow();
                require(row.getPositionY() < originalTaskY && row.getPositionY() > originalTaskY - 26,
                    "Task queue failed to stop between whole rows");
                var track = root.selectId("large-workstation-scrollbar").findFirst().orElseThrow();
                double x = screen.toScreen(track.getPositionX() + 4);
                double startY = screen.toScreen(track.getPositionY() + 5), endY = screen.toScreen(track.getPositionY() + 65);
                require(screen.mouseClicked(x, startY, 0), "Task scrollbar rejected the press");
                require(screen.mouseDragged(x, endY, 0, 0, endY - startY), "Task scrollbar rejected dragging");
                screen.mouseReleased(x, endY, 0);
                advance(12);
            }
            case 12 -> {
                if (ticks < 30) return;
                var screen = (RetainedUIGuiContainer) client.screen;
                var root = (cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement) screen.modularUI.mainGroup;
                var viewport = root.selectId("large-workstation-task-viewport").findFirst().orElseThrow();
                var hit = root.hitTest(viewport.getPositionX() + 10, viewport.getPositionY() + 14);
                require(hit != null && hit.getId().startsWith("large-workstation-task-"), "Scrolled task hitboxes lost their cards");
                var outside = root.hitTest(viewport.getPositionX() + 10, viewport.getPositionY() - 1);
                require(outside == null || !outside.getId().startsWith("large-workstation-task-"), "Task hitboxes escaped the clipped viewport");
                onServer(client, player -> {
                    var host = (ECOLargeIntegratedWorkingStationBlockEntity) player.serverLevel().getBlockEntity(controller);
                    var batches = host.saveWithFullMetadata().getList("pendingBatches", net.minecraft.nbt.Tag.TAG_COMPOUND);
                    require(batches.size() == 12 && batches.getCompound(0).getLong("progressRemainder") == 123,
                        "Viewing and scrolling changed queued work or paid remainder");
                });
                advance(13);
            }
            case 13 -> {
                if (!serverVerified || ticks < 10) return;
                capture(client, "neoecoae-upstream-sync-task-scroll.png");
                LOG.info("UPSTREAM_SYNC_CLIENT PASS: seven rows, native Shift-click, pattern scrollbar, preheat controls, continuous task scroll, clipped hitboxes and retained paid work");
                client.player.closeContainer();
                client.stop();
            }
            default -> throw new IllegalStateException("Unknown upstream sync stage " + stage);
        }
    }

    private static void open(Minecraft client, BlockPos position) {
        var result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false));
        require(result.consumesAction(), "Native workstation activation was rejected");
    }

    private static void onServer(Minecraft client, java.util.function.Consumer<ServerPlayer> action) {
        serverVerified = false;
        var id = client.player.getUUID();
        client.getSingleplayerServer().execute(() -> {
            try {
                action.accept(client.getSingleplayerServer().getPlayerList().getPlayer(id));
                serverVerified = true;
            } catch (Throwable error) { failure = error; }
        });
    }

    private static void capture(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(),
            message -> LOG.info("UPSTREAM_SYNC_CLIENT screenshot: {}", message.getString()));
    }

    private static void movePointer(Minecraft client, double x, double y) {
        double pixelX = x * client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth();
        double pixelY = y * client.getWindow().getScreenHeight() / client.getWindow().getGuiScaledHeight();
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(client.getWindow().getWindow(),
            pixelX, pixelY);
        // GLFW does not deliver moves to an unfocused development window; feed its native pixel state too.
        try {
            var xpos = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            var ypos = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            xpos.setAccessible(true); ypos.setAccessible(true);
            xpos.setDouble(client.mouseHandler, pixelX); ypos.setDouble(client.mouseHandler, pixelY);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot position native pointer fixture", error); }
        if (client.screen != null) client.screen.mouseMoved(x, y);
    }

    /** Queue fixtures use the production persisted-batch reader; this is UI acceptance, not live recipe execution. */
    @SuppressWarnings("unchecked")
    private static void seedSavedTasks(ECOLargeIntegratedWorkingStationBlockEntity host) {
        try {
            var batchType = Class.forName(ECOLargeIntegratedWorkingStationBlockEntity.class.getName() + "$PendingBatch");
            var load = batchType.getDeclaredMethod("load", net.minecraft.nbt.CompoundTag.class, long.class);
            load.setAccessible(true);
            var field = ECOLargeIntegratedWorkingStationBlockEntity.class.getDeclaredField("pendingBatches");
            field.setAccessible(true);
            var queue = (java.util.Deque<Object>) field.get(host);
            for (int index = 0; index < 12; index++) {
                var tag = new net.minecraft.nbt.CompoundTag();
                tag.putLong("craftCount", 256);
                tag.putLong("energyPerCraft", 100);
                tag.putInt("coolingTier", 0);
                tag.putInt("energyMultiplier", 1);
                tag.putInt("progress", index == 0 ? 40 : 0);
                tag.putLong("progressRemainder", index == 0 ? 123 : 0);
                tag.put("inputTotal", cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathStacks.writeGenericStacks(
                    java.util.List.of(new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.STONE), 256))));
                tag.put("outputTotal", cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathStacks.writeGenericStacks(
                    java.util.List.of(new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.DIAMOND), 256))));
                Object batch = load.invoke(null, tag, host.getLevel().getGameTime());
                require(batch != null, "Persisted task fixture was rejected");
                queue.addLast(batch);
            }
            host.setChanged();
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot prepare saved task UI fixture", error); }
    }

    private static void advance(int next) { stage = next; ticks = 0; }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
