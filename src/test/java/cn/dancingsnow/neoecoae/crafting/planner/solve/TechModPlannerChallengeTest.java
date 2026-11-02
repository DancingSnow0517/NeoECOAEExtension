package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCraftingPlannerService;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import com.google.gson.GsonBuilder;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in adversarial audit. Feasible misses remain failing tests; report JSON is written before the assertion. */
@EnabledIfSystemProperty(named = "planner.challenge.enabled", matches = "true")
class TechModPlannerChallengeTest {
    static Stream<TechModPlannerScenarios.Scenario> scenarios() {
        String selected = System.getProperty("planner.challenge.case", "");
        return TechModPlannerScenarios.all().stream().filter(s -> selected.isBlank() || s.id().matches(selected));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void plannerHandlesAnIndependentlyProvenPlayerOrder(TechModPlannerScenarios.Scenario scenario) throws Exception {
        if (scenario.feasible()) scenario.verifyWitness();
        else scenario.verifyImpossibleControl();
        String profile = System.getProperty("planner.challenge.profile", "default");
        assertTrue(profile.equals("default") || profile.equals("screening"));
        int previousMillis = NEConfig.ecoPlanningMaxMillis, previousWork = NEConfig.ecoPlanningMaxWork;
        if (profile.equals("screening")) { NEConfig.ecoPlanningMaxMillis = 5000; NEConfig.ecoPlanningMaxWork = 1_000_000; }
        var directory = Path.of("build", "reports", "planner-challenges", profile);
        Files.createDirectories(directory);
        var report = new LinkedHashMap<String, Object>();
        report.put("id", scenario.id()); report.put("title", scenario.title()); report.put("source", scenario.source());
        report.put("expectedFeasible", scenario.feasible()); report.put("quantity", scenario.requested());
        report.put("goal", scenario.labels().get(scenario.goal())); report.put("initial", scenario.named(scenario.initial()));
        report.put("recipeCount", scenario.recipes().size()); report.put("keyCount", scenario.labels().size());
        report.put("profile", profile); report.put("maxMillis", NEConfig.ecoPlanningMaxMillis);
        report.put("maxWork", NEConfig.ecoPlanningMaxWork); report.put("maxMemoryMiB", NEConfig.ecoPlanningMaxMemoryMiB);
        report.put("recipes", scenario.recipes().stream().map(recipe -> Map.of("name", recipe.name(),
                "inputs", scenario.named(recipe.inputs()), "outputs", scenario.named(recipe.outputs()),
                "returned", scenario.named(recipe.returned()))).toList());
        report.put("manualWitness", scenario.witness());
        report.put("status", "RUNNING"); write(directory, scenario.id(), report);
        AtomicLong checks = new AtomicLong();
        long start = System.nanoTime();
        try {
            ECOPlanningResult result = new ECOCraftingPlannerService()
                    .createSession(scenario.service(), scenario.goal(), scenario.inventory(), true)
                    .plan(scenario.requested(), false, () -> {
                        checks.incrementAndGet();
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Challenge cancelled");
                    });
            report.put("status", result.status().name());
            report.put("elapsedMillis", (System.nanoTime() - start) / 1_000_000.0);
            report.put("cancellationChecks", checks.get());
            report.put("missing", scenario.named(exact(result.exactMissingItems())));
            report.put("used", scenario.named(exact(result.exactUsedItems())));
            var byPattern = scenario.byPattern();
            Map<String, String> counts = new LinkedHashMap<>();
            result.exactPatternTimes().forEach((pattern, count) -> counts.put(byPattern.containsKey(pattern)
                    ? byPattern.get(pattern).name() : pattern.toString(), count.toString()));
            report.put("firings", counts);
            report.put("diagnostics", result.trace().diagnostics().stream().map(d -> d.code() + ": " + d.message()).toList());
            report.put("cycles", result.components().stream().filter(c -> c.cycleResult() != null)
                    .map(c -> Map.of("component", c.componentId(), "status", c.cycleResult().status().name(),
                            "diagnostics", c.cycleResult().diagnostics().stream().map(d -> d.code() + ": " + d.message()).toList(),
                            "startupCandidates", c.cycleResult().startupCandidates().stream().map(scenario::named).toList(),
                            "seed", scenario.named(c.cycleResult().requiredSeed()), "states", c.cycleResult().metrics().statesVisited())).toList());
            if (result.status() == PlanningStatus.SUCCESS) {
                assertNull(result.executionPlanError(), result.executionPlanError());
                assertTrue(result.exactMissingItems().isEmpty());
                Map<IPatternDetails, BigInteger> firings = new LinkedHashMap<>();
                result.exactPatternTimes().forEach((pattern, count) -> firings.put(pattern, count.toBigInteger()));
                scenario.verifyReturnedAmounts(firings, exact(result.exactUsedItems()));
                report.put("materialBalanceVerified", true);
                report.put("phaseCount", result.executionPlan() == null ? 0 : result.executionPlan().phases().size());
            }
            report.put("matchesOracle", scenario.feasible() == (result.status() == PlanningStatus.SUCCESS));
            write(directory, scenario.id(), report);
            if (scenario.feasible()) assertEquals(PlanningStatus.SUCCESS, result.status(), scenario.id()
                    + " has a verified physical witness; actual=" + result.status() + " missing=" + report.get("missing"));
            else assertNotEquals(PlanningStatus.SUCCESS, result.status(), scenario.id() + " cannot execute from this stock");
        } catch (Throwable failure) {
            report.put("elapsedMillis", (System.nanoTime() - start) / 1_000_000.0);
            report.put("cancellationChecks", checks.get());
            report.put("testFailure", failure.toString());
            write(directory, scenario.id(), report);
            throw failure;
        } finally {
            NEConfig.ecoPlanningMaxMillis = previousMillis; NEConfig.ecoPlanningMaxWork = previousWork;
        }
    }

    private static Map<AEKey, BigInteger> exact(Map<AEKey, cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount> source) {
        Map<AEKey, BigInteger> result = new LinkedHashMap<>();
        source.forEach((key, amount) -> result.put(key, amount.toBigInteger()));
        return result;
    }

    private static void write(Path directory, String id, Map<String, Object> report) throws Exception {
        Files.writeString(directory.resolve(id + ".json"), new GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
                .create().toJson(report) + "\n", StandardCharsets.UTF_8);
    }
}
