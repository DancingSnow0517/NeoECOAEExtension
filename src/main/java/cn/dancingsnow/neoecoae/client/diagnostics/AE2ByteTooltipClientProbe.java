package cn.dancingsnow.neoecoae.client.diagnostics;

import appeng.api.config.CpuSelectionMode;
import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.gui.task.ComputationTaskCards;
import cn.dancingsnow.neoecoae.gui.task.ComputationTaskEntry;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in regression against the actual AE2 classes transformed by the Forge launcher. */
final class AE2ByteTooltipClientProbe {
    private static final Logger LOG = LoggerFactory.getLogger("neoecoae.ae2ByteTooltipSmoke");

    private AE2ByteTooltipClientProbe() {}

    static void verify() {
        long[] expectedUnits = {1L << 10, 1L << 20, 1L << 30, 1L << 40, 1L << 50, 1L << 60};
        if (!Arrays.equals(expectedUnits, Tooltips.BYTE_NUMS)) {
            throw new IllegalStateException("Unexpected AE2 byte units: " + Arrays.toString(Tooltips.BYTE_NUMS));
        }
        String version = ModList.get().getModContainerById("ae2").orElseThrow().getModInfo().getVersion().toString();
        boolean thunderbolt = ModList.get().isLoaded("thunderbolt");
        long oldCrashBoundary = 1000L << 30;
        var cases = List.of(
            new ByteCase(0L, "0", ""), new ByteCase(1L, "1", ""), new ByteCase(1023L, "1023", ""),
            new ByteCase(1L << 10, "1", "k"), new ByteCase(1L << 20, "1", "M"),
            new ByteCase(1L << 30, "1", "G"), new ByteCase(oldCrashBoundary - 1, "1000", "G"),
            new ByteCase(oldCrashBoundary, "0.977", "T"), new ByteCase(oldCrashBoundary + 1, "0.977", "T"),
            new ByteCase(1L << 40, "1", "T"), new ByteCase(1L << 50, "1", "P"),
            new ByteCase(1L << 60, "1", "E"), new ByteCase(Long.MAX_VALUE - 1, "8", "E"),
            new ByteCase(Long.MAX_VALUE, thunderbolt ? "∞" : "8", thunderbolt ? "" : "E"));
        Locale previousLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ROOT);
            for (var test : cases) {
                long bytes = test.bytes();
                var amount = Tooltips.getByteAmount(bytes);
                var expected = new Tooltips.Amount(test.digit(), test.unit());
                if (!expected.equals(amount)) {
                    throw new IllegalStateException("AE2 byte amount " + bytes + ": expected " + expected + ", got " + amount);
                }
                String tooltip = Tooltips.ofBytes(bytes).getString();
                if (tooltip.isBlank()) throw new IllegalStateException("Empty AE2 byte tooltip for " + bytes);
                LOG.info("AE2_BYTE_TOOLTIP amount={} digit={} unit={} tooltip={}",
                    bytes, amount.digit(), amount.unit(), tooltip);
            }
            var entry = new ComputationTaskEntry("byte-tooltip-regression", ItemStack.EMPTY,
                0L, 1L, 1L, 1L, ComputationTaskEntry.Status.QUEUED, 1,
                Component.literal("ECO byte tooltip regression"), Long.MAX_VALUE, 1,
                CpuSelectionMode.ANY, 0.0F, 0L);
            var lines = ComputationTaskCards.tooltipLines(entry).stream().map(Component::getString).toList();
            String maximumCapacity = thunderbolt ? "∞" : "8E";
            if (lines.stream().noneMatch(line -> line.contains(maximumCapacity))) {
                throw new IllegalStateException("ECO task tooltip did not include the full CPU capacity: " + lines);
            }
            LOG.info("AE2_BYTE_TOOLTIP ECO task tooltip={}", lines);
            LOG.info("AE2_BYTE_TOOLTIP PASS version={} cases={} taskTooltip=true thunderbolt={}",
                version, cases.size(), thunderbolt);
        } finally {
            Locale.setDefault(previousLocale);
        }
    }

    private record ByteCase(long bytes, String digit, String unit) {}
}
