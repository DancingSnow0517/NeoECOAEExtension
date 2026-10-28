package cn.dancingsnow.neoecoae.uiprobe;

import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.factory.BlockUIMenuType;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.factory.BlockUIMenuType.BlockUI;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.factory.BlockUIMenuType.BlockUIHolder;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Button;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.ItemSlot;
import com.google.gson.GsonBuilder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Runs only in an explicitly launched disposable server, then saves and stops that server. */
@Mod("neoeco_ui_probe")
public final class DedicatedUiProbe {
    private final Map<String, Object> results = new LinkedHashMap<>();
    private int failures, menus, factoryOpens, skippedSeparateRoutes;

    public DedicatedUiProbe() { MinecraftForge.EVENT_BUS.addListener(this::run); }

    @FunctionalInterface private interface Check { void run() throws Exception; }
    private static final class SeparateMenuRoute extends RuntimeException {}
    private void check(String name, Check check) {
        try {
            check.run();
            results.put(name, "pass");
            System.out.println("UI_SERVER_PROBE PASS " + name);
        } catch (SeparateMenuRoute separate) {
            skippedSeparateRoutes++;
            results.put(name, "skip: separate native AE2 menu route");
            System.out.println("UI_SERVER_PROBE SKIP " + name + ": separate native AE2 menu route");
        } catch (Throwable failure) {
            failures++;
            results.put(name, failure.toString());
            failure.printStackTrace();
        }
    }
    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }

    private void run(ServerStartedEvent event) {
        var server = event.getServer();
        var level = server.overworld();
        var player = FakePlayerFactory.getMinecraft(level);
        var pos = new BlockPos(0, 100, 0);
        player.setPos(0.5, 100.5, 0.5);
        check("shared texture and workstation initialization", () -> {
            for (String name : new String[] {"platform.ui.runtime.gui.texture.IGuiTexture", "gui.theme.AETextures",
                    "gui.workstation.IntegratedWorkstationUI", "gui.workstation.WorkstationTextures",
                    "gui.workstation.WorkstationOutputPanel"}) {
                Class.forName("cn.dancingsnow.neoecoae." + name);
            }
        });
        var blocks = new ArrayList<Block>();
        for (var block : ForgeRegistries.BLOCKS.getValues()) {
            var id = ForgeRegistries.BLOCKS.getKey(block);
            if (id != null && id.getNamespace().equals("neoecoae") && block instanceof BlockUI) blocks.add(block);
        }
        blocks.sort(Comparator.comparing(block -> ForgeRegistries.BLOCKS.getKey(block).toString()));
        require(!blocks.isEmpty(), "No block UI routes were registered");
        for (var block : blocks) check("open and reopen " + ForgeRegistries.BLOCKS.getKey(block), () -> {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos, block.defaultBlockState());
            for (int repeat = 0; repeat < 2; repeat++) {
                var holder = new BlockUIHolder(player, pos);
                require(!holder.isInvalid(), "Fresh holder rejected");
                var ui = holder.createUI(player);
                if (ui == null && level.getBlockEntity(pos) instanceof
                        cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInterfaceBlockEntity)
                    throw new SeparateMenuRoute();
                require(ui != null, "Null menu template");
                ui.initWidgets();
                var menu = new ModularUIContainer(ui, 20 + menus++);
                player.containerMenu = menu;
                var data = encode(ui);
                data.release();
                menu.broadcastChanges();
                player.closeContainer();
                require(BlockUIMenuType.openUI(player, pos), "Factory failed to open a valid UI");
                factoryOpens++;
                player.closeContainer();
            }
        });
        check("formed workstation snapshot and stale holder actions", () -> workstation(level, player, pos));
        results.put("java", System.getProperty("java.version"));
        results.put("failures", failures);
        results.put("menus", menus);
        results.put("factoryOpens", factoryOpens);
        results.put("skippedSeparateRoutes", skippedSeparateRoutes);
        results.put("realClientNetworkRoundTrip", false);
        try {
            var destination = Path.of(System.getProperty("neoeco.uiProbe.report"));
            Files.writeString(destination, new GsonBuilder().setPrettyPrinting().create().toJson(results));
        } catch (Exception failure) { throw new RuntimeException(failure); }
        System.out.println("UI_SERVER_PROBE RESULT failures=" + failures + " menus=" + menus + " opens=" + factoryOpens);
        server.halt(false);
    }

    private void workstation(ServerLevel level, ServerPlayer player, BlockPos pos) {
        var station = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("neoecoae", "integrated_working_station"));
        require(station != null, "Missing workstation");
        var plan = MultiBlockPlacementService.preview(level, pos, station.defaultBlockState(),
            NEMultiBlocks.LARGE_INTEGRATED_WORKING_STATION, 1, false);
        for (var part : plan.getAllBlocks()) level.setBlockAndUpdate(part.worldPos(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, station.defaultBlockState());
        var staleState = new BlockUIHolder(player, pos);
        var entity = (ECOLargeIntegratedWorkingStationBlockEntity) level.getBlockEntity(pos);
        for (var part : plan.getAllBlocks()) level.setBlockAndUpdate(part.worldPos(), part.targetState());
        entity.rebuildMultiblock();
        require(entity.isFormed(), "Workstation did not form");
        var authoritative = new BlockUIHolder(player, pos);
        var source = authoritative.createUI(player);
        source.initWidgets();
        var oldTree = staleState.createUI(player);
        oldTree.initWidgets();
        require(source.getFlatWidgetCollection().size() != oldTree.getFlatWidgetCollection().size(), "Missing stale-tree control");
        var wire = new FriendlyByteBuf(Unpooled.buffer());
        try {
            authoritative.writeToSyncData(wire);
            level.setBlock(pos, authoritative.blockState.setValue(ECOIntegratedWorkingStation.FORMED, false), 2);
            var received = BlockUIHolder.readFromSyncData(player, wire);
            require(received.blockState.equals(authoritative.blockState), "Snapshot was not transmitted");
            require(!wire.isReadable(), "Holder decoder left bytes behind");
            var replica = received.createUI(player);
            replica.initWidgets();
            require(source.getFlatWidgetCollection().size() == replica.getFlatWidgetCollection().size(), "Snapshot-selected widget trees differ");
            var sourceData = encode(source);
            var replicaData = encode(replica);
            try {
                require(sourceData.readableBytes() == replicaData.readableBytes(), "Initial schemas differ");
                results.put("formedInitialBytes", sourceData.readableBytes());
                results.put("formedWidgets", source.getFlatWidgetCollection().size());
            } finally { sourceData.release(); replicaData.release(); }
        } finally { wire.release(); }
        level.setBlock(pos, authoritative.blockState, 2);

        var holder = new BlockUIHolder(player, pos);
        var ui = holder.createUI(player);
        var root = (UIElement) ui.mainGroup;
        var actions = new AtomicInteger();
        var customClicks = new AtomicInteger();
        var button = new Button().setOnServerClick(event -> actions.incrementAndGet());
        var contents = new SimpleContainer(1);
        contents.setItem(0, new ItemStack(Items.STONE, 3));
        var slot = new ItemSlot(new Slot(contents, 0, 0, 0));
        var custom = new ItemSlot(new Slot(new SimpleContainer(1), 0, 0, 0))
            .nativeClickHandler((which, kind, actor) -> { customClicks.incrementAndGet(); return ItemStack.EMPTY; });
        root.addChildren(button, slot, custom);
        ui.initWidgets();
        var menu = new ModularUIContainer(ui, 100);
        player.containerMenu = menu;
        var initial = encode(ui);
        initial.release(); // UIFactory always sends initial tank state before any broadcast.
        var nativeSlot = (SlotWidget) slot.widgets.get(0);
        var customSlot = (SlotWidget) custom.widgets.get(0);
        require(nativeSlot.getHandler().mayPickup(player), "Valid pickup denied");
        require(nativeSlot.getHandler().mayPlace(new ItemStack(Items.STONE)), "Valid placement denied");
        menu.clicked(nativeSlot.getHandler().index, 0, ClickType.PICKUP, player);
        require(menu.getCarried().getCount() == 3 && contents.getItem(0).isEmpty(), "Valid native pickup failed");
        menu.clicked(nativeSlot.getHandler().index, 0, ClickType.PICKUP, player);
        require(menu.getCarried().isEmpty() && contents.getItem(0).getCount() == 3, "Valid native placement failed");
        click(button);
        customSlot.slotClick(0, ClickType.PICKUP, player);
        require(actions.get() == 1 && customClicks.get() == 1, "Valid actions denied");
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, station.defaultBlockState());
        require(holder.isInvalid(), "Same-block entity replacement kept the old holder valid");
        require(!root.activeMenu(), "Stale menu still active before broadcast");
        click(button);
        require(!nativeSlot.getHandler().mayPickup(player), "Stale pickup allowed");
        require(!nativeSlot.getHandler().mayPlace(new ItemStack(Items.STONE)), "Stale placement allowed");
        require(customSlot.slotClick(0, ClickType.PICKUP, player) == ItemStack.EMPTY, "Stale custom click allowed vanilla fallback");
        require(actions.get() == 1 && customClicks.get() == 1, "Stale actions executed");
        menu.broadcastChanges();
        require(player.containerMenu != menu, "Stale menu did not close");
        require(contents.getItem(0).getCount() == 3, "Stale menu changed owned items");
    }

    private static FriendlyByteBuf encode(ModularUI ui) {
        var data = new FriendlyByteBuf(Unpooled.buffer());
        try { ui.mainGroup.writeInitialData(data); return data; }
        catch (Throwable failure) { data.release(); throw failure; }
    }
    private static void click(Button button) {
        var data = new FriendlyByteBuf(Unpooled.buffer());
        try { data.writeVarInt(0); button.handleClientAction(710, data); }
        finally { data.release(); }
    }
}
