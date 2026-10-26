package cn.dancingsnow.neoecoae.compatprobe;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.blockentity.grid.AENetworkBlockEntity;
import appeng.core.definitions.AEBlocks;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/** Physical machines assembled from the mod's published placement definitions. */
final class NeoMachineRig {
    final ServerLevel level;
    final BlockPos origin;
    final List<BlockPos> positions = new ArrayList<>();
    final List<IGridConnection> connections = new ArrayList<>();
    ECOStorageSystemBlockEntity storageHost;
    ECOComputationSystemBlockEntity computation;
    ECOCraftingSystemBlockEntity crafting;
    ECOIntegratedWorkingStationBlockEntity workstation;
    ECOLargeIntegratedWorkingStationBlockEntity large;
    AENetworkBlockEntity power;
    boolean installed;

    NeoMachineRig(ServerLevel level, BlockPos origin, boolean restore) {
        this.level = level;
        this.origin = origin;
        for (int x = -1; x <= 6; x++) for (int z = -1; z <= 2; z++)
            level.setChunkForced((origin.getX() >> 4) + x, (origin.getZ() >> 4) + z, true);
        if (!restore) {
            assemble(origin, NEBlocks.STORAGE_SYSTEM_L9.get(), NEMultiBlocks.STORAGE_SYSTEM_L9);
            assemble(origin.offset(16, 0, 0), NEBlocks.COMPUTATION_SYSTEM_L9.get(), NEMultiBlocks.COMPUTATION_SYSTEM_L9);
            assemble(origin.offset(32, 0, 0), NEBlocks.CRAFTING_SYSTEM_L9.get(), NEMultiBlocks.CRAFTING_SYSTEM_L9);
            place(origin.offset(48, 0, 0), NEBlocks.INTEGRATED_WORKING_STATION.get());
            assemble(origin.offset(64, 0, 0), NEBlocks.INTEGRATED_WORKING_STATION.get(), NEMultiBlocks.LARGE_INTEGRATED_WORKING_STATION);
            place(origin.offset(0, 4, 0), AEBlocks.CREATIVE_ENERGY_CELL.block());
        }
        storageHost = (ECOStorageSystemBlockEntity) level.getBlockEntity(origin);
        computation = (ECOComputationSystemBlockEntity) level.getBlockEntity(origin.offset(16, 0, 0));
        crafting = (ECOCraftingSystemBlockEntity) level.getBlockEntity(origin.offset(32, 0, 0));
        workstation = (ECOIntegratedWorkingStationBlockEntity) level.getBlockEntity(origin.offset(48, 0, 0));
        large = (ECOLargeIntegratedWorkingStationBlockEntity) level.getBlockEntity(origin.offset(64, 0, 0));
        power = (AENetworkBlockEntity) level.getBlockEntity(origin.offset(0, 4, 0));
        check(storageHost != null && computation != null && crafting != null && workstation != null && large != null && power != null,
                "NeoECO fixture block entities are missing");
        if (restore) installed = true;
    }

    private void assemble(BlockPos position, Block block, MultiBlockDefinition definition) {
        place(position, block);
        var plan = MultiBlockPlacementService.preview(level, position, level.getBlockState(position), definition, 1, false);
        check(plan.getConflictPositions().isEmpty(), "NeoECO fixture overlaps another structure");
        for (var part : plan.getAllBlocks()) {
            level.setBlockAndUpdate(part.worldPos(), part.targetState());
            positions.add(part.worldPos());
        }
        BlockEntity controller = level.getBlockEntity(position);
        if (controller instanceof NEBlockEntity<?, ?> host) host.rebuildMultiblock();
        if (controller instanceof ECOLargeIntegratedWorkingStationBlockEntity host) host.rebuildMultiblock();
    }

    void place(BlockPos position, Block block) {
        level.setBlockAndUpdate(position, block.defaultBlockState());
        positions.add(position);
    }

    boolean ready() throws Exception {
        if (!storageHost.isFormed() || !computation.isFormed() || !crafting.isFormed() || !large.isFormed()) return false;
        for (var host : List.of(storageHost, computation, crafting, workstation, large, power))
            if (host.getMainNode().getNode() == null) return false;
        connect(port(computation).getMainNode());
        connect(port(crafting).getMainNode());
        connect(workstation.getMainNode());
        connect(large.getCluster().getCommunication().getMainNode());
        connect(power.getMainNode());
        if (!installed) {
            var drives = storageHost.getCluster().getDrives();
            check(drives.size() >= 3, "Storage definition has fewer than three drives");
            drives.get(0).setCellStack(NEItems.ECO_ITEM_CELL_256M.asStack());
            drives.get(1).setCellStack(NEItems.ECO_ITEM_CELL_256M.asStack());
            drives.get(2).setCellStack(NEItems.ECO_FLUID_CELL_256M.asStack());
            for (var drive : computation.getCluster().getUpperDrives()) drive.setCellStack(NEItems.ECO_COMPUTATION_CELL_L9.asStack());
            for (var drive : computation.getCluster().getLowerDrives()) drive.setCellStack(NEItems.ECO_COMPUTATION_CELL_L9.asStack());
            installed = true;
            return false;
        }
        return storageHost.getMainNode().isOnline() && computation.getMainNode().isOnline()
                && crafting.getMainNode().isOnline() && workstation.getMainNode().isOnline() && large.getMainNode().isOnline()
                && storageHost.getCluster().getDrives().stream().filter(drive -> drive.getCellStack() != null
                        && !drive.getCellStack().isEmpty()).count() == 3
                && storageHost.getCluster().getDrives().stream().filter(drive -> drive.getCellStack() != null
                        && !drive.getCellStack().isEmpty()).allMatch(drive -> drive.isMounted());
    }

    void connect(IManagedGridNode node) throws Exception {
        if (node.getNode() == null) return;
        if (node.getGrid() != storageHost.getMainNode().getGrid())
            connections.add(GridHelper.createConnection(port(storageHost).getMainNode().getNode(), node.getNode()));
    }

    private AENetworkBlockEntity port(NEBlockEntity<?, ?> host) {
        var members = host.getCluster().getBlockEntities();
        while (members.hasNext()) {
            var member = members.next();
            if (member instanceof cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity<?> communication) return communication;
        }
        throw new AssertionError("Formed NeoECO host has no communication interface");
    }

    IGrid grid() { return storageHost.getMainNode().getGrid(); }
    MEStorage inventory() { return grid().getStorageService().getInventory(); }
    long amount(AEKey key) { return inventory().getAvailableStacks().get(key); }

    void seed(AEKey key, long amount) {
        check(inventory().insert(key, amount, Actionable.MODULATE, IActionSource.empty()) == amount, "NeoECO storage rejected fixture input " + key);
    }

    void feed(ECOIntegratedWorkingStationBlockEntity machine, java.util.Map<AEKey, Long> input) {
        for (var entry : input.entrySet()) {
            long amount = entry.getValue();
            check(inventory().extract(entry.getKey(), amount, Actionable.MODULATE, IActionSource.empty()) == amount, "Workstation feeder did not own its input");
            if (entry.getKey() instanceof AEItemKey item) {
                var left = item.toStack(Math.toIntExact(amount));
                for (int slot = 0; slot < 9 && !left.isEmpty(); slot++) left = machine.getInternalInventory().insertItem(slot, left, false);
                check(left.isEmpty(), "Workstation item input is full");
            } else if (entry.getKey() instanceof AEFluidKey fluid) {
                check(machine.getFluidCombined().fill(fluid.toStack(Math.toIntExact(amount)), IFluidHandler.FluidAction.EXECUTE) == amount,
                        "Workstation fluid input is full");
            } else throw new AssertionError("Unsupported workstation key " + entry.getKey());
        }
    }

    void collect(ECOIntegratedWorkingStationBlockEntity machine) {
        var inv = machine.getInternalInventory();
        for (int slot = 9; slot < inv.size(); slot++) {
            var stack = inv.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            long accepted = inventory().insert(AEItemKey.of(stack), stack.getCount(), Actionable.MODULATE, IActionSource.empty());
            check(inv.extractItem(slot, Math.toIntExact(accepted), false).getCount() == accepted, "Workstation output extraction changed");
        }
        var tank = machine.getFluidCombined();
        FluidStack fluid = tank.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        if (!fluid.isEmpty()) {
            long accepted = inventory().insert(AEFluidKey.of(fluid.getFluid(), fluid.getTag()), fluid.getAmount(), Actionable.MODULATE, IActionSource.empty());
            check(tank.drain(Math.toIntExact(accepted), IFluidHandler.FluidAction.EXECUTE).getAmount() == accepted, "Workstation fluid output extraction changed");
        }
    }

    void powerOff() {
        power.getMainNode().destroy();
        for (var cell : storageHost.getCluster().getEnergyCells()) cell.extractAEPower(Double.MAX_VALUE, Actionable.MODULATE, PowerMultiplier.ONE);
        workstation.extractAEPower(Double.MAX_VALUE, Actionable.MODULATE, PowerMultiplier.ONE);
        large.extractAEPower(Double.MAX_VALUE, Actionable.MODULATE, PowerMultiplier.ONE);
        grid().getEnergyService().extractAEPower(Double.MAX_VALUE, Actionable.MODULATE, PowerMultiplier.ONE);
    }

    void powerOn() throws Exception {
        var position = origin.offset(0, 4, 0);
        level.removeBlock(position, false);
        place(position, AEBlocks.CREATIVE_ENERGY_CELL.block());
        power = (AENetworkBlockEntity) level.getBlockEntity(position);
    }

    static AEItemKey item(String id) {
        var location = new ResourceLocation(id);
        check(BuiltInRegistries.ITEM.containsKey(location), "Missing pack item " + id);
        return AEItemKey.of(BuiltInRegistries.ITEM.get(location));
    }

    static AEFluidKey fluid(String id) {
        var location = new ResourceLocation(id);
        check(BuiltInRegistries.FLUID.containsKey(location), "Missing pack fluid " + id);
        return AEFluidKey.of(BuiltInRegistries.FLUID.get(location));
    }

    static void check(boolean condition, String detail) { if (!condition) throw new AssertionError(detail); }
}
