package cn.dancingsnow.neoecoae.network;

import cn.dancingsnow.neoecoae.gui.ldlib.widget.NELDLibSyncedStateWidget;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.modular.WidgetUIAccess;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("neoecoae")
@PrefixGameTestTemplate(false)
public final class WidgetSyncGameTest {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void largeWidgetStreamsAndReinitializesWithoutResendingUnchangedData(GameTestHelper helper) {
        byte[] initial = new byte[100_000];
        new Random(19).nextBytes(initial);
        var source = new AtomicReference<>(initial);
        var server = new TestWidget(source);
        var client = new TestWidget(new AtomicReference<>(new byte[0]));
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        server.setGui(new ModularUI(100, 100, null, player));
        var bytesSent = new AtomicInteger();
        var lastPacket = new AtomicReference<byte[]>();
        server.setUiAccess(new WidgetUIAccess() {
            @Override
            public boolean attemptMergeStack(ItemStack stack, boolean a, boolean b) {
                return false;
            }

            @Override
            public void writeClientAction(Widget widget, int id, Consumer<FriendlyByteBuf> writer) {}

            @Override
            public void writeUpdateInfo(Widget widget, int id, Consumer<FriendlyByteBuf> writer) {
                byte[] bytes = BoundedData.encode(BoundedData.PART_BYTES + 128, writer);
                bytesSent.addAndGet(bytes.length);
                lastPacket.set(bytes);
                var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
                try {
                    client.readUpdateInfo(id, buffer);
                } finally {
                    buffer.release();
                }
            }
        });
        initialize(helper, server, client);
        helper.assertTrue(client.value().length == 0, "large initial state leaked into the UI-open packet");
        for (int tick = 1; tick <= 7; tick++) helper.runAfterDelay(tick, server::detectAndSendChanges);
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(Arrays.equals(initial, client.value()), "initial stream failed to reconstruct the state");
            byte[] changed = initial.clone();
            Arrays.fill(changed, 0, 8, (byte) 99);
            source.set(changed);
            server.urgent();
            bytesSent.set(0);
        });
        helper.runAfterDelay(9, server::detectAndSendChanges);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(Arrays.equals(source.get(), client.value()), "delta did not update the counter");
            helper.assertTrue(bytesSent.get() < 256, "counter update resent the list/NBT");
            var old = lastPacket.get();
            initialize(helper, server, client);
            var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(old));
            try {
                client.readUpdateInfo(16, buffer);
            } finally {
                buffer.release();
            }
        });
        for (int tick = 11; tick <= 17; tick++) helper.runAfterDelay(tick, server::detectAndSendChanges);
        helper.runAfterDelay(18, () -> {
            helper.assertTrue(
                    Arrays.equals(source.get(), client.value()), "reinitialization used an obsolete baseline");
            helper.succeed();
        });
    }

    private static void initialize(GameTestHelper helper, TestWidget server, TestWidget client) {
        byte[] bytes = BoundedData.encode(8192, server::writeInitialData);
        helper.assertTrue(bytes.length < 4096, "UI-open state is unbounded");
        var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            client.readInitialData(buffer);
        } finally {
            buffer.release();
        }
    }

    private static final class TestWidget extends NELDLibSyncedStateWidget<byte[]> {
        TestWidget(AtomicReference<byte[]> state) {
            super(
                    Component.empty(),
                    100,
                    100,
                    new byte[0],
                    state::get,
                    (buf, value) -> buf.writeByteArray(value),
                    buf -> buf.readByteArray(StateDelta.MAX_STATE_BYTES),
                    10);
        }

        @Override
        protected void initLdWidgets() {}

        byte[] value() {
            return currentState();
        }

        void urgent() {
            syncStateNow();
        }
    }
}
