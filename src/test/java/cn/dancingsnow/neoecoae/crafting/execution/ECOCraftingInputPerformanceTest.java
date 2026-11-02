package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "crafting.performance", matches = "true")
class ECOCraftingInputPerformanceTest {
    private static volatile long consumed;

    @Test void compareCopiedInventoryAndReusableOverlay() throws Exception {
        InventoryTestBootstrap.initialize();
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        var rows = new ArrayList<String>();
        rows.add("mode,unrelated_keys,round,operations,ns_per_operation,bytes_per_operation");
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var water = AEItemKey.of(Items.WATER_BUCKET);
        var bucket = AEItemKey.of(Items.BUCKET);
        var output = AEItemKey.of(Items.QUARTZ);
        var pattern = new TestPattern(new IPatternDetails.IInput[] {
                new TestInput(new GenericStack[] {new GenericStack(iron, 2)}, null),
                new TestInput(new GenericStack[] {new GenericStack(water, 1)}, bucket),
                new TestInput(new GenericStack[] {new GenericStack(iron, 1)}, null)},
                List.of(new GenericStack(output, 1)));

        for (int count : new int[] {1, 1000, 10000}) {
            var inventory = new ListCraftingInventory(ignored -> {});
            inventory.insert(iron, 64, Actionable.MODULATE);
            inventory.insert(water, 64, Actionable.MODULATE);
            for (int index = 0; index < count; index++) {
                var stack = new ItemStack(Items.STONE);
                stack.set(DataComponents.CUSTOM_NAME, Component.literal("unrelated_" + index));
                inventory.insert(AEItemKey.of(stack), 100, Actionable.MODULATE);
            }
            var preview = new ECOCraftingInputPreview(inventory);
            var buffer = new ECOCraftingInputBuffer();
            var remainders = new ECOCraftingRemainderCache();
            int operations = count < 1000 ? 4000 : 400;
            Runnable copied = () -> {
                long total = 0;
                for (int operation = 0; operation < operations; operation++) {
                    var snapshot = new ListCraftingInventory(ignored -> {});
                    snapshot.list.addAll(inventory.list);
                    var inputs = CraftingCpuHelper.extractPatternInputs(pattern, snapshot, null,
                            new KeyCounter(), new KeyCounter());
                    total += inputs[0].get(iron) + inputs[1].get(water) + inputs[2].get(iron);
                }
                consumed = total;
            };
            Runnable overlay = () -> {
                long total = 0;
                for (int operation = 0; operation < operations; operation++) {
                    preview.reset();
                    var inputs = ECOCraftingInputResolver.extractPatternInputsFromDisposablePreview(
                            pattern, preview, null, buffer, remainders);
                    total += inputs[0].get(iron) + inputs[1].get(water) + inputs[2].get(iron);
                }
                consumed = total;
            };
            for (int warmup = 0; warmup < 5; warmup++) {
                copied.run();
                overlay.run();
            }
            for (int round = 0; round < 7; round++) {
                if ((round & 1) == 0) {
                    measure(rows, bean, "copied_inventory", count, round, operations, copied);
                    measure(rows, bean, "reusable_overlay", count, round, operations, overlay);
                } else {
                    measure(rows, bean, "reusable_overlay", count, round, operations, overlay);
                    measure(rows, bean, "copied_inventory", count, round, operations, copied);
                }
                assertEquals(operations * 4L, consumed);
            }
            assertEquals(64, inventory.list.get(iron));
            assertEquals(64, inventory.list.get(water));
            assertEquals(1, buffer.outputs().get(output));
            assertEquals(1, buffer.remainders().get(bucket));
        }
        var report = Path.of("build/reports/crafting-input-performance.csv");
        Files.createDirectories(report.getParent());
        Files.write(report, rows);
    }

    private static void measure(List<String> rows, com.sun.management.ThreadMXBean bean, String mode,
            int count, int round, int operations, Runnable action) {
        long thread = Thread.currentThread().threadId();
        long allocated = bean.getThreadAllocatedBytes(thread);
        long started = System.nanoTime();
        action.run();
        long elapsed = System.nanoTime() - started;
        allocated = bean.getThreadAllocatedBytes(thread) - allocated;
        rows.add(mode + "," + count + "," + round + "," + operations + ","
                + (double) elapsed / operations + "," + (double) allocated / operations);
    }

    private record TestInput(GenericStack[] possibleInputs, AEKey remainder) implements IPatternDetails.IInput {
        @Override public GenericStack[] getPossibleInputs() { return possibleInputs; }
        @Override public long getMultiplier() { return 1L; }
        @Override public boolean isValid(AEKey input, Level level) { return true; }
        @Override public AEKey getRemainingKey(AEKey template) { return remainder; }
    }

    private record TestPattern(IPatternDetails.IInput[] inputs, List<GenericStack> outputs) implements IPatternDetails {
        @Override public AEItemKey getDefinition() { return AEItemKey.of(Items.PAPER); }
        @Override public IPatternDetails.IInput[] getInputs() { return inputs; }
        @Override public List<GenericStack> getOutputs() { return outputs; }
    }
}
