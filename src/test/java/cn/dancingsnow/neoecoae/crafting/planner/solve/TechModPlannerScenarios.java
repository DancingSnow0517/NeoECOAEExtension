package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Quantitative AE2 contract fixtures. Named vanilla keys stand in for mod registry entries without global registrations. */
final class TechModPlannerScenarios {
    record Recipe(String name, Map<AEKey, Long> inputs, Map<AEKey, Long> outputs,
                  Map<AEKey, Long> returned, IPatternDetails pattern) {
        Map<AEKey, Long> grossOutputs() {
            if (returned.isEmpty()) return outputs;
            var result = new LinkedHashMap<>(outputs);
            returned.forEach((key, amount) -> result.merge(key, amount, Math::addExact));
            return result;
        }
    }

    /** An independently specified sequence, expanded one physical firing at a time by the fixture oracle. */
    record Witness(long repetitions, List<Integer> recipes) {}

    record Scenario(String id, String title, String source, AEKey goal, long requested,
                    Map<AEKey, String> labels, Map<AEKey, Long> initial, List<Recipe> recipes,
                    List<Witness> witness, boolean feasible) {
        @Override public String toString() { return id; }

        KeyCounter inventory() {
            var counter = new KeyCounter();
            initial.forEach(counter::add);
            return counter;
        }

        ICraftingService service() {
            Map<AEKey, List<IPatternDetails>> index = new LinkedHashMap<>();
            for (Recipe recipe : recipes) for (AEKey key : recipe.outputs.keySet()) {
                index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(recipe.pattern);
            }
            var service = mock(ICraftingService.class, id);
            when(service.getCraftingFor(any())).thenAnswer(call -> index.getOrDefault(call.getArgument(0), List.of()));
            return service;
        }

        Map<String, String> named(Map<AEKey, ? extends Number> amounts) {
            Map<String, String> result = new LinkedHashMap<>();
            amounts.forEach((key, amount) -> result.put(labels.getOrDefault(key, key.toString()), amount.toString()));
            return result;
        }

        Map<IPatternDetails, Recipe> byPattern() {
            Map<IPatternDetails, Recipe> result = new IdentityHashMap<>();
            recipes.forEach(recipe -> result.put(recipe.pattern, recipe));
            return result;
        }

        /** No production planner, state equation, or repeat-summary implementation is used by this oracle. */
        Map<AEKey, Long> verifyWitness() {
            assertTrue(feasible);
            var stock = new LinkedHashMap<>(initial);
            long firings = 0;
            for (Witness block : witness) {
                for (long lap = 0; lap < block.repetitions; lap++) {
                    for (int index : block.recipes) {
                        assertTrue(++firings <= 5_000_000, "Fixture witness is too large for independent firing-by-firing replay");
                        Recipe recipe = recipes.get(index);
                        if (!enabled(recipe, stock)) fail(id + " cannot execute " + recipe.name + " from " + named(stock));
                        fire(recipe, stock);
                    }
                }
            }
            long newlyProduced = Math.subtractExact(stock.getOrDefault(goal, 0L), initial.getOrDefault(goal, 0L));
            assertTrue(newlyProduced >= requested, id + " hand witness delivers only " + newlyProduced);
            return stock;
        }

        /** Small negative controls close their complete finite marking set; a cutoff would fail the oracle itself. */
        void verifyImpossibleControl() {
            assertFalse(feasible);
            var pending = new ArrayDeque<Map<AEKey, Long>>();
            Set<Map<AEKey, Long>> seen = new LinkedHashSet<>();
            pending.add(Map.copyOf(initial));
            seen.add(Map.copyOf(initial));
            while (!pending.isEmpty()) {
                var current = pending.removeFirst();
                assertTrue(current.getOrDefault(goal, 0L) - initial.getOrDefault(goal, 0L) < requested,
                        id + " incorrectly claims impossibility");
                for (Recipe recipe : recipes) {
                    if (!enabled(recipe, current)) continue;
                    var next = new LinkedHashMap<>(current);
                    fire(recipe, next);
                    next.values().removeIf(amount -> amount == 0L);
                    var marking = Map.copyOf(next);
                    if (seen.add(marking)) pending.addLast(marking);
                    assertTrue(seen.size() <= 100_000, id + " negative oracle did not close");
                }
            }
        }

        void verifyReturnedAmounts(Map<IPatternDetails, BigInteger> firings, Map<AEKey, BigInteger> reservations) {
            Map<AEKey, BigInteger> balance = new LinkedHashMap<>(reservations);
            reservations.forEach((key, amount) -> {
                assertTrue(amount.signum() >= 0);
                assertTrue(amount.compareTo(BigInteger.valueOf(initial.getOrDefault(key, 0L))) <= 0,
                        id + " over-reserves " + labels.get(key));
            });
            var byPattern = byPattern();
            firings.forEach((pattern, count) -> {
                assertTrue(count.signum() >= 0);
                Recipe recipe = byPattern.get(pattern);
                assertNotNull(recipe, "Planner invented a recipe");
                recipe.inputs.forEach((key, amount) -> balance.merge(key, count.multiply(BigInteger.valueOf(amount)).negate(), BigInteger::add));
                recipe.grossOutputs().forEach((key, amount) -> balance.merge(key, count.multiply(BigInteger.valueOf(amount)), BigInteger::add));
            });
            balance.forEach((key, amount) -> assertTrue(amount.signum() >= 0,
                    id + " returned an unclosed material balance for " + labels.get(key) + ": " + amount));
            assertTrue(balance.getOrDefault(goal, BigInteger.ZERO).compareTo(BigInteger.valueOf(requested)) >= 0,
                    id + " does not physically produce the requested amount from its reservations");
        }
    }

    static List<Scenario> all() {
        InventoryTestBootstrap.initialize();
        List<Scenario> cases = new ArrayList<>();
        cases.add(mekanismSubstrate());
        cases.add(mekanismHdpe("mek_hdpe_byproduct_oxygen", 64, false, false));
        cases.add(mekanismHdpe("mek_hdpe_liquid_feed", 64, true, false));
        cases.add(mekanismHdpe("mek_hdpe_no_seed_control", 16, false, true));
        cases.add(mekanismBootstrap(false));
        cases.add(mekanismBootstrap(true));
        cases.add(acidRecovery(1000, false));
        cases.add(acidRecovery(500, false));
        cases.add(acidRecovery(500, true));
        cases.add(acidRecovery(500, true, true, 64));
        cases.add(acidRecovery(500, true, false, 3));
        cases.add(mixedCatalyst(false));
        cases.add(mixedCatalyst(true));
        cases.add(mixedCatalyst(false, false, false, 32));
        cases.add(mixedCatalyst(false, true, true, 32));
        cases.add(mixedCatalyst(false, true, false, 1));
        cases.add(catalystRegeneration(false));
        cases.add(catalystRegeneration(true));
        for (int amount : new int[] {1, 1024, 65_536, 131_072, 1_000_000}) cases.add(enzymeStartup(amount));
        cases.add(coupledRecovery());
        cases.add(slowCrystalGrowth());
        cases.add(sharedMetalAlternatives(8));
        return List.copyOf(cases);
    }

    private static Scenario mekanismSubstrate() {
        var b = new Builder("mek_substrate_stock_seed", "Mekanism 基片自增殖", "Mekanism PRC water_ethene, original 1 -> 8 ratio");
        var substrate = b.item("基片"); var ethene = b.fluid("乙烯气体"); var water = b.fluid("水"); var oxygen = b.fluid("氧气");
        b.recipe("基片增殖", m(substrate, 1, water, 200, ethene, 100), m(substrate, 8, oxygen, 10));
        return b.finish(substrate, 1024, m(substrate, 1, water, 29_400, ethene, 14_700), List.of(w(147, 0)), true);
    }

    private static Scenario mekanismHdpe(String id, int amount, boolean liquidFeed, boolean noSeed) {
        var b = new Builder(id, "Mekanism 基片增殖副产氧气 + 乙烯液化 + HDPE", "Mekanism PRC and rotary original ratios; no electrolysis pattern installed");
        var substrate = b.item("基片"); var gas = b.fluid("乙烯气体"); var liquid = b.fluid("液态乙烯");
        var water = b.fluid("水"); var oxygen = b.fluid("氧气"); var pellet = b.item("HDPE 颗粒");
        b.recipe("HDPE", m(substrate, 1, liquid, 50, oxygen, 10), m(pellet, 1)); // 0
        b.recipe("基片增殖", m(substrate, 1, water, 200, gas, 100), m(substrate, 8, oxygen, 10)); // 1
        b.recipe("乙烯液化", m(gas, 50), m(liquid, 50)); // 2, fifty 1:1 rotary operations per AE processing batch
        b.recipe("乙烯汽化", m(liquid, 50), m(gas, 50)); // 3
        var stock = m(water, 200L * amount, liquidFeed ? liquid : gas, 150L * amount);
        if (!noSeed) stock.put(substrate, 1L);
        List<Witness> witness = new ArrayList<>();
        if (liquidFeed) witness.add(w(3L * amount, 3));
        witness.add(w(amount, 1, 2, 0));
        return b.finish(pellet, amount, stock, noSeed ? List.of() : witness, !noSeed);
    }

    private static Scenario mekanismBootstrap(boolean growthFirst) {
        var b = new Builder("mek_hdpe_bootstrap_" + (growthFirst ? "growth_first" : "biomass_first"),
                "Mekanism 从生物燃料启动，改变样板优先级", "Mekanism PRC, rotary and electrolytic separator original ratios");
        var substrate = b.item("基片"); var bio = b.item("生物燃料"); var water = b.fluid("水");
        var gas = b.fluid("乙烯气体"); var liquid = b.fluid("液态乙烯"); var hydrogen = b.fluid("氢气"); var oxygen = b.fluid("氧气");
        var pellet = b.item("HDPE 颗粒");
        b.recipe("HDPE", m(substrate, 1, liquid, 50, oxygen, 10), m(pellet, 1));
        if (growthFirst) b.recipe("基片增殖", m(substrate, 1, water, 200, gas, 100), m(substrate, 8, oxygen, 10));
        int bootstrap = b.recipe("生物燃料反应", m(bio, 2, water, 10, hydrogen, 100), m(substrate, 1, gas, 100));
        if (!growthFirst) b.recipe("基片增殖", m(substrate, 1, water, 200, gas, 100), m(substrate, 8, oxygen, 10));
        int separate = b.recipe("电解水", m(water, 2), m(hydrogen, 2, oxygen, 1));
        int rotary = b.recipe("乙烯液化", m(gas, 50), m(liquid, 50));
        b.recipe("乙烯汽化", m(liquid, 50), m(gas, 50));
        return b.finish(pellet, 64, m(bio, 128, water, 7040),
                List.of(w(3200, separate), w(64, bootstrap, rotary, 0)), true);
    }

    private static Scenario acidRecovery(int acidStock, boolean bootstrap) {
        return acidRecovery(acidStock, bootstrap, false, 64);
    }

    private static Scenario acidRecovery(int acidStock, boolean bootstrap, boolean makeupFirst, int amount) {
        var b = new Builder("acid_recovery_" + acidStock + (bootstrap ? "_with_makeup" : "")
                + (makeupFirst ? "_makeup_first" : "") + (amount == 64 ? "" : "_q" + amount),
                "酸洗回收需要凑够两次产物再蒸馏", "Pack-style ore wash; GT dilute HCl distillation keeps the original 2000 -> 1000 + 1000 ratio");
        var ore = b.item("待洗矿粉"); var washed = b.item("洗净矿粉"); var acid = b.fluid("盐酸");
        var dilute = b.fluid("稀盐酸"); var water = b.fluid("水"); var reagent = b.item("制酸试剂");
        int makeup = makeupFirst ? b.recipe("补充启动酸", m(reagent, 1), m(acid, 500)) : -1;
        int wash = b.recipe("酸洗", m(ore, 1, acid, 500, water, 500), m(washed, 1, dilute, 1000));
        int recover = b.recipe("回收盐酸", m(dilute, 2000), m(acid, 1000, water, 1000));
        var stock = m(ore, amount, acid, acidStock, water, 1000);
        var witness = new ArrayList<Witness>();
        if (bootstrap) {
            if (!makeupFirst) makeup = b.recipe("补充启动酸", m(reagent, 1), m(acid, 500));
            stock.put(reagent, 1L); witness.add(w(1, makeup));
        }
        witness.add(w(amount / 2, wash, wash, recover));
        if (amount % 2 != 0) witness.add(w(1, wash));
        boolean feasible = acidStock == 1000 || bootstrap;
        return b.finish(washed, amount, stock, feasible ? witness : List.of(), feasible);
    }

    private static Scenario mixedCatalyst(boolean explicitOutput) {
        return mixedCatalyst(explicitOutput, true, false, 32);
    }

    private static Scenario mixedCatalyst(boolean explicitOutput, boolean withTool, boolean reverse, int perRoute) {
        var b = new Builder("mixed_feed_" + (!withTool ? "no_tool_control" : explicitOutput ? "processing_return" : "crafting_remainder")
                + (reverse ? "_reverse" : "") + (perRoute == 32 ? "" : "_q" + 2 * perRoute),
                "两种原料各够半单，共用不消耗的催化工具", "Pack-style alternative ingredient recipes with an unchanged tool/catalyst");
        var first = b.item("一级原料"); var second = b.item("二级原料"); var output = b.item("成品粉末");
        var catalyst = withTool ? b.item("催化工具") : null;
        for (AEKey material : reverse ? List.of(second, first) : List.of(first, second)) {
            String name = material.equals(first) ? "一级原料" : "二级原料";
            if (!withTool) b.recipe(name, m(material, 1), m(output, 1));
            else if (explicitOutput) b.recipe(name, m(material, 1, catalyst, 1), m(output, 1, catalyst, 1));
            else b.recipe(name, m(material, 1, catalyst, 1), m(output, 1), m(catalyst, 1));
        }
        var stock = m(first, perRoute, second, perRoute);
        if (withTool) stock.put(catalyst, 1L);
        return b.finish(output, 2L * perRoute, stock, List.of(w(perRoute, 0), w(perRoute, 1)), true);
    }

    private static Scenario catalystRegeneration(boolean spentSeed) {
        var b = new Builder("catalyst_regeneration_" + (spentSeed ? "spent_start" : "fresh_start"),
                "催化剂再生 + 四批稀硫酸回收", "Pack-style catalyst regeneration; GT dilute sulfuric distillation 3000 -> 2000 + 1000");
        var active = b.item("活性催化剂"); var spent = b.item("失活催化剂"); var feed = b.item("反应原料"); var product = b.item("反应产物");
        var acid = b.fluid("硫酸"); var dilute = b.fluid("稀硫酸"); var water = b.fluid("水");
        b.recipe("主反应", m(active, 1, feed, 1), m(product, 1, spent, 1));
        b.recipe("再生催化剂", m(spent, 1, acid, 500, water, 250), m(active, 1, dilute, 750));
        b.recipe("回收硫酸", m(dilute, 3000), m(acid, 2000, water, 1000));
        var sequence = spentSeed ? List.of(1, 0, 1, 0, 1, 0, 1, 0, 2) : List.of(0, 1, 0, 1, 0, 1, 0, 1, 2);
        return b.finish(product, 64, m(spentSeed ? spent : active, 1, acid, 2000, water, 1000, feed, 64),
                List.of(new Witness(16, sequence)), true);
    }

    private static Scenario enzymeStartup(int amount) {
        var b = new Builder("enzyme_startup_detour_" + amount, "培养液必须先扩培，再同时提供两种启动物", "Pack-style fermenter/culture multiplication, not a specific mod recipe");
        var broth = b.fluid("培养液"); var culture = b.item("菌种"); var feed = b.item("营养料"); var enzyme = b.item("酶");
        b.recipe("产酶", m(broth, 1000, culture, 1, feed, 1), m(enzyme, 1, broth, 2000));
        b.recipe("分离菌种", m(broth, 1000), m(culture, 1));
        b.recipe("扩培", m(culture, 1, feed, 1), m(broth, 2000));
        return b.finish(enzyme, amount, m(broth, 1000, feed, amount + 1L), List.of(w(1, 1, 2), w(amount, 1, 0)), true);
    }

    private static Scenario coupledRecovery() {
        var b = new Builder("coupled_catalyst_solvent_recovery", "催化剂与两组溶剂相互回收", "Pack-style sequential chemical reactors, six reusable state keys");
        var active = b.item("催化剂"); var spent = b.item("失活催化剂"); var a = b.fluid("溶剂A"); var spentA = b.fluid("废溶剂A");
        var c = b.fluid("溶剂B"); var spentB = b.fluid("废溶剂B"); var feed = b.item("原料"); var product = b.item("高分子");
        b.recipe("聚合", m(active, 1, feed, 1), m(product, 1, spent, 1));
        b.recipe("催化剂清洗", m(spent, 1, a, 1000), m(active, 1, spentA, 1000));
        b.recipe("萃取", m(spentA, 1000, c, 1000), m(a, 1000, spentB, 1000));
        b.recipe("溶剂精馏", m(spentB, 1000), m(c, 1000));
        return b.finish(product, 4096, m(active, 1, a, 1000, c, 1000, feed, 4096), List.of(w(4096, 0, 1, 2, 3)), true);
    }

    private static Scenario slowCrystalGrowth() {
        var b = new Builder("crystal_nine_to_ten", "九份晶种经两道工序变十份", "Pack-style seeded crystallization with small net gain");
        var crystal = b.item("晶种"); var slurry = b.fluid("晶种浆液"); var feed = b.item("结晶原料");
        b.recipe("溶解", m(crystal, 9, feed, 1), m(slurry, 1000));
        b.recipe("结晶", m(slurry, 1000), m(crystal, 10));
        return b.finish(crystal, 4096, m(crystal, 9, feed, 4096), List.of(w(4096, 0, 1)), true);
    }

    private static Scenario sharedMetalAlternatives(int count) {
        var b = new Builder("shared_metal_" + count + "_returned_tool", "电路的多种零件与最后的布线争用同一批金属", "Pack-style multi-input circuit assembly, two compatible recipes per intermediate");
        var copper = b.item("铜料"); var alternate = b.item("替代金属"); var tool = b.item("不损耗模具"); var wire = b.item("铜线"); var board = b.item("电路板");
        List<AEKey> parts = new ArrayList<>();
        for (int i = 0; i < count; i++) parts.add(b.item("零件" + (i + 1)));
        var assembly = new LinkedHashMap<AEKey, Long>();
        parts.forEach(part -> assembly.put(part, 1L));
        assembly.put(wire, (long) count);
        b.recipe("组装电路板", assembly, m(board, 1));
        List<Integer> alternatives = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            b.recipe("铜制零件" + i, m(copper, 1, tool, 1), m(parts.get(i), 1), m(tool, 1));
            alternatives.add(b.recipe("替代金属零件" + i, m(alternate, 1, tool, 1), m(parts.get(i), 1), m(tool, 1)));
        }
        int drawing = b.recipe("拉丝", m(copper, 1), m(wire, 1));
        List<Witness> witness = new ArrayList<>();
        witness.add(new Witness(1, alternatives)); witness.add(w(count, drawing)); witness.add(w(1, 0));
        return b.finish(board, 1, m(copper, count, alternate, count, tool, 1), witness, true);
    }

    private static Witness w(long repetitions, Integer... sequence) { return new Witness(repetitions, List.of(sequence)); }

    private static Map<AEKey, Long> m(Object... entries) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) result.put((AEKey) entries[i], ((Number) entries[i + 1]).longValue());
        return result;
    }

    private static boolean enabled(Recipe recipe, Map<AEKey, Long> stock) {
        return recipe.inputs.entrySet().stream().allMatch(entry -> stock.getOrDefault(entry.getKey(), 0L) >= entry.getValue());
    }

    private static void fire(Recipe recipe, Map<AEKey, Long> stock) {
        recipe.inputs.forEach((key, amount) -> stock.put(key, Math.subtractExact(stock.getOrDefault(key, 0L), amount)));
        recipe.grossOutputs().forEach((key, amount) -> stock.merge(key, amount, Math::addExact));
    }

    private static final class Builder {
        final String id, title, source;
        final Map<AEKey, String> labels = new LinkedHashMap<>();
        final List<Recipe> recipes = new ArrayList<>();
        Builder(String id, String title, String source) { this.id = id; this.title = title; this.source = source; }

        AEKey item(String name) {
            var stack = new ItemStack(Items.PAPER);
            stack.setHoverName(Component.literal(id + ":" + name));
            AEKey key = AEItemKey.of(stack);
            labels.put(key, name);
            return key;
        }

        AEKey fluid(String name) {
            var stack = new FluidStack(Fluids.WATER, 1);
            stack.setHoverName(Component.literal(id + ":" + name));
            AEKey key = AEFluidKey.of(stack);
            labels.put(key, name + " [mB]");
            return key;
        }

        int recipe(String name, Map<AEKey, Long> inputs, Map<AEKey, Long> outputs) {
            return recipe(name, inputs, outputs, Map.of());
        }

        int recipe(String name, Map<AEKey, Long> inputs, Map<AEKey, Long> outputs, Map<AEKey, Long> returns) {
            var pattern = mock(IPatternDetails.class, id + "/" + name);
            var stacks = outputs.entrySet().stream().map(entry -> new GenericStack(entry.getKey(), entry.getValue())).toList();
            var slots = inputs.entrySet().stream().map(entry -> {
                var slot = mock(IPatternDetails.IInput.class);
                when(slot.getMultiplier()).thenReturn(1L);
                when(slot.getPossibleInputs()).thenReturn(new GenericStack[] {new GenericStack(entry.getKey(), entry.getValue())});
                when(slot.isValid(any(), isNull())).thenAnswer(call -> entry.getKey().equals(call.getArgument(0)));
                if (returns.containsKey(entry.getKey())) {
                    assertEquals(1L, entry.getValue());
                    assertEquals(1L, returns.get(entry.getKey()));
                    when(slot.getRemainingKey(entry.getKey())).thenReturn(entry.getKey());
                }
                return slot;
            }).toArray(IPatternDetails.IInput[]::new);
            when(pattern.getInputs()).thenReturn(slots);
            when(pattern.getOutputs()).thenReturn(stacks.toArray(GenericStack[]::new));
            when(pattern.getPrimaryOutput()).thenReturn(cn.dancingsnow.neoecoae.platform.JdkCompat.first(stacks));
            recipes.add(new Recipe(name, Collections.unmodifiableMap(new LinkedHashMap<>(inputs)),
                    Collections.unmodifiableMap(new LinkedHashMap<>(outputs)), Map.copyOf(returns), pattern));
            return recipes.size() - 1;
        }

        Scenario finish(AEKey goal, long requested, Map<AEKey, Long> initial, List<Witness> witness, boolean feasible) {
            return new Scenario(id, title, source, goal, requested, Map.copyOf(labels), Map.copyOf(initial),
                    List.copyOf(recipes), List.copyOf(witness), feasible);
        }
    }
}
