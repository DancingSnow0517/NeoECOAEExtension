package cn.dancingsnow.neoecoae.crafting.planner.solve;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Proves that each proposed challenge is a real executable order, or a fully closed negative control. */
class TechModPlannerScenarioTest {
    static Stream<TechModPlannerScenarios.Scenario> scenarios() { return TechModPlannerScenarios.all().stream(); }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void independentRecipeOracle(TechModPlannerScenarios.Scenario scenario) {
        if (scenario.feasible()) scenario.verifyWitness();
        else scenario.verifyImpossibleControl();
    }
}
