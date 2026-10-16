package cn.dancingsnow.neoecoae.core.planner.acyclic;

import static org.junit.jupiter.api.Assertions.*;

import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.core.concurrent.Cancellation;
import cn.dancingsnow.neoecoae.core.planner.PatternView;
import cn.dancingsnow.neoecoae.core.planner.PlannerCounter;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;

class AcyclicPassTest {
    @Test void specialProcurementSharesStockAndKeepsHugeCountsAndSurplusExact() throws Exception {
        var f = new Fixture();
        f.recipe("goal", 1, List.of(new Input("tool", 1, false)), Map.of("goal", 1L));
        f.recipe("tool", 2, List.of(new Input("raw", 3, false)), Map.of("tool", 2L, "scrap", 1L));
        var requested = PlannerAmount.of(BigInteger.ONE.shiftLeft(100).add(BigInteger.ONE));
        var times = requested.subtract(1).ceilDiv(PlannerAmount.of(2));
        f.stored.add("tool", 1);
        f.stored.add("raw", times.multiply(3));
        f.procurement(Set.of()).resolve(f.recipes.get("goal"), "tool", requested, false);
        assertEquals(times, f.firings.get("tool"));
        assertEquals(PlannerAmount.ONE, f.used.get("tool"));
        assertEquals(times.multiply(3), f.used.get("raw"));
        assertEquals(times, f.crafted.get("scrap"));
        assertTrue(f.missing.isEmpty());
        assertEquals(Set.of("goal"), f.parents.get("tool"));
    }

    @Test void specialFeedbackReportsConcreteDeficitOnlyForSupportedNonGrowingCycles() throws Exception {
        for (boolean unsafe : new boolean[] {false, true}) {
            var f = new Fixture();
            f.recipe("goal", 1, List.of(new Input("tool", 1, false)), Map.of("goal", 1L));
            f.recipe("tool", 1, List.of(new Input("part", 1, false)), Map.of("tool", 1L));
            f.recipe("part", 1, List.of(new Input("tool", 1, false)), Map.of("part", 1L));
            f.procurement(unsafe ? Set.of("part") : Set.of()).resolve(f.recipes.get("goal"), "tool", PlannerAmount.of(4), false);
            assertEquals(unsafe ? PlannerAmount.ZERO : PlannerAmount.of(4), f.missing.get("tool"));
            assertEquals(unsafe ? Set.of("tool") : Set.of(), f.unsupported);
            assertEquals(Map.of("tool", PlannerAmount.of(4), "part", PlannerAmount.of(4)), f.firings);
        }
    }

    @Test void specialProcurementRetainsEmittedVersusUnsupportedProducerDistinction() throws Exception {
        var f = new Fixture();
        f.recipe("goal", 1, List.of(), Map.of("goal", 1L));
        f.emittable.add("external");
        f.unavailableProducers.add("foreign");
        var resolver = f.procurement(Set.of());
        resolver.resolve(f.recipes.get("goal"), "external", PlannerAmount.of(7), false);
        resolver.resolve(f.recipes.get("goal"), "absent", PlannerAmount.of(5), false);
        resolver.resolve(f.recipes.get("goal"), "foreign", PlannerAmount.of(3), false);
        assertEquals(PlannerAmount.of(7), f.emitted.get("external"));
        assertEquals(PlannerAmount.of(5), f.missing.get("absent"));
        assertEquals(Set.of("foreign"), f.unsupported);
        assertTrue(f.firings.isEmpty());
    }

    @Test void changedProducerDependenciesAreOrderedAndStockLeavesCanBreakFeedback() throws Exception {
        var f = new Fixture();
        f.recipe("goal", 1, List.of(new Input("part", 1, false)), Map.of("goal", 1L));
        f.recipe("part", 1, List.of(new Input("raw", 1, false)), Map.of("part", 1L));
        var allowed = Set.of("goal", "part", "raw");
        assertEquals(List.of("goal", "part", "raw"), SelectedRoute.order(allowed, Set.of("goal"), Set.of(),
            f::selected, p -> false, (p, slot) -> false, Cancellation.NONE));
        f.recipe("part", 1, List.of(new Input("goal", 1, false)), Map.of("part", 1L));
        assertNull(SelectedRoute.order(allowed, Set.of("goal"), Set.of(), f::selected,
            p -> false, (p, slot) -> false, Cancellation.NONE));
        assertEquals(List.of("goal", "part"), SelectedRoute.order(allowed, Set.of("goal"), Set.of("part"),
            f::selected, p -> false, (p, slot) -> false, Cancellation.NONE));
    }

    @Test void inventorySnapshotsIsolateCandidateRetriesAndUnboundedOwnership() {
        var source = new LinkedHashMap<String, PlannerAmount>();
        source.put("stock", PlannerAmount.of(8));
        var unbounded = new HashSet<>(Set.of("infinite"));
        var snapshot = new InventorySnapshot<>(source, unbounded);
        source.put("stock", PlannerAmount.ONE);
        unbounded.clear();
        var first = new PlannerCounter<String>();
        snapshot.initialize(first);
        first.remove("stock", PlannerAmount.of(5));
        var retry = new PlannerCounter<String>();
        snapshot.initialize(retry);
        assertEquals(PlannerAmount.of(8), retry.get("stock"));
        assertTrue(retry.isUnbounded("infinite"));
        assertEquals(PlannerAmount.of(3), first.get("stock"));
    }

    @Test void sharedOutputsSatisfyIndependentDemandsWithoutActivatingTheirOwnProducer() throws Exception {
        var f = new Fixture();
        f.recipe("goal", 1, List.of(new Input("a", 1, false), new Input("b", 2, false)), Map.of("goal", 1L));
        f.recipe("a", 1, List.of(new Input("raw", 3, false)), Map.of("a", 1L, "b", 2L));
        f.recipe("b", 1, List.of(new Input("unavailable", 1, false)), Map.of("b", 1L));
        f.stored.add("raw", 3);
        f.run(PlannerAmount.ONE, true, Set.of());
        assertEquals(Map.of("goal", PlannerAmount.ONE, "a", PlannerAmount.ONE), f.firings);
        assertEquals(PlannerAmount.of(3), f.used.get("raw"));
        assertTrue(f.missing.isEmpty());
        assertEquals(PlannerAmount.ZERO, f.crafted.get("b"));
    }

    @Test void exactCountsPromoteWhileReusableStockIsReservedOnlyOnce() throws Exception {
        var f = new Fixture();
        f.recipe("goal", 2, List.of(new Input("raw", 3, false), new Input("tool", 1, true)), Map.of("goal", 2L));
        var amount = PlannerAmount.of(BigInteger.ONE.shiftLeft(100).add(BigInteger.ONE));
        f.stored.add("raw", amount.multiply(3));
        f.stored.add("tool", 1);
        f.run(amount, true, Set.of());
        var times = amount.ceilDiv(PlannerAmount.of(2));
        assertEquals(times, f.firings.get("goal"));
        assertEquals(times.multiply(3), f.used.get("raw"));
        assertEquals(PlannerAmount.ONE, f.used.get("tool"));
        assertEquals(PlannerAmount.ONE, f.crafted.get("goal"));
        assertTrue(f.bytes.compareTo(PlannerAmount.of(Long.MAX_VALUE)) > 0);
    }

    @Test void additionalGoalStockDoesNotReplaceNewCraftsButBoundaryGoalsCanConsumeIt() throws Exception {
        for (boolean additional : new boolean[] {false, true}) {
            var f = new Fixture();
            f.recipe("goal", 1, List.of(), Map.of("goal", 1L));
            f.stored.add("goal", 8);
            f.run(PlannerAmount.of(3), additional, Set.of());
            assertEquals(additional ? PlannerAmount.of(3) : null, f.firings.get("goal"));
            assertEquals(additional ? PlannerAmount.ZERO : PlannerAmount.of(3), f.used.get("goal"));
        }
    }

    @Test void emittedAndDeferredSuppliersDoNotInventFiringsAndStockLeavesReopen() throws Exception {
        var f = new Fixture();
        f.recipe("goal", 1, List.of(new Input("emit", 2, false), new Input("cycle", 3, false),
            new Input("leaf", 4, false), new Input("missing", 5, false)), Map.of("goal", 1L));
        f.recipe("cycle", 1, List.of(), Map.of("cycle", 1L));
        f.recipe("leaf", 1, List.of(), Map.of("leaf", 1L));
        f.emittable.add("emit");
        f.deferred.add("cycle");
        f.stored.add("leaf", 1);
        f.run(PlannerAmount.ONE, true, Set.of("leaf"));
        assertEquals(PlannerAmount.of(2), f.emitted.get("emit"));
        assertEquals(PlannerAmount.of(3), f.deferredAmounts.get("cycle"));
        assertEquals(Set.of("leaf"), f.unsupported);
        assertEquals(PlannerAmount.of(5), f.missing.get("missing"));
        assertEquals(Map.of("goal", PlannerAmount.ONE), f.firings);
    }

    @Test void alternativeStockConsumesMatchingPhysicalKeysAndPreservesUnboundedStock() {
        var stock = new PlannerCounter<String>();
        var used = new PlannerCounter<String>();
        stock.add("red", 2);
        stock.add("blue", 4);
        stock.add("other", 10);
        var attributed = new LinkedHashMap<String, PlannerAmount>();
        assertEquals(PlannerAmount.of(5), StockConsumption.consume(stock, used, "wanted", PlannerAmount.of(5),
            true, key -> !key.equals("other"), attributed::put));
        assertEquals(Map.of("red", PlannerAmount.of(2), "blue", PlannerAmount.of(3)), attributed);
        assertEquals(PlannerAmount.ONE, stock.get("blue"));
        assertEquals(PlannerAmount.of(10), stock.get("other"));
        stock.setUnbounded("infinite");
        var huge = PlannerAmount.of(BigInteger.ONE.shiftLeft(100));
        assertEquals(huge, StockConsumption.consume(stock, used, "infinite", huge, false, key -> false, (k, v) -> {}));
        assertTrue(stock.isUnbounded("infinite"));
        assertEquals(huge, used.get("infinite"));
    }

    @Test void fallbackAdvancesNearestAncestorAndTerminatesAcrossParentCycles() {
        var choices = new HashMap<String, Integer>();
        var parents = Map.of("missing", Set.of("near"), "near", Set.of("far"), "far", Set.of("near"));
        assertTrue(CandidateFallback.advance(List.of("missing"), List.of(), parents, k -> k.equals("near") ? 2 : 3, choices));
        assertEquals(Map.of("near", 1), choices);
        assertTrue(CandidateFallback.advance(List.of("missing"), List.of(), parents, k -> k.equals("near") ? 2 : 3, choices));
        assertEquals(Map.of("near", 1, "far", 1), choices);
        choices.put("far", 2);
        assertFalse(CandidateFallback.advance(List.of("missing"), List.of(), parents, k -> k.equals("near") ? 2 : 3, choices));
    }

    record Input(String key, long amount, boolean reusable) {}
    record Recipe(int id, String producedKey, long output, List<Input> inputs,
            List<Map.Entry<String, Long>> outputs) implements PatternView<String> {
        @Override public Object identity() { return producedKey; }
        @Override public int inputCount() { return inputs.size(); }
        @Override public String inputKey(int i) { return inputs.get(i).key(); }
        @Override public PlannerAmount inputAmount(int i) { return PlannerAmount.of(inputs.get(i).amount()); }
        @Override public boolean inputFastSupported(int i) { return true; }
        @Override public boolean inputIgnoresComponents(int i) { return false; }
        @Override public int grossOutputCount() { return outputs.size(); }
        @Override public String grossOutputKey(int i) { return outputs.get(i).getKey(); }
        @Override public long grossOutputAmount(int i) { return outputs.get(i).getValue(); }
        @Override public boolean fastSupported() { return true; }
        @Override public boolean completeForStaticPlanning() { return true; }
        @Override public boolean special() { return false; }
    }

    static final class Fixture implements AcyclicPass.State<String, Recipe>, AcyclicPass.Domain<String, Recipe> {
        final PlannerCounter<String> stored = new PlannerCounter<>(), used = new PlannerCounter<>(),
            crafted = new PlannerCounter<>(), emitted = new PlannerCounter<>(), missing = new PlannerCounter<>();
        final Map<String, PlannerAmount> demand = new LinkedHashMap<>(), pending = new HashMap<>(),
            firings = new LinkedHashMap<>(), deferredAmounts = new HashMap<>();
        final Map<String, Recipe> recipes = new LinkedHashMap<>(), selected = new HashMap<>();
        final Map<String, Set<String>> parents = new HashMap<>();
        final Set<String> unsupported = new LinkedHashSet<>(), emittable = new HashSet<>(), deferred = new HashSet<>(),
            unavailableProducers = new HashSet<>();
        PlannerAmount bytes = PlannerAmount.ZERO;
        void recipe(String key, long count, List<Input> inputs, Map<String, Long> outputs) {
            recipes.put(key, new Recipe(recipes.size(), key, count, inputs, List.copyOf(outputs.entrySet())));
        }
        void run(PlannerAmount amount, boolean additional, Set<String> leaves) throws Exception {
            AcyclicPass.run(this, this, List.copyOf(recipes.keySet()), Map.of("goal", amount), additional, leaves, Cancellation.NONE);
        }
        SpecialDemandResolver<String, Recipe, String> procurement(Set<String> unsafe) {
            return new SpecialDemandResolver<>(this, new SpecialDemandResolver.Domain<>() {
                @Override public String register(Recipe owner, String key, PlannerAmount amount) {
                    pending.merge(key, amount, PlannerAmount::add); return key;
                }
                @Override public void inputProducer(String key, Recipe owner) {}
                @Override public PlannerAmount stock(Recipe owner, String demand, String key, PlannerAmount amount, boolean ignore) {
                    return consumeRequestedStock(key, amount);
                }
                @Override public void consumeCrafted(String demand, String key, PlannerAmount amount) {
                    Fixture.this.consumeCrafted(key, amount);
                }
                @Override public Recipe selected(String key) { return Fixture.this.selected(key); }
                @Override public boolean hasProducer(String key) { return Fixture.this.hasProducer(key); }
                @Override public boolean emittable(String key) { return Fixture.this.emittable(key); }
                @Override public boolean cycleSafe(Recipe p) { return !unsafe.contains(p.producedKey()); }
                @Override public void emitted(String demand, String key, PlannerAmount amount) { debit(key, amount); }
                @Override public void supplied(String demand, String key, Recipe p, PlannerAmount amount) { debit(key, amount); }
                @Override public PlannerAmount outputPerPattern(Recipe p) { return Fixture.this.outputPerPattern(p); }
                @Override public int outputCount(Recipe p) { return Fixture.this.outputCount(p); }
                @Override public String outputKey(Recipe p, int i) { return Fixture.this.outputKey(p, i); }
                @Override public long outputAmount(Recipe p, int i) { return Fixture.this.outputAmount(p, i); }
                @Override public void special(Recipe p, PlannerAmount times) {}
                @Override public boolean excludedInput(Recipe p, int slot) { return false; }
            });
        }
        @Override public PlannerCounter<String> stored() { return stored; }
        @Override public PlannerCounter<String> emitted() { return emitted; }
        @Override public PlannerCounter<String> missing() { return missing; }
        @Override public Map<String, PlannerAmount> demand() { return demand; }
        @Override public Map<String, Recipe> selected() { return selected; }
        @Override public Map<String, Set<String>> parents() { return parents; }
        @Override public Set<String> unsupported() { return unsupported; }
        @Override public void addBytes(PlannerAmount value) { bytes = bytes.add(value); }
        @Override public void recordPattern(Recipe p, PlannerAmount times) { firings.merge(p.producedKey(), times, PlannerAmount::add); }
        @Override public PlannerAmount craftedAmount(String key) { return crafted.get(key); }
        @Override public void consumeCrafted(String key, PlannerAmount amount) { crafted.remove(key, amount); debit(key, amount); }
        @Override public void creditCrafted(String key, Recipe p, PlannerAmount amount) { crafted.add(key, amount); }
        void debit(String key, PlannerAmount amount) { pending.put(key, pending(key).subtract(amount)); }
        @Override public String goal() { return "goal"; }
        @Override public Recipe selected(String key) { return recipes.get(key); }
        @Override public boolean hasProducer(String key) { return recipes.containsKey(key) || unavailableProducers.contains(key); }
        @Override public boolean emittable(String key) { return emittable.contains(key); }
        @Override public boolean deferred(Recipe p) { return deferred.contains(p.producedKey()); }
        @Override public long amountPerByte(String key) { return 8; }
        @Override public PlannerAmount outputPerPattern(Recipe p) { return PlannerAmount.of(p.output()); }
        @Override public int outputCount(Recipe p) { return p.outputs().size(); }
        @Override public String outputKey(Recipe p, int i) { return p.grossOutputKey(i); }
        @Override public long outputAmount(Recipe p, int i) { return p.grossOutputAmount(i); }
        @Override public boolean excludedInput(Recipe p, int slot) { return false; }
        @Override public boolean reusableInput(Recipe p, int slot) { return p.inputs().get(slot).reusable(); }
        @Override public AcyclicPass.Queue<String> queue(List<String> route, Map<String, Recipe> selected, Set<String> leaves) {
            return new AcyclicPass.Queue<>() {
                final LinkedHashSet<String> keys = new LinkedHashSet<>();
                @Override public void add(String key) { keys.add(key); }
                @Override public boolean isEmpty() { return keys.isEmpty(); }
                @Override public String poll() { var iterator = keys.iterator(); String key = iterator.next(); iterator.remove(); return key; }
            };
        }
        @Override public void registerGoal(String key, PlannerAmount amount) { pending.merge(key, amount, PlannerAmount::add); }
        @Override public PlannerAmount pending(String key) { return pending.getOrDefault(key, PlannerAmount.ZERO); }
        @Override public PlannerAmount consumeRequestedStock(String key, PlannerAmount requested) {
            return StockConsumption.consume(stored, used, key, requested, false, k -> false, (k, amount) -> debit(key, amount));
        }
        @Override public void emitted(String key, PlannerAmount amount) { debit(key, amount); }
        @Override public void supplied(String key, Recipe p, PlannerAmount amount, boolean defer) {
            debit(key, amount); if (defer) deferredAmounts.merge(key, amount, PlannerAmount::add);
        }
        @Override public void resolveSpecial(Recipe p, PlannerAmount times) {}
        @Override public PlannerAmount registerInput(Recipe p, int slot, PlannerAmount required) {
            pending.merge(p.inputKey(slot), required, PlannerAmount::add); return PlannerAmount.ZERO;
        }
        @Override public void inputProducer(String key, Recipe p) {}
    }
}
