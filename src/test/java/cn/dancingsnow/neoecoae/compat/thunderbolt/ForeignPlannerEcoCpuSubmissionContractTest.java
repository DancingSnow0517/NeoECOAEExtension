package cn.dancingsnow.neoecoae.compat.thunderbolt;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ForeignPlannerEcoCpuSubmissionContractTest {
    @Test
    void explicitEcoCpuSelectionPrecedesEcoPlannerOwnershipCheck() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/cn/dancingsnow/neoecoae/mixins/ae2/crafting/CraftingServiceMixin.java"));

        int explicitCpu = source.indexOf("target instanceof ECOCraftingCPU ecoCpu");
        int explicitPlaceholder = source.indexOf("cluster.getFakeCPU() == target");
        int plannerOwnership = source.indexOf("ECOPlanningResultRegistry.find(job)");

        assertTrue(explicitCpu >= 0 && explicitCpu < plannerOwnership);
        assertTrue(explicitPlaceholder >= 0 && explicitPlaceholder < plannerOwnership);
    }
}
