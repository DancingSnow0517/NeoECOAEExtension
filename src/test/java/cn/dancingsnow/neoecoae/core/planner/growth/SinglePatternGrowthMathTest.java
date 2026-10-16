package cn.dancingsnow.neoecoae.core.planner.growth;

import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;
import java.math.BigInteger;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SinglePatternGrowthMathTest {
 @Test void genericKeysComputeCeilingFiringsAndExactDemand() {
  var required=Map.of("output", PlannerAmount.of(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)));
  var stock=Map.<String,PlannerAmount>of("output", PlannerAmount.of(1));
  var net=Map.of("output", PlannerAmount.of(3));
  assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TWO).divide(BigInteger.valueOf(3)),
      SinglePatternGrowthMath.requiredFirings(required,stock,net).toBigInteger());
  var demand=SinglePatternGrowthMath.externalDemand(Map.of("input",PlannerAmount.of(5),"output",PlannerAmount.of(1)),
      Map.of("input",PlannerAmount.of(2)), "output", PlannerAmount.of(4));
  assertEquals(PlannerAmount.of(12), demand.get("input"));
 }
 @Test void missingPositiveNetOutputIsRejected() {
  assertThrows(IllegalArgumentException.class, () -> SinglePatternGrowthMath.requiredFirings(
      Map.of("x",PlannerAmount.ONE), Map.of(), Map.of("x",PlannerAmount.ZERO)));
 }
}
