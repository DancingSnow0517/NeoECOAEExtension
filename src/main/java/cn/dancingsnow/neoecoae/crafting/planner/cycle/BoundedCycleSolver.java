package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import cn.dancingsnow.neoecoae.config.NEConfig;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningBudget;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.component.ComponentDependency;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphEdge;
import cn.dancingsnow.neoecoae.crafting.planner.growth.PatternProfileValidator;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionCountKnowledge;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.SpecialPatternAnalysis;
import com.y271727uy.computatio.api.cycle.CycleBudgetExhaustedException;
import com.y271727uy.computatio.api.cycle.CycleLimits;
import com.y271727uy.computatio.api.cycle.CyclePlan;
import com.y271727uy.computatio.api.cycle.CycleProblem;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cycle solving through Computatio's {@link CycleProblem}: the request becomes a problem in AE2 key and pattern
 * types, and the answer is mapped back. Every planning budget is shared with the library (see
 * {@link ECOPlanningBudget#check}).
 */
public final class BoundedCycleSolver implements CycleSolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(BoundedCycleSolver.class);
    private final PatternProfileValidator validator = new PatternProfileValidator();

    public BoundedCycleSolver() {}

    @Override
    public CycleSolveResult solve(CycleSolveRequest request, ECOCancellation cancellation)
            throws InterruptedException {
        if (!(cancellation instanceof ECOPlanningBudget)) cancellation = new ECOPlanningBudget(cancellation);
        try {
            Problem problem = problem(request);
            return result(problem, problem.cycle.solve(limits(request), ECOPlanningBudget.check(cancellation)));
        } catch (CycleBudgetExhaustedException exhausted) {
            throw ECOPlanningBudget.Exhausted.of(exhausted);
        } catch (ECOPlanningBudget.Exhausted exhausted) {
            throw exhausted;
        } catch (RuntimeException failure) {
            if (NEConfig.ecoPlanningStageDebug) LOGGER.error("BoundedCycleSolver failed for componentId={} memberKeys={} patternCount={}",
                request.component().componentId(), request.component().members(),
                request.component().patterns().size(), failure);
            throw failure;
        }
    }

    /** Verify a caller's fixed integer candidate with existing prefix/repeat algebra, without optimizing it again. */
    public @Nullable CycleSolveResult verifyFirings(CycleSolveRequest request,
            Map<IPatternDetails, PlannerAmount> counts, ECOCancellation cancellation) throws InterruptedException {
        Problem problem = problem(request);
        Map<IPatternDetails, BigInteger> exact = new LinkedHashMap<>();
        counts.forEach((details, amount) -> exact.put(details, amount.toBigInteger()));
        try {
            CyclePlan<AEKey, IPatternDetails> plan = problem.cycle.verifyFirings(exact, limits(request),
                ECOPlanningBudget.check(cancellation));
            return plan == null ? null : result(problem, plan);
        } catch (CycleBudgetExhaustedException exhausted) {
            throw ECOPlanningBudget.Exhausted.of(exhausted);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Request -> problem
    // ---------------------------------------------------------------------------------------------------------------

    /** The library problem plus the modelled (lowest-id) view of every pattern, for mapping the answer back. */
    private record Problem(CycleProblem<AEKey, IPatternDetails> cycle, Map<IPatternDetails, CompiledPattern> views) {}

    private Problem problem(CycleSolveRequest request) {
        CycleComponent component = request.component();
        CycleProblem.Builder<AEKey, IPatternDetails> builder = CycleProblem.builder();
        for (AEKey member : component.members()) builder.member(member);
        for (ComponentDependency dependency : request.externalResourceBoundary()) {
            for (CraftingGraphEdge edge : dependency.relationships()) builder.boundary(edge.requiredInput());
        }
        for (AEKey key : request.unboundedStock()) builder.unbounded(key);
        request.boundaryImportCaps().forEach((key, cap) -> builder.importCap(key, cap.toBigInteger()));
        request.plannerRelevantStock().forEach((key, amount) -> builder.stock(key, amount.toBigInteger()));
        request.plannerRequiredOutputs().forEach((key, amount) -> builder.required(key, amount.toBigInteger()));
        builder.growthSelfLoop(growthSelfLoop(component));

        Map<IPatternDetails, CompiledPattern> views = new LinkedHashMap<>();
        for (CompiledPattern pattern : component.patterns().stream()
                .sorted(Comparator.comparingInt(CompiledPattern::id)).toList()) {
            views.putIfAbsent(pattern.details(), pattern);
        }
        // Only a single-pattern component reaches the growth profile.
        boolean growthCandidate = views.size() == 1;
        for (CompiledPattern pattern : component.patterns()) {
            var recipe = builder.recipe(pattern.details(), pattern.id());
            for (CompiledInput input : pattern.inputs()) {
                recipe.input(input.key(), input.amountPerPattern().toBigInteger(), input.remainderKey(),
                    input.remainderAmountPerPattern().toBigInteger());
            }
            for (GenericStack output : pattern.grossOutputs()) recipe.output(output.what(), output.amount());
            boolean durability = pattern.specialAnalysis().requirements().stream()
                .anyMatch(requirement -> requirement.type() == SpecialPatternAnalysis.Type.DURABILITY);
            recipe.circuitSafe(pattern.semantics().cycleSafeForStaticPlanning() && !durability);
            if (growthCandidate) recipe.growthSafe(netGrowthSafe(pattern));
            String reason = unsupportedReason(pattern);
            if (reason != null) recipe.reject(reason);
            recipe.add();
        }
        return new Problem(builder.build(), views);
    }

    private boolean netGrowthSafe(CompiledPattern pattern) {
        try {
            return validator.validate(pattern).netGrowthSafe();
        } catch (RuntimeException failure) {
            // The growth calculator declines a pattern it cannot validate.
            return false;
        }
    }

    /**
     * The growth calculator's edge rule: every internal edge is a self-loop of the only pattern on the member. As in
     * the calculator, a failure while reading the edges only declines the growth path.
     */
    private static boolean growthSelfLoop(CycleComponent component) {
        try {
            Map<IPatternDetails, CompiledPattern> distinct = new LinkedHashMap<>();
            for (CompiledPattern pattern : component.patterns()) {
                if (pattern != null && pattern.details() != null) distinct.putIfAbsent(pattern.details(), pattern);
            }
            if (distinct.size() != 1 || component.members().size() != 1) return true; // declined earlier anyway
            CompiledPattern only = distinct.values().iterator().next();
            AEKey member = component.members().get(0);
            if (component.internalEdges().isEmpty()) return false;
            for (CraftingGraphEdge edge : component.internalEdges()) {
                CompiledPattern pattern = edge.pattern();
                if (pattern == null || pattern.details() == null || !pattern.details().equals(only.details())) {
                    return false;
                }
                if (!member.equals(edge.producer()) || !member.equals(edge.requiredInput())) return false;
            }
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static @Nullable String unsupportedReason(CompiledPattern pattern) {
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
        }
        if (pattern.outputs().isEmpty()) return "NO_OUTPUTS";
        for (GenericStack output : pattern.outputs()) {
            if (output == null || output.what() == null || output.amount() <= 0) return "INVALID_OUTPUT";
        }
        return null;
    }

    private static CycleLimits limits(CycleSolveRequest request) {
        CycleSolveLimits limits = request.options().limits();
        return new CycleLimits(limits.maxKeys(), limits.maxPatterns(), limits.maxStates(), limits.maxFirings(),
            limits.maxSeedLadderSteps(), maxMemoryBytes());
    }

    /** The configured retained-memory allowance of one cycle solve or material balance. */
    static long maxMemoryBytes() {
        return Math.max(1L, NEConfig.ecoPlanningMaxMemoryMiB) * 1024L * 1024L;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Plan -> result
    // ---------------------------------------------------------------------------------------------------------------

    private static CycleSolveResult result(Problem problem, CyclePlan<AEKey, IPatternDetails> plan) {
        List<CycleFiring> witness = new ArrayList<>(plan.witness().size());
        for (IPatternDetails details : plan.witness()) {
            witness.add(new CycleFiring(witness.size(), problem.views.get(details)));
        }
        List<PatternRun> runs = new ArrayList<>(plan.runs().size());
        for (CyclePlan.Run<IPatternDetails> run : plan.runs()) {
            runs.add(new PatternRun(problem.views.get(run.recipe()), PlannerAmount.of(run.count()), run.repeatWidth(),
                PlannerAmount.of(run.repetitions())));
        }
        List<CycleSolveDiagnostic> diagnostics = new ArrayList<>(plan.diagnostics().size());
        for (CyclePlan.Diagnostic diagnostic : plan.diagnostics()) {
            CycleSolveDiagnostic.Code code = known(CycleSolveDiagnostic.Code.class, diagnostic.code());
            if (code == null) {
                LOGGER.warn("Skipping cycle diagnostic with a code this NeoECO build does not know: {} {}",
                    diagnostic.code(), diagnostic.message());
                continue;
            }
            diagnostics.add(new CycleSolveDiagnostic(code, diagnostic.message()));
        }
        List<Map<AEKey, PlannerAmount>> candidates = new ArrayList<>(plan.startupCandidates().size());
        for (Map<AEKey, BigInteger> candidate : plan.startupCandidates()) candidates.add(amounts(candidate));
        CyclePlan.Metrics m = plan.metrics();
        CycleSolveMetrics metrics = new CycleSolveMetrics(m.relevantKeys(), m.transitions(), m.statesVisited(),
            m.statesExpanded(), m.witnessLength(), m.seedLadderSteps(), m.stateBudgetExhausted(),
            m.firingDepthTruncated(), m.amountOverflowTruncated(), m.greedyCandidates(), m.lookaheadNodes(),
            m.heuristicMacroSteps(), m.heuristicBudgetExhausted());
        CycleSolveStatus status = known(CycleSolveStatus.class, plan.status().name());
        if (status == null) {
            // Never a proof: an unknown verdict must not be read as missing materials or as success.
            LOGGER.warn("Cycle status {} is unknown to this NeoECO build; treating it as UNKNOWN_BUDGET",
                plan.status());
            return CycleSolveResult.failure(CycleSolveStatus.UNKNOWN_BUDGET, diagnostics, metrics);
        }
        return new CycleSolveResult(status,
            plan.exactCounts() ? ExecutionCountKnowledge.EXACT : ExecutionCountKnowledge.UNKNOWN,
            amounts(plan.firings()), amounts(plan.externalDemand()), amounts(plan.requiredSeed()),
            amounts(plan.seedShortfall()), amounts(plan.produced()), amounts(plan.deliverable()),
            List.copyOf(witness), List.copyOf(runs), List.copyOf(diagnostics), metrics, List.copyOf(candidates));
    }

    /** The constant of {@code type} named {@code name}, or {@code null} when a newer Computatio reports another. */
    private static <E extends Enum<E>> @Nullable E known(Class<E> type, String name) {
        for (E constant : type.getEnumConstants()) if (constant.name().equals(name)) return constant;
        return null;
    }

    private static <K> Map<K, PlannerAmount> amounts(Map<K, BigInteger> values) {
        Map<K, PlannerAmount> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(key, PlannerAmount.of(value)));
        return Collections.unmodifiableMap(result);
    }
}
