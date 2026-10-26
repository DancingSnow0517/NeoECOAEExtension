package cn.dancingsnow.neoecoae.api.me.menu;

import appeng.menu.guisync.PacketWritable;
import appeng.menu.me.crafting.CraftingStatusMenu.CraftingCpuListEntry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

/** A bounded snapshot of AE2's CPU list, plus the selected CPU even when it is off screen. */
public record ECOCpuListPage(int total, int offset, long sequence, List<CraftingCpuListEntry> cpus,
        @Nullable CraftingCpuListEntry selected) implements PacketWritable {
    public static final int ROWS = 6;
    public static final ECOCpuListPage EMPTY = new ECOCpuListPage(0, 0, 0, List.of(), null);

    public ECOCpuListPage {
        cpus = List.copyOf(cpus);
        if (total < 0 || offset < 0 || offset > Math.max(0, total - ROWS)
                || cpus.size() != Math.min(ROWS, total - offset) || sequence < 0) {
            throw new IllegalArgumentException("Invalid CPU page");
        }
    }

    public ECOCpuListPage(RegistryFriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), readEntries(buf),
            buf.readBoolean() ? CraftingCpuListEntry.readFromPacket(buf) : null);
    }

    public static ECOCpuListPage sample(List<CraftingCpuListEntry> sorted, int offset, long sequence, int selectedSerial) {
        int first = Math.clamp(offset, 0, Math.max(0, sorted.size() - ROWS));
        var selected = sorted.stream().filter(cpu -> cpu.serial() == selectedSerial).findFirst().orElse(null);
        return new ECOCpuListPage(sorted.size(), first, sequence,
            sorted.subList(first, Math.min(sorted.size(), first + ROWS)), selected);
    }

    private static List<CraftingCpuListEntry> readEntries(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > ROWS) throw new IllegalArgumentException("CPU page exceeds visible rows");
        var cpus = new ArrayList<CraftingCpuListEntry>(count);
        for (int i = 0; i < count; i++) cpus.add(CraftingCpuListEntry.readFromPacket(buf));
        return cpus;
    }

    @Override public void writeToPacket(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(total);
        buf.writeVarInt(offset);
        buf.writeVarLong(sequence);
        buf.writeVarInt(cpus.size());
        for (var cpu : cpus) cpu.writeToPacket(buf);
        buf.writeBoolean(selected != null);
        if (selected != null) selected.writeToPacket(buf);
    }
}
