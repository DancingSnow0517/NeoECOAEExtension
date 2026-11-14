package cn.dancingsnow.neoecoae.uiprobe;

import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.graph.ClientCraftingGraph;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutEngine;
import cn.dancingsnow.neoecoae.crafting.graph.RecursiveCycleNode;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.multiblock.network.NENetworkSwitchUtil;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildSession;
import cn.dancingsnow.neoecoae.multiblock.placement.WorldPlannedBlock;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.ValueWire;
import cn.dancingsnow.neoecoae.storage.transfer.ECOCreativeExtractionFilter;
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
import java.util.List;
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
        check("Long menu argument framing", DedicatedUiProbe::longWire);
        check("native creative storage routing and scope", DedicatedUiProbe::creativeRouting);
        check("build bounds and missing-material placement", () -> buildPlacement(level, player));
        check("network-switch preview reuse", () -> switchPreview(level));
        check("recursive graph centered materials and separate ports", DedicatedUiProbe::recursiveGraph);
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

    private static void longWire() {
        for (long expected : new long[] {Long.MIN_VALUE, -1, 0, 1, 1L << 53, Long.MAX_VALUE}) {
            var wire = new FriendlyByteBuf(Unpooled.buffer());
            try {
                ValueWire.write(wire, Integer.class, 19);
                ValueWire.write(wire, Long.class, expected);
                ValueWire.write(wire, Boolean.class, true);
                require(ValueWire.read(wire, Integer.class).equals(19), "CPU identity lost");
                require(ValueWire.read(wire, Long.class).equals(expected), "Long request changed");
                require(ValueWire.read(wire, Boolean.class).equals(true) && !wire.isReadable(), "RPC framing changed");
            } finally { wire.release(); }
        }
    }

    private static void creativeRouting() {
        var key = appeng.api.stacks.AEItemKey.of(Items.STONE);
        var source = appeng.api.networking.security.IActionSource.empty();
        var stack = appeng.core.definitions.AEItems.ITEM_CELL_CREATIVE.stack();
        appeng.items.contents.CellConfig.create(stack).setStack(0, new appeng.api.stacks.GenericStack(key, 1));
        var creative = appeng.api.storage.StorageCells.getCellInventory(stack, null);
        require(creative instanceof ECOCreativeExtractionFilter.CreativeStorage, "Native creative mixin was not applied");
        var wrapped = new appeng.api.storage.SupplierStorage(() -> new appeng.me.storage.DelegatingMEInventory(creative));
        require(ECOCreativeExtractionFilter.extract(wrapped, key, 128, source) == 0, "Accessor wrappers hid creative input");
        var finite = appeng.api.storage.StorageCells.getCellInventory(appeng.core.definitions.AEItems.ITEM_CELL_256K.stack(), null);
        require(finite != null, "Missing ordinary 256K inventory");
        var network = new appeng.me.storage.NetworkStorage();
        network.mount(100, wrapped);
        network.mount(0, finite);
        long inserted = ECOCreativeExtractionFilter.transfer(false,
            () -> network.insert(key, 128, appeng.api.config.Actionable.MODULATE, source));
        require(inserted == 128 && finite.getAvailableStacks().get(key) == 128, "Native creative output swallowed stock");
        require(network.insert(key, 1, appeng.api.config.Actionable.MODULATE, source) == 1
            && finite.getAvailableStacks().get(key) == 128, "IO scope changed ordinary AE2 insertion");
        require(ECOCreativeExtractionFilter.extract(network, key, 128, source) == 128
            && finite.getAvailableStacks().get(key) == 0, "Native mixed input bypassed the finite inventory");
    }

    private static void buildPlacement(ServerLevel level, ServerPlayer player) {
        var pos = new BlockPos(50, 100, 50);
        level.getChunkAt(pos);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        player.getInventory().clearContent();
        var session = new MultiBlockBuildSession(List.of(new WorldPlannedBlock(pos,
            Blocks.STONE.defaultBlockState(), new ItemStack(Items.STONE, 3))), 0);
        require(MultiBlockPlacementService.tickBuild(level, session, player)
            == MultiBlockPlacementService.PlacementTickResult.MISSING_ITEMS, "Missing material was reported as blocked space");
        require(level.getBlockState(pos).isAir() && !session.isFinished(), "Missing material placed or advanced a block");
        player.getInventory().setItem(0, new ItemStack(Items.STONE, 3));
        require(MultiBlockPlacementService.tickBuild(level, session, player)
            == MultiBlockPlacementService.PlacementTickResult.COMPLETED, "Owned materials did not complete placement");
        require(level.getBlockState(pos).is(Blocks.STONE) && player.getInventory().getItem(0).isEmpty(), "Placement changed material custody");
        var floor = new BlockPos(72, level.getMinBuildHeight(), 72);
        level.getChunkAt(floor);
        var plan = MultiBlockPlacementService.preview(level, floor, NEBlocks.INTEGRATED_WORKING_STATION.getDefaultState(),
            NEMultiBlocks.LARGE_INTEGRATED_WORKING_STATION, 1, false);
        require(plan.getConflictPositions().stream().anyMatch(level::isOutsideBuildHeight), "Preview accepted blocks below build height");
        var ceiling = new BlockPos(72, level.getMaxBuildHeight() - 1, 72);
        var controllerState = NEBlocks.CRAFTING_SYSTEM_L9.getDefaultState();
        level.setBlockAndUpdate(ceiling, controllerState);
        var controller = (cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity) level.getBlockEntity(ceiling);
        var definition = controller.getBuildDefinition();
        var upperPlan = MultiBlockPlacementService.preview(level, ceiling, controllerState, definition, definition.getExpandMin(), false);
        require(upperPlan.getConflictPositions().stream().anyMatch(level::isOutsideBuildHeight), "Preview accepted blocks above build height");
    }

    private static void switchPreview(ServerLevel level) {
        var pos = new BlockPos(72, 100, 72);
        level.getChunkAt(pos);
        var state = NEBlocks.CRAFTING_SYSTEM_L9.getDefaultState();
        level.setBlockAndUpdate(pos, state);
        var controller = (cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity) level.getBlockEntity(pos);
        var definition = controller.getBuildDefinition();
        require(definition != null, "Missing crafting structure definition");
        for (boolean mirrored : new boolean[] {false, true}) {
            level.setBlockAndUpdate(NENetworkSwitchUtil.switchPosition(pos, state, false), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(NENetworkSwitchUtil.switchPosition(pos, state, true), Blocks.AIR.defaultBlockState());
            var switchPos = NENetworkSwitchUtil.switchPosition(pos, state, mirrored);
            level.setBlockAndUpdate(switchPos, NEBlocks.CRAFTING_NETWORK_SWITCH.getDefaultState());
            var own = MultiBlockPlacementService.preview(level, pos, state, definition, definition.getExpandMin(), mirrored);
            require(!own.getConflictPositions().contains(switchPos) && own.getReusedBlockCount() == 1,
                "Compatible switch was not reused in mirror mode " + mirrored);
            level.setBlockAndUpdate(switchPos, NEBlocks.COMPUTATION_NETWORK_SWITCH.getDefaultState());
            var foreign = MultiBlockPlacementService.preview(level, pos, state, definition, definition.getExpandMin(), mirrored);
            require(foreign.getConflictPositions().contains(switchPos), "A computation switch replaced crafting casing");
        }
    }

    private static void recursiveGraph() throws Exception {
        var stone = appeng.api.stacks.AEItemKey.of(Items.STONE);
        var dirt = appeng.api.stacks.AEItemKey.of(Items.DIRT);
        var nodes = new LinkedHashMap<Integer, ClientCraftingGraph.Node>();
        var roles = new RecursiveCycleNode.Role[] {RecursiveCycleNode.Role.SEED, RecursiveCycleNode.Role.INPUT,
            RecursiveCycleNode.Role.STEP, RecursiveCycleNode.Role.STOCK, RecursiveCycleNode.Role.STOCK,
            RecursiveCycleNode.Role.STEP, RecursiveCycleNode.Role.RETAINED, RecursiveCycleNode.Role.RETAINED,
            RecursiveCycleNode.Role.OUTPUT};
        int[] levels = {0, 0, 1, 2, 2, 3, 4, 4, 4};
        for (int id = 0; id < roles.length; id++) {
            var key = roles[id] == RecursiveCycleNode.Role.STEP ? null : id == 4 || id == 7 ? dirt : stone;
            nodes.put(id, new ClientCraftingGraph.Node(id, ClientCraftingGraph.Kind.RECURSION, "probe", key,
                null, null, null, null, new RecursiveCycleNode(roles[id], levels[id], PlannerAmount.of(1), null)));
        }
        var links = new ArrayList<ClientCraftingGraph.Link>();
        for (int[] pair : new int[][] {{0, 2}, {1, 2}, {2, 3}, {2, 4}, {3, 5}, {4, 5}, {5, 6}, {5, 7}, {3, 8}})
            links.add(new ClientCraftingGraph.Link(pair[0], pair[1], 1L, CraftingGraphSnapshot.EdgeKind.CYCLE_INTERNAL, true));
        var constructor = ClientCraftingGraph.class.getDeclaredConstructor(CraftingGraphSnapshot.class,
            ClientCraftingGraph.View.class, int.class, int.class, Map.class, List.class);
        constructor.setAccessible(true);
        var graph = (ClientCraftingGraph) constructor.newInstance(CraftingGraphSnapshot.EMPTY,
            ClientCraftingGraph.View.CYCLE_FOCUS, 5, -1, nodes, links);
        var backendConstructor = Class.forName("cn.dancingsnow.neoecoae.crafting.graph.RecursiveCycleLayout").getDeclaredConstructor();
        backendConstructor.setAccessible(true);
        var layout = ((GraphLayoutEngine) backendConstructor.newInstance()).layout(graph, 1);
        require(Math.abs(layout.box(0).centerY() - layout.box(2).centerY()) < 0.01f, "Seed material was not centered");
        require(!layout.edgePoints(links.get(4)).get(0).equals(layout.edgePoints(links.get(8)).get(0)), "Delivery and stock shared a port");
        var bounds = layout.bounds();
        for (var link : links) {
            require(layout.edgePoints(link).size() >= 2, "An edge lost its route");
            for (var point : layout.edgePoints(link)) require(Float.isFinite(point.x()) && Float.isFinite(point.y())
                && point.x() >= bounds.left() && point.x() <= bounds.right()
                && point.y() >= bounds.top() && point.y() <= bounds.bottom(), "Route escaped the graph viewport bounds");
        }
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
