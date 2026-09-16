package cn.dancingsnow.neoecoae.api.fastpath;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EcoFastpathHostContractTest {
    @Test
    void rejectsMissingProcessingIdentity() {
        assertThrows(NullPointerException.class, () -> new EcoFastpathHost.FastpathRequest(null, List.of(), 8L,
                EcoFastpathHost.CAPABILITY_ID, EcoFastpathHost.API_VERSION, UUID.randomUUID()));
    }

    @Test
    void publicContractDoesNotExposeEcoExecutionInternals() {
        for (var method : EcoFastpathHost.class.getMethods()) {
            var signature = method.toGenericString().toLowerCase(Locale.ROOT);
            assertTrue(!signature.contains("planner") && !signature.contains("craftingcpu")
                    && !signature.contains("provider") && !signature.contains("jobid")
                    && !signature.contains("screen"), signature);
        }
    }
}
