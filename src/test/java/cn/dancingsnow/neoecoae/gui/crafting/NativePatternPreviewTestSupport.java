package cn.dancingsnow.neoecoae.gui.crafting;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

final class NativePatternPreviewTestSupport {
    private NativePatternPreviewTestSupport() {}

    static PatternPreviewNativeSlots.Target[] acknowledge(CraftingPatternPreviewState state) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            state.nativeSlots().writeView(buffer);
            long generation = buffer.readLong();
            var targets = new PatternPreviewNativeSlots.Target[CraftingInterfaceUI.PREVIEW_BUFFER_SLOTS];
            for (int i = 0; i < targets.length; i++) {
                if (buffer.readBoolean()) targets[i] = new PatternPreviewNativeSlots.Target(
                    buffer.readLong(), buffer.readVarInt(), buffer.readInt(), buffer.readBoolean());
            }
            state.nativeSlots().acknowledge(generation);
            return targets;
        } finally { buffer.release(); }
    }
}
