package cn.dancingsnow.neoecoae.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.MaterialNode;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.MaterialStatus;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshotFactory;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlanTraceNode;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ECOExactMaterialTableRendererTest {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger WIDE = LONG_MAX.add(BigInteger.ONE);
    private static final BigInteger ZERO = BigInteger.ZERO;

    @Test
    void switchesToExactMaterialsWhenAnyReportAmountExceedsLongWithoutACycle() {
        var nodes = List.of(
                material(0, WIDE, ZERO, ZERO, ZERO),
                material(1, ZERO, WIDE, ZERO, ZERO),
                material(2, ZERO, ZERO, WIDE, ZERO),
                material(3, ZERO, ZERO, ZERO, WIDE));

        for (var node : nodes) {
            assertTrue(ECOExactMaterialTableRenderer.shouldUseExactMaterials(
                    PlanningStatus.SUCCESS, List.of(node), false));
        }
    }

    @Test
    void preservesNativeRowsForOrdinaryPlansAndExactRowsForCycles() {
        var nodes = List.of(material(0, LONG_MAX, ZERO, LONG_MAX, ZERO));

        assertFalse(ECOExactMaterialTableRenderer.shouldUseExactMaterials(PlanningStatus.SUCCESS, nodes, false));
        assertTrue(ECOExactMaterialTableRenderer.shouldUseExactMaterials(PlanningStatus.SUCCESS, nodes, true));
        assertTrue(ECOExactMaterialTableRenderer.shouldUseExactMaterials(
                PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, nodes, false));
    }

    @Test
    void failedEcoDiagnosticsCannotHideTheNativeFallbackMaterialList() {
        var nodes = List.of(material(0, WIDE, ZERO, WIDE, ZERO));

        for (var status : List.of(
                PlanningStatus.PARTIAL_UNSUPPORTED, PlanningStatus.UNSUPPORTED, PlanningStatus.INTERNAL_ERROR)) {
            assertFalse(ECOExactMaterialTableRenderer.shouldUseExactMaterials(status, nodes, true), status.name());
        }
    }

    @Test
    void missingMaterialsUseExactRowsOnlyWhenTheNativeCountsCannotRepresentThem() {
        var ordinary = List.of(material(0, LONG_MAX, ZERO, ZERO, LONG_MAX));
        var wide = List.of(material(0, WIDE, ZERO, ZERO, WIDE));

        assertFalse(
                ECOExactMaterialTableRenderer.shouldUseExactMaterials(PlanningStatus.MISSING_ITEMS, ordinary, true));
        assertTrue(ECOExactMaterialTableRenderer.shouldUseExactMaterials(PlanningStatus.MISSING_ITEMS, wide, false));
    }

    @Test
    void finalValidationShortagesAreIncludedWithoutTruncatingExactTraceAmounts() {
        var key = new TestKey(1, null);
        var lateMissingKey = new TestKey(1, null);
        var trace = new ECOPlanTrace();
        trace.addNode(new PlanTraceNode(
                        PlanTraceNode.Kind.GOAL,
                        key,
                        null,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        PlanTraceNode.Selection.NOT_APPLICABLE,
                        null)
                .withExact(WIDE, ZERO, ZERO, WIDE, ZERO));
        var missing = new KeyCounter();
        missing.add(key, Long.MAX_VALUE);
        missing.add(lateMissingKey, 7L);
        var plan = new CraftingPlan(
                new GenericStack(key, 1L), 0L, true, false, new KeyCounter(), new KeyCounter(), missing, Map.of());
        var result = new ECOPlanningResult(PlanningStatus.MISSING_ITEMS, plan, trace, List.of(), 0L);

        var snapshot = CraftingGraphSnapshotFactory.create(result);
        var exactMaterial = snapshot.nodes().stream()
                .filter(node -> node.key() == key)
                .findFirst()
                .orElseThrow();
        var lateMissingMaterial = snapshot.nodes().stream()
                .filter(node -> node.key() == lateMissingKey)
                .findFirst()
                .orElseThrow();

        assertFalse(result.shouldUseNativeFallback());
        assertTrue(ECOExactMaterialTableRenderer.shouldUseExactMaterials(result.status(), snapshot.nodes(), false));
        assertEquals(WIDE, exactMaterial.requestedBigInteger());
        assertEquals(WIDE, exactMaterial.missingBigInteger());
        assertEquals(Long.MAX_VALUE, exactMaterial.missing());
        assertEquals(BigInteger.valueOf(7L), lateMissingMaterial.missingBigInteger());
        assertEquals(MaterialStatus.MISSING, lateMissingMaterial.status());
        assertEquals(
                2, ECOExactMaterialTableRenderer.sortMaterials(snapshot.nodes()).size());
    }

    @Test
    void missingSnapshotCannotReplaceTheNativeMaterialListWithAnEmptyTable() {
        assertFalse(ECOExactMaterialTableRenderer.shouldUseExactMaterials(null, List.of(), false));
        assertFalse(ECOExactMaterialTableRenderer.shouldUseExactMaterials(
                PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, List.of(), true));
    }

    @Test
    void ordersMissingCraftedAndStoredAmountsUsingTheirExactValues() {
        var smallerMissing = material(0, ZERO, ZERO, ZERO, WIDE);
        var largerMissing = material(1, ZERO, ZERO, ZERO, WIDE.add(BigInteger.ONE));
        var smallerCraft = material(2, ZERO, ZERO, WIDE, ZERO);
        var largerCraft = material(3, ZERO, ZERO, WIDE.add(BigInteger.ONE), ZERO);
        var smallerStored = material(4, ZERO, WIDE, ZERO, ZERO);
        var largerStored = material(5, ZERO, WIDE.add(BigInteger.ONE), ZERO, ZERO);
        var empty = material(6, ZERO, ZERO, ZERO, ZERO);

        assertEquals(
                List.of(largerMissing, smallerMissing, largerCraft, smallerCraft, largerStored, smallerStored),
                ECOExactMaterialTableRenderer.sortMaterials(List.of(
                        smallerStored, empty, smallerCraft, smallerMissing, largerStored, largerCraft, largerMissing)));
    }

    @Test
    void itemTooltipRetainsAllDigitsBeyondLongAndDoublePrecision() {
        var key = new TestKey(1, null);
        var amount = new BigInteger("9223372036854775808123456789");

        assertEquals(
                "9,223,372,036,854,775,808,123,456,789",
                ECOExactMaterialTableRenderer.formatAmount(key, amount, AmountFormat.FULL));
        assertEquals("9.2R", ECOExactMaterialTableRenderer.formatAmount(key, amount, AmountFormat.SLOT));
        assertEquals("9R", ECOExactMaterialTableRenderer.formatAmount(key, amount, AmountFormat.SLOT_LARGE_FONT));
        assertEquals(
                "9,223,372,036,854,775,808", ECOExactMaterialTableRenderer.formatAmount(key, WIDE, AmountFormat.FULL));
    }

    @Test
    void fluidAmountsUseTheKeyUnitWithoutLosingTheRemainder() {
        var key = new TestKey(1000, "B");
        var amount = new BigInteger("9223372036854775808123");

        assertEquals(
                "9,223,372,036,854,775,808.123 B",
                ECOExactMaterialTableRenderer.formatAmount(key, amount, AmountFormat.FULL));
        assertEquals("9.2E", ECOExactMaterialTableRenderer.formatAmount(key, amount, AmountFormat.SLOT));
    }

    @Test
    void customResourceUnitsKeepMoreThanThreeFractionalDigits() {
        var key = new TestKey(1_000_000, "u");
        var amount = new BigInteger("9223372036854775808123456");

        assertEquals(
                "9,223,372,036,854,775,808.123456 u",
                ECOExactMaterialTableRenderer.formatAmount(key, amount, AmountFormat.FULL));
    }

    @Test
    void representableAmountsStillUseTheKeyFormatter() {
        var key = new TestKey(1000, "B");

        for (var format : AmountFormat.values()) {
            assertEquals(
                    key.formatAmount(Long.MAX_VALUE, format),
                    ECOExactMaterialTableRenderer.formatAmount(key, LONG_MAX, format));
            assertEquals(
                    key.formatAmount(123L, format),
                    ECOExactMaterialTableRenderer.formatAmount(key, BigInteger.valueOf(123L), format));
        }
    }

    private static MaterialNode material(
            int id, BigInteger requested, BigInteger stored, BigInteger craft, BigInteger missing) {
        return new MaterialNode(
                id,
                new TestKey(1, null),
                requested.min(LONG_MAX).longValueExact(),
                stored.min(LONG_MAX).longValueExact(),
                craft.min(LONG_MAX).longValueExact(),
                missing.min(LONG_MAX).longValueExact(),
                missing.signum() > 0 ? MaterialStatus.MISSING : MaterialStatus.CRAFTING,
                requested.toString(),
                stored.toString(),
                craft.toString(),
                missing.toString(),
                "0",
                "0");
    }

    /** Uses a resource type without requiring the Forge registries or a client window. */
    private static final class TestKey extends AEKey {
        private final AEKeyType type;

        private TestKey(int amountPerUnit, String unit) {
            type =
                    new AEKeyType(
                            ResourceLocation.fromNamespaceAndPath("test", "report"), TestKey.class, Component.empty()) {
                        @Override
                        public int getAmountPerUnit() {
                            return amountPerUnit;
                        }

                        @Override
                        public String getUnitSymbol() {
                            return unit;
                        }

                        @Override
                        public AEKey readFromPacket(FriendlyByteBuf input) {
                            throw new UnsupportedOperationException();
                        }

                        @Override
                        public AEKey loadKeyFromTag(CompoundTag input) {
                            throw new UnsupportedOperationException();
                        }
                    };
        }

        @Override
        public String formatAmount(long amount, AmountFormat format) {
            return "native:" + amount + ":" + format;
        }

        @Override
        public AEKeyType getType() {
            return type;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public CompoundTag toTag() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getPrimaryKey() {
            return this;
        }

        @Override
        public ResourceLocation getId() {
            return type.getId();
        }

        @Override
        public void writeToPacket(FriendlyByteBuf buffer) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal("test");
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}
    }
}
