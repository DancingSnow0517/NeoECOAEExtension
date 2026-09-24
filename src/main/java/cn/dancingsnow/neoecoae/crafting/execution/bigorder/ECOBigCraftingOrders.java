package cn.dancingsnow.neoecoae.crafting.execution.bigorder;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.compat.ae2.NeoECOCraftingServiceBridge;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderAdmission;
import cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent exact orders. Only one bounded, fully validated AE2 job is in flight per order. */
public final class ECOBigCraftingOrders extends SavedData {
    private static final String NAME = "neoecoae_big_crafting_orders";
    private static final ThreadLocal<Order> SUBMITTING = new ThreadLocal<>();
    private final Map<UUID, Order> orders = new LinkedHashMap<>();
    private final ListTag quarantined = new ListTag();

    public static ECOBigCraftingOrders get(MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(ECOBigCraftingOrders::load, ECOBigCraftingOrders::new, NAME);
    }

    public UUID add(ServerLevel level, BlockPos pos, UUID player, AEKey key, BigInteger amount) {
        if (amount.signum() <= 0) throw new IllegalArgumentException("Amount must be positive");
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.owner = player;
        order.dimension = level.dimension();
        order.pos = pos.immutable();
        order.key = key;
        order.encodedKey = key.toTagGeneric();
        order.ledger = new ECOBigCraftingLedger(amount);
        orders.put(order.id, order);
        setDirty();
        return order.id;
    }

    public String describe(UUID id, UUID player) {
        Order order = owned(id, player);
        return order.id + " : " + order.ledger.remaining() + " / " + order.ledger.total() + " : " + order.status;
    }

    public void cancel(UUID id, UUID player, MinecraftServer server) {
        Order order = owned(id, player);
        order.paused = true;
        order.status = "cancelled";
        if (order.future != null) order.future.cancel(true);
        order.future = null;
        if (order.ledger.jobId() != null)
            ECOCraftingJobLifecycle.finish(server.overworld(), order.ledger.jobId(), false);
        setDirty();
    }

    public void resume(UUID id, UUID player) {
        Order order = owned(id, player);
        if (order.key == null) throw new IllegalArgumentException("Order key is unavailable");
        if ("cancelled".equals(order.status)) throw new IllegalArgumentException("Cancelled orders cannot resume");
        order.paused = false;
        order.status = "queued";
        setDirty();
    }

    private Order owned(UUID id, UUID player) {
        Order order = orders.get(id);
        if (order == null || !order.owner.equals(player)) throw new IllegalArgumentException("Order not found");
        return order;
    }

    /** Called in the same server-thread submission that establishes the worker ownership UUID. */
    public static void bindSubmittedJob(UUID jobId) {
        Order order = SUBMITTING.get();
        if (order != null) order.ledger.bind(jobId, order.batch);
    }

    public void tick(MinecraftServer server) {
        for (Order order : orders.values()) {
            if ("cancelled".equals(order.status) && order.ledger.jobId() != null) {
                ECOCraftingJobLifecycle.finish(server.overworld(), order.ledger.jobId(), false);
            }
            if (order.paused || order.ledger.remaining().signum() == 0) continue;
            try {
                tickOrder(server, order);
            } catch (Exception failure) {
                if (order.future != null) order.future.cancel(true);
                order.future = null;
                order.paused = true;
                order.status = "paused: " + failure.getClass().getSimpleName();
                setDirty();
            }
        }
    }

    private void tickOrder(MinecraftServer server, Order order) throws Exception {
        if (order.ledger.jobId() != null) {
            Boolean completed = ECOCraftingJobLifecycle.completion(server.overworld(), order.ledger.jobId());
            if (completed == null) return;
            if (!completed) {
                order.paused = true;
                order.status = "cancelled";
            } else {
                order.ledger.complete(order.ledger.jobId());
                order.status = order.ledger.remaining().signum() == 0 ? "completed" : "queued";
            }
            setDirty();
            return;
        }
        ServerLevel level = server.getLevel(order.dimension);
        var player = server.getPlayerList().getPlayer(order.owner);
        if (level == null || player == null || !level.hasChunkAt(order.pos)) return;
        if (!(level.getBlockEntity(order.pos) instanceof NEBlockEntity host)) return;
        var node = host.getGridNode();
        if (node == null || !node.isActive()) return;
        var grid = node.getGrid();
        var source = IActionSource.ofPlayer(player);
        if (order.future != null && order.calculationGrid != grid) {
            order.future.cancel(true);
            order.future = null;
        }
        if (order.future == null) {
            order.batch = order.ledger.nextBatch(order.batchLimit);
            order.calculationGrid = grid;
            order.future = grid.getCraftingService()
                    .beginCraftingCalculation(
                            level, () -> source, order.key, order.batch, CalculationStrategy.REPORT_MISSING_ITEMS);
            order.status = "calculating " + order.batch;
            setDirty();
            return;
        }
        if (!order.future.isDone()) return;
        ICraftingPlan plan = order.future.get();
        order.future = null;
        ICraftingPlan executable = plan;
        if (plan != null && plan.simulation() && plan instanceof ECOCraftingPlanDiagnostics diagnostics
                && ECOBigOrderAdmission.allows(diagnostics.neoecoae$getPlanningResult(), false)) {
            try {
                var exactResult = diagnostics.neoecoae$getPlanningResult();
                if (ECOExactCraftingPlan.needsSmallerBatch(exactResult)) {
                    if (order.batch > 1L) {
                        order.batchLimit = Math.max(1L, order.batch / 2L);
                        order.status = "reducing batch: exact runtime limit";
                    } else {
                        order.paused = true;
                        order.status = "paused: exact runtime limit";
                    }
                    setDirty();
                    return;
                }
                executable = new ECOExactCraftingPlan(exactResult, false);
            } catch (IllegalArgumentException unsupported) {
                order.paused = true;
                order.status = "paused: " + unsupported.getMessage();
                setDirty();
                return;
            }
        }
        if (executable == null || executable.simulation()
                || !(executable instanceof ECOExactCraftingPlan) && !ECOBigCraftingBatchLimits.fits(executable)) {
            if (order.batch > 1) {
                order.batchLimit = Math.max(1, order.batch / 2);
            } else {
                order.paused = true;
                order.status = "paused: missing materials or unsupported plan";
            }
            setDirty();
            return;
        }
        if (!order.key.equals(executable.finalOutput().what()) || executable.finalOutput().amount() != order.batch)
            throw new IllegalStateException("Calculation changed the requested batch");
        SUBMITTING.set(order);
        try {
            var result = NeoECOCraftingServiceBridge.submitJob(grid, executable, null, null, source);
            if (result == null || !result.successful()) {
                if (order.batch > 1
                        && (result == null
                                || result.errorCode()
                                        == appeng.api.networking.crafting.CraftingSubmitErrorCode.CPU_TOO_SMALL
                                || result.errorCode()
                                        == appeng.api.networking.crafting.CraftingSubmitErrorCode.NO_CPU_FOUND)) {
                    order.batchLimit = Math.max(1, order.batch / 2);
                    order.status = "reducing batch";
                } else {
                    order.paused = true;
                    order.status = "paused: " + (result == null ? "no ECO CPU" : result.errorCode());
                }
            } else if (order.ledger.jobId() == null) {
                throw new IllegalStateException("ECO submission did not bind an order");
            } else order.status = "executing " + order.batch;
            setDirty();
        } finally {
            SUBMITTING.remove();
        }
    }

    public void stop() {
        for (Order order : orders.values()) {
            if (order.future != null) order.future.cancel(true);
            order.future = null;
        }
    }

    static ECOBigCraftingOrders load(CompoundTag tag) {
        ECOBigCraftingOrders data = new ECOBigCraftingOrders();
        for (Tag element : tag.getList("orders", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) element;
            try {
                Order order = new Order();
                order.id = value.getUUID("id");
                order.owner = value.getUUID("owner");
                order.dimension =
                        ResourceKey.create(Registries.DIMENSION, new ResourceLocation(value.getString("dimension")));
                order.pos = BlockPos.of(value.getLong("pos"));
                order.encodedKey = value.getCompound("key").copy();
                order.key = AEKey.fromTagGeneric(order.encodedKey);
                order.ledger = ECOBigCraftingLedger.read(value.getCompound("ledger"));
                order.batchLimit = value.getLong("batchLimit");
                order.batch = value.getLong("batch");
                if (order.ledger.jobId() != null && order.batch != order.ledger.batch())
                    throw new IllegalArgumentException("Inconsistent active order batch");
                order.paused = value.getBoolean("paused");
                order.status = value.getString("status");
                if (order.key == null || order.batchLimit <= 0) {
                    order.paused = true;
                    order.status = "paused: invalid saved order";
                }
                data.orders.put(order.id, order);
            } catch (RuntimeException invalid) {
                data.quarantined.add(value.copy());
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Order order : orders.values()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("id", order.id);
            value.putUUID("owner", order.owner);
            value.putString("dimension", order.dimension.location().toString());
            value.putLong("pos", order.pos.asLong());
            value.put("key", order.encodedKey.copy());
            value.put("ledger", order.ledger.write());
            value.putLong("batchLimit", order.batchLimit);
            value.putLong("batch", order.batch);
            value.putBoolean("paused", order.paused);
            value.putString("status", order.status);
            list.add(value);
        }
        for (Tag value : quarantined) list.add(value.copy());
        tag.put("orders", list);
        return tag;
    }

    private static final class Order {
        UUID id, owner;
        ResourceKey<Level> dimension;
        BlockPos pos;
        AEKey key;
        CompoundTag encodedKey;
        ECOBigCraftingLedger ledger;
        long batchLimit = Long.MAX_VALUE;
        long batch;
        boolean paused;
        String status = "queued";
        Future<ICraftingPlan> future;
        appeng.api.networking.IGrid calculationGrid;
    }
}
