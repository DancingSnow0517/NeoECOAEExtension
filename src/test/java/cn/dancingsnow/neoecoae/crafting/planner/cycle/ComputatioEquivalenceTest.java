package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningBudget;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.component.ComponentDependency;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphEdge;
import cn.dancingsnow.neoecoae.crafting.planner.growth.PatternProfileValidator;
import cn.dancingsnow.neoecoae.crafting.planner.growth.SinglePatternGrowthCalculator;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.SpecialPatternAnalysis;
import com.y271727uy.computatio.algorithm.cycle.CycleRun;
import com.y271727uy.computatio.algorithm.cycle.CycleSolution;
import com.y271727uy.computatio.algorithm.cycle.PlanningBudget;
import com.y271727uy.computatio.api.cycle.CycleProblem;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Test branch only. NeoECO's own BoundedCycleSolver against Computatio, driven through Computatio's public
 * {@link CycleProblem} adapter, on identical random cyclic components. solve, solveGrowth and verifyFirings must agree
 * on every observable field (in NeoECO's long-based form).
 */
class ComputatioEquivalenceTest {
    private static final long WORK = 1_000_000L;
    private static final com.y271727uy.computatio.algorithm.cycle.CycleSolveLimits LIMITS =
        com.y271727uy.computatio.algorithm.cycle.CycleSolveLimits.DEFAULT;

    @Test
    void computatioMatchesNeoEco() throws Exception {
        int samples = Integer.parseInt(System.getenv().getOrDefault("NEOECO_EQUIV_SAMPLES", "3000"));
        Random random = new Random(0xEC0EC0L);
        Map<CycleSolveStatus, Integer> outcomes = new EnumMap<>(CycleSolveStatus.class);
        Map<String, Integer> shapes = new TreeMap<>();
        Map<String, Integer> checks = new TreeMap<>();
        List<String> mismatches = new ArrayList<>();
        long neoNanos = 0;
        long compNanos = 0;
        for (int sample = 0; sample < samples; sample++) {
            Sample s = sample(random, sample);
            shapes.merge(s.shape, 1, Integer::sum);
            CycleProblem<AEKey, IPatternDetails> problem = adapt(s.request);

            // ---- solve
            long a = System.nanoTime();
            Outcome<CycleSolveResult> neo = neo(() -> new BoundedCycleSolver().solve(s.request, budgetNeo()));
            long b = System.nanoTime();
            Outcome<CycleSolution> comp = comp(() -> problem.solve(LIMITS, budgetComp()));
            long c = System.nanoTime();
            neoNanos += b - a;
            compNanos += c - b;
            if (neo.value != null) outcomes.merge(neo.value.status(), 1, Integer::sum);
            compare("solve", sample, s, problem, neo, comp, mismatches, checks);

            // ---- solveGrowth (NeoECO's SinglePatternGrowthCycleSolver path)
            Outcome<CycleSolveResult> neoGrowth = neo(() -> BoundedCycleSolver.solveGrowth(s.request,
                new SinglePatternGrowthCalculator(), budgetNeo()));
            Outcome<CycleSolution> compGrowth = comp(() -> problem.solveGrowth(LIMITS, budgetComp()));
            compare("solveGrowth", sample, s, problem, neoGrowth, compGrowth, mismatches, checks);

            // ---- verifyFirings with NeoECO's own answer as the candidate (JointRouteOptimizer's use)
            if (neo.value != null && !neo.value.exactPatternTimes().isEmpty()) {
                Map<IPatternDetails, PlannerAmount> candidate = neo.value.exactPatternTimes();
                Map<IPatternDetails, com.y271727uy.computatio.algorithm.amount.PlannerAmount> converted = new LinkedHashMap<>();
                candidate.forEach((details, amount) -> converted.put(details, convert(amount)));
                Outcome<CycleSolveResult> neoVerify = neo(() -> new BoundedCycleSolver().verifyFirings(s.request,
                    candidate, budgetNeo()));
                Outcome<CycleSolution> compVerify = comp(() -> problem.verifyFirings(converted, LIMITS, budgetComp()));
                compare("verifyFirings", sample, s, problem, neoVerify, compVerify, mismatches, checks);
            }
        }
        System.out.printf("[NeoECO equivalence] %d samples, shapes %s, neo solve outcomes %s, comparisons %s, mismatches %d;"
                + " solve time neo %.0f ms, computatio %.0f ms (x%.1f)%n", samples, shapes, outcomes, checks,
            mismatches.size(), neoNanos / 1e6, compNanos / 1e6, (double) neoNanos / Math.max(1, compNanos));
        mismatches.stream().limit(20).forEach(System.out::println);
        assertTrue(mismatches.isEmpty(), mismatches.size() + " mismatches; first: "
            + (mismatches.isEmpty() ? "" : mismatches.get(0)));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The adapter: NeoECO request -> Computatio CycleProblem (what a production integration would do)
    // ---------------------------------------------------------------------------------------------------------------

    static CycleProblem<AEKey, IPatternDetails> adapt(CycleSolveRequest request) {
        CycleProblem.Builder<AEKey, IPatternDetails> builder = CycleProblem.builder();
        for (AEKey member : request.component().members()) builder.member(member);
        for (ComponentDependency dependency : request.externalResourceBoundary()) {
            for (CraftingGraphEdge edge : dependency.relationships()) builder.boundary(edge.requiredInput());
        }
        request.availableRelevantStock().forEach(builder::stock);
        request.plannerRequiredOutputs().forEach((key, amount) -> builder.required(key, convert(amount)));
        PatternProfileValidator validator = new PatternProfileValidator();
        for (CompiledPattern pattern : request.component().patterns()) {
            var recipe = builder.recipe(pattern.details(), pattern.id());
            for (CompiledInput input : pattern.inputs()) {
                if (input.remainderKey() != null) {
                    recipe.input(input.key(), convert(input.amountPerPattern()), input.remainderKey(),
                        convert(input.remainderAmountPerPattern()));
                } else {
                    recipe.input(input.key(), convert(input.amountPerPattern()));
                }
            }
            for (GenericStack output : pattern.grossOutputs()) recipe.output(output.what(), output.amount());
            boolean durability = pattern.specialAnalysis().requirements().stream()
                .anyMatch(r -> r.type() == SpecialPatternAnalysis.Type.DURABILITY);
            recipe.circuitSafe(pattern.semantics().cycleSafeForStaticPlanning() && !durability);
            recipe.growthSafe(validator.validate(pattern).netGrowthSafe());
            String reason = unsupportedReason(pattern);
            if (reason != null) recipe.reject(reason);
            recipe.add();
        }
        return builder.build();
    }

    /** Verbatim copy of NeoECO's private BoundedCycleSolver.unsupportedReason. */
    static String unsupportedReason(CompiledPattern pattern) {
        if (!pattern.fastSupported()) {
            return pattern.unsupportedReason() == null || pattern.unsupportedReason().isEmpty()
                ? "NOT_FAST_SUPPORTED" : pattern.unsupportedReason();
        }
        if (pattern.outputPerPattern().signum() <= 0) return "PRIMARY_OUTPUT_MISMATCH";
        for (CompiledInput input : pattern.inputs()) {
            if (!input.fastSupported()) {
                return input.unsupportedReason() == null || input.unsupportedReason().isEmpty()
                    ? "UNSUPPORTED_INPUT" : input.unsupportedReason();
            }
            if (input.amountPerPattern().signum() <= 0) return "INVALID_INPUT_AMOUNT";
            if (!input.amountPerPattern().fitsLong()) {
                return "AMOUNT_UNREPRESENTABLE: input=" + input.key() + " amount="
                    + input.amountPerPattern() + " max=" + Long.MAX_VALUE;
            }
        }
        if (pattern.outputs().isEmpty()) return "NO_OUTPUTS";
        for (GenericStack output : pattern.outputs()) {
            if (output == null || output.what() == null || output.amount() <= 0) return "INVALID_OUTPUT";
        }
        return null;
    }

    static com.y271727uy.computatio.algorithm.amount.PlannerAmount convert(PlannerAmount amount) {
        return com.y271727uy.computatio.algorithm.amount.PlannerAmount.of(amount.toBigInteger());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Comparison in NeoECO's long-based form
    // ---------------------------------------------------------------------------------------------------------------

    private interface Call<T> { T run() throws Exception; }
    private record Outcome<T>(T value, String exception) {}

    private static <T> Outcome<T> neo(Call<T> call) {
        try { return new Outcome<>(call.run(), null); }
        catch (ECOPlanningBudget.Exhausted e) { return new Outcome<>(null, "BUDGET_EXHAUSTED"); }
        catch (Exception e) { return new Outcome<>(null, e.getClass().getSimpleName() + ": " + e.getMessage()); }
    }

    private static <T> Outcome<T> comp(Call<T> call) {
        try { return new Outcome<>(call.run(), null); }
        catch (PlanningBudget.Exhausted e) { return new Outcome<>(null, "BUDGET_EXHAUSTED"); }
        catch (Exception e) { return new Outcome<>(null, e.getClass().getSimpleName() + ": " + e.getMessage()); }
    }

    private static ECOPlanningBudget budgetNeo() {
        return new ECOPlanningBudget(ECOCancellation.NONE, WORK, Long.MAX_VALUE, () -> 0L);
    }

    private static PlanningBudget budgetComp() {
        return new PlanningBudget(com.y271727uy.computatio.algorithm.cycle.Cancellation.NONE, WORK, Long.MAX_VALUE, () -> 0L);
    }

    private static void compare(String what, int sample, Sample s, CycleProblem<AEKey, IPatternDetails> problem,
            Outcome<CycleSolveResult> neo, Outcome<CycleSolution> comp, List<String> mismatches, Map<String, Integer> checks) {
        checks.merge(what, 1, Integer::sum);
        String left = neo.exception != null ? "EXCEPTION " + neo.exception
            : neo.value == null ? "null" : fingerprint(problem, neo.value);
        String right = comp.exception != null ? "EXCEPTION " + comp.exception
            : comp.value == null ? "null" : fingerprint(problem, comp.value);
        if (left.equals(right) && neo.value != null && comp.value != null && !neo.value.executionWitness().isEmpty()) {
            List<Integer> neoSteps = new ArrayList<>();
            neo.value.executionWitness().forEach(f -> neoSteps.add(f.pattern().id()));
            int[] expanded = comp.value.expandedPlan(neoSteps.size());
            List<Integer> compSteps = new ArrayList<>();
            if (expanded != null) for (int t : expanded) compSteps.add(idOf(s, problem.recipe(t)));
            if (!neoSteps.equals(compSteps)) right += " | witness " + compSteps + " vs " + neoSteps;
        }
        if (!left.equals(right)) {
            mismatches.add(what + " sample " + sample + " [" + s.shape + "] " + s.describe + "\n   neo        " + left
                + "\n   computatio " + right);
        }
    }

    private static int idOf(Sample s, IPatternDetails details) {
        for (CompiledPattern pattern : s.request.component().patterns()) if (pattern.details() == details) return pattern.id();
        return -1;
    }

    private static String fingerprint(CycleProblem<AEKey, IPatternDetails> p, CycleSolveResult r) {
        Map<String, BigInteger> times = new TreeMap<>();
        r.exactPatternTimes().forEach((d, amount) -> times.put(name(d), amount.toBigInteger()));
        List<String> plan = new ArrayList<>();
        r.executionPlan().forEach(run -> plan.add(name(run.pattern().details()) + "x" + run.count() + "/"
            + run.repeatWidth() + "^" + run.repetitions()));
        List<Map<String, BigInteger>> startup = new ArrayList<>();
        r.startupCandidates().forEach(c -> startup.add(longs(c)));
        List<String> codes = new ArrayList<>();
        r.diagnostics().forEach(d -> { if (d.code() != CycleSolveDiagnostic.Code.SEARCH_METRICS) codes.add(d.code().name()); });
        return r.status() + "|" + r.executionCountKnowledge() + "|times=" + times + "|plan=" + plan
            + "|demand=" + longs(r.externalDemand()) + "|seed=" + longs(r.requiredSeed()) + "|short=" + longs(r.seedShortfall())
            + "|produced=" + longs(r.producedOutputs()) + "|deliver=" + longs(r.deliverableOutputs())
            + "|startup=" + startup + "|codes=" + codes;
    }

    private static String fingerprint(CycleProblem<AEKey, IPatternDetails> p, CycleSolution r) {
        Map<String, BigInteger> times = new TreeMap<>();
        p.firings(r).forEach((d, amount) -> times.put(name(d), amount.toBigInteger()));
        // NeoECO clears its long plan when any runtime count leaves the long range.
        boolean runtimeFits = r.firings().stream().allMatch(a -> a.signum() == 0 || a.fitsLong())
            && r.plan().stream().allMatch(run -> run.count().fitsLong() && run.repetitions().fitsLong());
        List<String> plan = new ArrayList<>();
        if (runtimeFits) {
            for (CycleRun run : r.plan()) plan.add(name(p.recipe(run.transition())) + "x" + run.count() + "/"
                + run.repeatWidth() + "^" + run.repetitions());
        }
        List<Map<String, BigInteger>> startup = new ArrayList<>();
        p.startupCandidates(r).forEach(c -> startup.add(exact(c, false)));
        List<String> codes = new ArrayList<>();
        r.diagnostics().forEach(d -> { if (!d.code().name().equals("SEARCH_METRICS")) codes.add(d.code().name()); });
        return CycleProblem.longStatus(r) + "|" + r.countKnowledge() + "|times=" + times + "|plan=" + plan
            + "|demand=" + exact(p.externalDemand(r), true) + "|seed=" + exact(p.requiredSeed(r), true)
            + "|short=" + exact(p.seedShortfall(r), true) + "|produced=" + exact(p.produced(r), true)
            + "|deliver=" + exact(p.deliverable(r), true) + "|startup=" + startup + "|codes=" + codes;
    }

    private static String name(IPatternDetails details) { return details.toString(); }

    private static Map<String, BigInteger> longs(Map<AEKey, Long> values) {
        Map<String, BigInteger> result = new TreeMap<>();
        values.forEach((k, v) -> result.put(k.toString(), BigInteger.valueOf(v)));
        return result;
    }

    /** NeoECO's representable(): amounts beyond the long range are dropped from its long maps. */
    private static Map<String, BigInteger> exact(Map<AEKey, com.y271727uy.computatio.algorithm.amount.PlannerAmount> values,
            boolean dropWide) {
        Map<String, BigInteger> result = new TreeMap<>();
        values.forEach((k, v) -> { if (!dropWide || v.fitsLong()) result.put(k.toString(), v.toBigInteger()); });
        return result;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Random NeoECO cyclic components
    // ---------------------------------------------------------------------------------------------------------------

    private record Sample(CycleSolveRequest request, String shape, String describe) {}

    private static final String[] SHAPES = {"fuelled", "free", "single", "scaled", "wide", "duplicate", "remainder", "unsupported"};

    private static Sample sample(Random random, int sample) {
        String shape = SHAPES[sample % SHAPES.length];
        int materials = 3 + random.nextInt(2);
        List<AEKey> m = new ArrayList<>();
        for (int i = 0; i < materials; i++) m.add(mock(AEKey.class, "m" + i));
        AEKey fuel = mock(AEKey.class, "fuel");
        AEKey imported = mock(AEKey.class, "import");
        AEKey bucket = mock(AEKey.class, "bucket");
        boolean fuelled = shape.equals("fuelled") || shape.equals("duplicate") || shape.equals("unsupported");
        boolean single = shape.equals("single") || (shape.equals("remainder") && random.nextBoolean());

        List<AEKey> members = new ArrayList<>();
        if (single) members.add(m.get(0));
        else {
            for (AEKey key : m) if (random.nextInt(3) == 0) members.add(key);
            if (members.isEmpty()) members.add(m.get(random.nextInt(materials)));
        }

        int transitions = single ? 1 : 1 + random.nextInt(4);
        List<CompiledPattern> patterns = new ArrayList<>();
        List<CraftingGraphEdge> boundaryEdges = new ArrayList<>();
        List<CraftingGraphEdge> internalEdges = new ArrayList<>();
        int nextId = 0;
        for (int t = 0; t < transitions; t++) {
            long[] in = new long[materials];
            long[] out = new long[materials];
            for (int k = 0; k < materials; k++) {
                if (random.nextInt(2) == 0) in[k] = random.nextInt(3);
                if (random.nextInt(2) == 0) out[k] = random.nextInt(4);
            }
            if (single) {
                in[0] = 1 + random.nextInt(2);
                out[0] = Math.max(1, in[0] + random.nextInt(3) - (random.nextInt(4) == 0 ? 1 : 0));
            }
            boolean anyOut = false;
            for (long o : out) anyOut |= o > 0;
            if (!anyOut) out[random.nextInt(materials)] = 1 + random.nextInt(3);

            IPatternDetails details = mock(IPatternDetails.class, "p" + t);
            List<GenericStack> outputs = new ArrayList<>();
            for (int k = 0; k < materials; k++) if (out[k] > 0) outputs.add(new GenericStack(m.get(k), out[k]));
            List<GenericStack> returned = new ArrayList<>();
            List<CompiledInput> inputs = new ArrayList<>();
            for (int k = 0; k < materials; k++) {
                if (in[k] > 0) inputs.add(new CompiledInput(null, m.get(k), in[k], true, null));
            }
            if (shape.equals("remainder") && random.nextBoolean()) {
                // A container input handed back: as an input remainder, sometimes also as normalised returned stock.
                boolean onFeedback = single && random.nextBoolean();
                AEKey container = onFeedback ? m.get(0) : bucket;
                inputs.add(new CompiledInput(null, container, PlannerAmount.ONE, true, null, container,
                    PlannerAmount.of(random.nextInt(3) == 0 ? 0 : 1)));
                if (random.nextBoolean()) returned.add(new GenericStack(container, 1));
            }
            if (fuelled) inputs.add(new CompiledInput(null, fuel, 1, true, null));
            CompiledInput importInput = null;
            if (random.nextInt(3) == 0) {
                importInput = new CompiledInput(null, imported, 1 + random.nextInt(3), true, null);
                inputs.add(importInput);
            }
            boolean fast = !(shape.equals("unsupported") && random.nextInt(3) == 0);
            boolean cycleSafe = random.nextInt(4) != 0;
            boolean growthValidated = single ? random.nextInt(4) != 0 : random.nextBoolean();
            var semantics = new PatternSemantics(details, null, List.of(), outputs, returned, List.of(),
                PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, cycleSafe, null);
            CompiledPattern pattern = new CompiledPattern(nextId++, details, outputs.get(0).what(),
                PlannerAmount.of(outputs.get(0).amount()), inputs, outputs, fast, fast ? null : "SUBSTITUTION",
                growthValidated, semantics);
            patterns.add(pattern);
            if (shape.equals("duplicate") && random.nextBoolean()) {
                // A second compiled view of the same physical pattern, with a different id and amounts.
                List<GenericStack> otherOutputs = List.of(new GenericStack(outputs.get(0).what(), outputs.get(0).amount() + 1));
                var otherSemantics = new PatternSemantics(details, null, List.of(), otherOutputs, List.of(), List.of(),
                    PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, cycleSafe, null);
                int otherId = random.nextBoolean() ? nextId++ + 100 : -1 - t;
                patterns.add(new CompiledPattern(otherId, details, outputs.get(0).what(),
                    PlannerAmount.of(outputs.get(0).amount() + 1), inputs, otherOutputs, true, null, growthValidated, otherSemantics));
            }
            if (importInput != null) boundaryEdges.add(new CraftingGraphEdge(null, imported, pattern, importInput));
            if (single) {
                for (CompiledInput input : inputs) {
                    if (input.key() == members.get(0)) internalEdges.add(new CraftingGraphEdge(members.get(0), members.get(0), pattern, input));
                }
            }
        }

        Map<AEKey, Long> stock = new LinkedHashMap<>();
        for (AEKey key : m) stock.put(key, (long) random.nextInt(5));
        if (fuelled) stock.put(fuel, (long) random.nextInt(7));
        stock.put(bucket, (long) random.nextInt(2));
        Map<AEKey, PlannerAmount> required = new LinkedHashMap<>();
        int targets = 1 + random.nextInt(2);
        for (int i = 0; i < targets; i++) {
            AEKey key = single ? members.get(0) : m.get(random.nextInt(materials));
            PlannerAmount amount;
            switch (shape) {
                case "scaled" -> amount = PlannerAmount.of((1 + random.nextInt(9)) * (long) Math.pow(10, 3 + random.nextInt(13)));
                case "wide" -> amount = PlannerAmount.of(BigInteger.TEN.pow(19 + random.nextInt(12)).multiply(BigInteger.valueOf(1 + random.nextInt(9))));
                case "fuelled", "duplicate", "unsupported" -> amount = PlannerAmount.of(random.nextInt(10));
                default -> amount = PlannerAmount.of(single && random.nextInt(3) == 0
                    ? (1 + random.nextInt(9)) * (long) Math.pow(10, random.nextInt(16)) : random.nextInt(41));
            }
            required.put(key, amount);
        }
        List<ComponentDependency> boundary = boundaryEdges.isEmpty() ? List.of() : List.of(new ComponentDependency(1, 0, boundaryEdges));
        CycleComponent component = new CycleComponent(0, members, patterns, internalEdges, boundary, List.of());
        CycleSolveRequest request = new CycleSolveRequest(component, Map.of(), required, stock, boundary,
            new CycleSolveRequest.PlannerOptions(CycleSolveLimits.DEFAULT));

        StringBuilder describe = new StringBuilder("members=").append(members).append(" stock=").append(stock)
            .append(" required=").append(required);
        Set<String> seen = new LinkedHashSet<>();
        for (CompiledPattern pattern : patterns) {
            describe.append(" | ").append(pattern.details()).append("#").append(pattern.id())
                .append(pattern.fastSupported() ? "" : "!fast").append(pattern.semantics().cycleSafe() ? "" : "!c")
                .append(pattern.netGrowthValidated() ? "" : "!g").append(" in=");
            pattern.inputs().forEach(input -> describe.append(input.key()).append('x').append(input.amountPerPattern())
                .append(input.remainderKey() != null ? "(rem " + input.remainderKey() + "x" + input.remainderAmountPerPattern() + ")" : "")
                .append(','));
            describe.append(" out=").append(pattern.grossOutputs());
            seen.add(pattern.details().toString());
        }
        return new Sample(request, shape, describe.toString());
    }
}
