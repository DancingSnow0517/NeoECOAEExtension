package cn.dancingsnow.neoecoae.gui.ldlib.support;
import appeng.api.config.CpuSelectionMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
public final class NELDLibStateCodecs {
    private static final int MAX_STRUCTURE_TERMINAL_MATERIALS=512;
    public static void writeStructureTerminal(FriendlyByteBuf buf, NEStructureTerminalConfigState state) {
        buf.writeVarInt(state.length());
        buf.writeVarInt(state.minLength());
        buf.writeVarInt(state.maxLength());
        buf.writeVarInt(state.tier());
        buf.writeEnum(state.hostType());
        buf.writeEnum(state.operationMode());
        buf.writeBoolean(state.operationModePending());
        buf.writeBoolean(state.previewMirrored());
        buf.writeBoolean(state.previewFormed());
        buf.writeVarInt(state.previewLayer());
        buf.writeVarInt(state.previewMaterialScroll());
        buf.writeBoolean(state.linkedHost());
        buf.writeBoolean(state.formed());
        buf.writeBoolean(state.buildInProgress());
        buf.writeVarInt(state.previewMissingBlocks());
        buf.writeVarInt(state.previewConflictBlocks());
        buf.writeVarInt(state.previewReusedBlocks());
        buf.writeVarInt(state.previewRequiredItems());
        buf.writeUtf(state.previewStatusKey(), 256);
        buf.writeVarInt(state.previewStatusArg1());
        buf.writeVarInt(state.previewStatusArg2());
        List<cn.dancingsnow.neoecoae.multiblock.NEStructureTerminalUiState.BuildMaterialEntry> materials =
                state.materials();
        buf.writeVarInt(Math.min(materials.size(), MAX_STRUCTURE_TERMINAL_MATERIALS));
        int written = 0;
        for (var material : materials) {
            if (written++ >= MAX_STRUCTURE_TERMINAL_MATERIALS) {
                break;
            }
            buf.writeItem(material.item());
            buf.writeVarInt(Math.max(0, material.required()));
            buf.writeVarInt(Math.max(0, material.available()));
        }
    }

    public static NEStructureTerminalConfigState readStructureTerminal(FriendlyByteBuf buf) {
        int length = buf.readVarInt();
        int minLength = buf.readVarInt();
        int maxLength = buf.readVarInt();
        int tier = buf.readVarInt();
        var hostType = buf.readEnum(cn.dancingsnow.neoecoae.multiblock.StructureTerminalHostType.class);
        var mode = buf.readEnum(cn.dancingsnow.neoecoae.multiblock.StructureTerminalMode.class);
        boolean modePending = buf.readBoolean();
        boolean previewMirrored = buf.readBoolean();
        boolean previewFormed = buf.readBoolean();
        int previewLayer = buf.readVarInt();
        int previewMaterialScroll = buf.readVarInt();
        boolean linkedHost = buf.readBoolean();
        boolean formed = buf.readBoolean();
        boolean buildInProgress = buf.readBoolean();
        int previewMissingBlocks = buf.readVarInt();
        int previewConflictBlocks = buf.readVarInt();
        int previewReusedBlocks = buf.readVarInt();
        int previewRequiredItems = buf.readVarInt();
        String previewStatusKey = buf.readUtf(256);
        int previewStatusArg1 = buf.readVarInt();
        int previewStatusArg2 = buf.readVarInt();
        int materialCount = readBoundedNonNegativeVarInt(
                buf, MAX_STRUCTURE_TERMINAL_MATERIALS, "Structure Terminal material count");
        List<cn.dancingsnow.neoecoae.multiblock.NEStructureTerminalUiState.BuildMaterialEntry> materials =
                new ArrayList<>(materialCount);
        for (int i = 0; i < materialCount; i++) {
            materials.add(new cn.dancingsnow.neoecoae.multiblock.NEStructureTerminalUiState.BuildMaterialEntry(
                    buf.readItem(), buf.readVarInt(), buf.readVarInt()));
        }
        return new NEStructureTerminalConfigState(
                length,
                minLength,
                maxLength,
                tier,
                hostType,
                mode,
                modePending,
                previewMirrored,
                previewFormed,
                previewLayer,
                previewMaterialScroll,
                linkedHost,
                formed,
                buildInProgress,
                previewMissingBlocks,
                previewConflictBlocks,
                previewReusedBlocks,
                previewRequiredItems,
                previewStatusKey,
                previewStatusArg1,
                previewStatusArg2,
                materials);
    }

    private static int readBoundedNonNegativeVarInt(FriendlyByteBuf buf, int max, String fieldName) {
        int value = buf.readVarInt();
        if (value < 0 || value > max) {
            throw new IllegalArgumentException(fieldName + " outside protocol limit: " + value);
        }
        return value;
    }

    private static void writeBoundedUtf(FriendlyByteBuf buf, String value, int maxLength) {
        buf.writeUtf(NELDLibText.bounded(value, maxLength), maxLength);
    }


private NELDLibStateCodecs() {}
}
