package cn.dancingsnow.neoecoae.compat.ae2lt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOFastPathStacks;
import java.util.UUID;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/** Loader-isolated access to AE2LT's optional batch API. */
public final class ECOAe2LtBatchCapability {
    private ECOAe2LtBatchCapability() {}

    @Nullable
    public static Session open(Object provider) {
        var modList = ModList.get();
        if (modList == null || !modList.isLoaded("ae2lt")) return null;
        return Access.open(provider);
    }

    public interface Session {
        long inspect(IPatternDetails details, KeyCounter[] inputs, long requested);
        boolean unbounded();
        long submit(IPatternDetails details, KeyCounter[] inputs, long copies);
    }

    private static final class Access {
        private static Session open(Object value) {
            if (value instanceof com.moakiee.ae2lt.api.lightning.batch.LightningBatchProvider provider) {
                return new LightningSession(provider);
            }
            if (value instanceof com.moakiee.ae2lt.api.tianshu.synthesis.TianshuSynthesizer synthesizer) {
                return new TianshuSession(synthesizer);
            }
            return null;
        }
    }

    private static final class LightningSession implements Session {
        private final com.moakiee.ae2lt.api.lightning.batch.LightningBatchProvider provider;
        private final UUID nonce = UUID.randomUUID();
        private long maxSafeBatch;

        private LightningSession(com.moakiee.ae2lt.api.lightning.batch.LightningBatchProvider provider) {
            this.provider = provider;
        }

        @Override
        public long inspect(IPatternDetails details, KeyCounter[] inputs, long requested) {
            try {
                var capability = provider.inspect(request(details, inputs, requested));
                if (capability.capabilityVersion()
                        != com.moakiee.ae2lt.api.lightning.batch.LightningBatchProvider.API_VERSION
                        || !provider.capabilityId().equals(capability.capabilityId())) return 0L;
                maxSafeBatch = capability.maxSafeBatch();
                return Math.max(0L, Math.min(requested, capability.acceptedAmount()));
            } catch (RuntimeException unavailable) {
                maxSafeBatch = 0L;
                return 0L;
            }
        }

        @Override
        public boolean unbounded() {
            return maxSafeBatch == Long.MAX_VALUE;
        }

        @Override
        public long submit(IPatternDetails details, KeyCounter[] inputs, long copies) {
            try {
                var result = provider.submit(request(details, inputs, copies));
                long accepted = result.acceptedAmount();
                if (accepted < 0L || accepted > copies
                        || result.unacceptedAmount() != copies - accepted) return copies;
                return copies - accepted;
            } catch (RuntimeException unavailable) {
                return copies;
            }
        }

        private com.moakiee.ae2lt.api.lightning.batch.LightningBatchProvider.BatchRequest request(
                IPatternDetails details, KeyCounter[] inputs, long requested) {
            var snapshot = java.util.Arrays.stream(inputs)
                    .map(ECOFastPathStacks::copyCounter)
                    .toList();
            return new com.moakiee.ae2lt.api.lightning.batch.LightningBatchProvider.BatchRequest(
                    com.moakiee.ae2lt.api.lightning.batch.LightningBatchProvider.API_VERSION,
                    provider.capabilityId(), details.getDefinition(), snapshot, requested, nonce);
        }
    }

    private static final class TianshuSession implements Session {
        private final com.moakiee.ae2lt.api.tianshu.synthesis.TianshuSynthesizer synthesizer;
        private final UUID nonce = UUID.randomUUID();
        private long maxSafeBatch;

        private TianshuSession(com.moakiee.ae2lt.api.tianshu.synthesis.TianshuSynthesizer synthesizer) {
            this.synthesizer = synthesizer;
        }

        @Override
        public long inspect(IPatternDetails details, KeyCounter[] inputs, long requested) {
            try {
                var capability = synthesizer.inspect(request(details, inputs, requested));
                if (capability.capabilityVersion()
                        != com.moakiee.ae2lt.api.tianshu.synthesis.TianshuSynthesizer.API_VERSION
                        || !com.moakiee.ae2lt.api.tianshu.synthesis.TianshuSynthesizer.CAPABILITY_ID
                                .equals(capability.capabilityId())) return 0L;
                maxSafeBatch = capability.maxSafeBatch();
                return Math.max(0L, Math.min(requested, capability.acceptedAmount()));
            } catch (RuntimeException unavailable) {
                maxSafeBatch = 0L;
                return 0L;
            }
        }

        @Override
        public boolean unbounded() {
            return maxSafeBatch == Long.MAX_VALUE;
        }

        @Override
        public long submit(IPatternDetails details, KeyCounter[] inputs, long copies) {
            try {
                var result = synthesizer.submit(request(details, inputs, copies));
                long accepted = result.acceptedAmount();
                if (accepted < 0L || accepted > copies
                        || result.unacceptedAmount() != copies - accepted) return copies;
                return copies - accepted;
            } catch (RuntimeException unavailable) {
                return copies;
            }
        }

        private com.moakiee.ae2lt.api.tianshu.synthesis.TianshuSynthesizer.SynthesisRequest request(
                IPatternDetails details, KeyCounter[] inputs, long requested) {
            var snapshot = java.util.Arrays.stream(inputs)
                    .map(ECOFastPathStacks::copyCounter)
                    .toList();
            return new com.moakiee.ae2lt.api.tianshu.synthesis.TianshuSynthesizer.SynthesisRequest(
                    details.getDefinition(), snapshot, requested,
                    com.moakiee.ae2lt.api.tianshu.synthesis.TianshuSynthesizer.CAPABILITY_ID,
                    com.moakiee.ae2lt.api.tianshu.synthesis.TianshuSynthesizer.API_VERSION, nonce);
        }
    }
}
