package cn.dancingsnow.neoecoae.network;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.menu.me.crafting.CraftingCPUMenu;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.compat.ae2.NeoECOCraftingCpuMenuBridge;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPULogic;
import cn.dancingsnow.neoecoae.crafting.execution.ElapsedTimeTracker;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import io.netty.buffer.Unpooled;
import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("neoecoae")
@PrefixGameTestTemplate(false)
public final class NetworkSyncGameTest {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void realNbtKeysAndExactQuantityDeletionRoundTrip(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.getOrCreateTag().putByteArray("payload", new byte[80_000]);
        var key = AEItemKey.of(stack);
        var amount = BigInteger.ONE.shiftLeft(200);
        var initial = new ECOExactStoragePayload(true, Map.of(key, amount));
        byte[] bytes = BoundedData.encode(BoundedData.MAX_BYTES, out -> ECOExactStoragePayload.encode(initial, out));
        var receiver = new BoundedData.Receiver();
        byte[] complete = null;
        for (int offset = 0; offset < bytes.length; offset += BoundedData.PART_BYTES) {
            complete = receiver.accept(
                    bytes.length,
                    offset,
                    java.util.Arrays.copyOfRange(
                            bytes, offset, Math.min(bytes.length, offset + BoundedData.PART_BYTES)));
        }
        var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(complete));
        try {
            var result = ECOExactStoragePayload.decode(buffer).apply(Map.of());
            helper.assertTrue(result.get(key).equals(amount), "real NBT key or exact amount was corrupted");
            var removal = ECOExactStoragePayload.difference(result, Map.of());
            helper.assertTrue(removal.apply(result).isEmpty(), "stale exact override remains after removal");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void transformedIdleCpuSendsOnlyOneReset(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(
                level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "SyncTest"));
        var original = player.connection;
        var packets = new AtomicInteger();
        player.connection =
                new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player) {
                    @Override
                    public void send(Packet<?> packet) {
                        packets.incrementAndGet();
                    }
                };
        helper.setBlock(BlockPos.ZERO, Blocks.CHEST);
        var host = level.getBlockEntity(helper.absolutePos(BlockPos.ZERO));
        var menu = new CraftingCPUMenu(CraftingCPUMenu.TYPE, 7, player.getInventory(), host);
        var cpu = new ECOCraftingCPU(new NEComputationCluster(BlockPos.ZERO, BlockPos.ZERO), 1000L, ECOTier.L4) {
            final ChangingLogic source = new ChangingLogic(this);

            @Override
            public ECOCraftingCPULogic getLogic() {
                return source;
            }
        };
        var select =
                CraftingCPUMenu.class.getDeclaredMethod("setCPU", appeng.api.networking.crafting.ICraftingCPU.class);
        select.setAccessible(true);
        packets.set(0);
        select.invoke(menu, cpu);
        helper.assertTrue(packets.get() == 1, "initial CPU selection must send exactly one reset");
        for (int tick = 1; tick <= 30; tick++) {
            helper.runAfterDelay(tick, () -> ((NeoECOCraftingCpuMenuBridge) menu).neoecoae$broadcastEcoCpuChanges());
        }
        helper.runAfterDelay(31, () -> {
            helper.assertTrue(packets.get() == 1, "idle CPU keeps sending reset/status packets");
        });
        for (int tick = 32; tick < 52; tick++) {
            helper.runAfterDelay(tick, () -> {
                cpu.source.active = true;
                cpu.source.quantity++;
                ((NeoECOCraftingCpuMenuBridge) menu).neoecoae$broadcastEcoCpuChanges();
            });
        }
        helper.runAfterDelay(52, () -> {
            helper.assertTrue(packets.get() == 5, "active counts/headers bypassed the five-tick window");
            cpu.source.paused = true;
            ((NeoECOCraftingCpuMenuBridge) menu).neoecoae$broadcastEcoCpuChanges();
            helper.assertTrue(packets.get() == 6, "pause did not synchronize immediately");
        });
        helper.runAfterDelay(53, () -> {
            cpu.source.paused = false;
            ((NeoECOCraftingCpuMenuBridge) menu).neoecoae$broadcastEcoCpuChanges();
            helper.assertTrue(packets.get() == 7, "resume was delayed by the send window");
        });
        helper.runAfterDelay(54, () -> {
            cpu.source.active = false;
            ((NeoECOCraftingCpuMenuBridge) menu).neoecoae$broadcastEcoCpuChanges();
            helper.assertTrue(packets.get() == 8, "completion must clear the task immediately");
        });
        for (int tick = 55; tick < 65; tick++) {
            helper.runAfterDelay(tick, () -> ((NeoECOCraftingCpuMenuBridge) menu).neoecoae$broadcastEcoCpuChanges());
        }
        helper.runAfterDelay(65, () -> {
            ((NeoECOCraftingCpuMenuBridge) menu).neoecoae$cleanupEcoCpuListener();
            player.connection = original;
            helper.assertTrue(packets.get() == 8, "completed CPU resumed idle spam");
            helper.succeed();
        });
    }

    /** Controlled source events drive the real, transformed AE2 menu and its real packet encoder. */
    private static final class ChangingLogic extends ECOCraftingCPULogic {
        boolean active;
        boolean paused;
        long quantity;
        final AEKey key = AEItemKey.of(Items.IRON_INGOT);
        final ElapsedTimeTracker tracker = new ElapsedTimeTracker() {
            @Override
            public long getElapsedTime() {
                return active ? quantity * 1000 : 0;
            }

            @Override
            public long getSyntheticRemainingItemCount() {
                return active ? 100 - quantity : 0;
            }

            @Override
            public long getSyntheticStartItemCount() {
                return active ? 100 : 0;
            }
        };

        ChangingLogic(ECOCraftingCPU cpu) {
            super(cpu);
        }

        @Override
        public boolean hasJob() {
            return active;
        }

        @Override
        public boolean isJobSuspended() {
            return paused;
        }

        @Override
        public boolean isJobUserPaused() {
            return paused;
        }

        @Override
        public long getStatusRevision() {
            return quantity;
        }

        @Override
        public void getAllItems(KeyCounter items) {
            if (active) items.add(key, quantity);
        }

        @Override
        public long getStored(AEKey what) {
            return 0;
        }

        @Override
        public long getWaitingFor(AEKey what) {
            return active ? quantity : 0;
        }

        @Override
        public long getPendingOutputs(AEKey what) {
            return 0;
        }

        @Override
        public ElapsedTimeTracker getElapsedTimeTracker() {
            return tracker;
        }
    }
}
