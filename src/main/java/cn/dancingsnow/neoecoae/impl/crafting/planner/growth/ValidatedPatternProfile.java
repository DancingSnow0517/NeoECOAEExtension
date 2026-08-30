package cn.dancingsnow.neoecoae.impl.crafting.planner.growth;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Verified: self-growing pattern" — an unforgeable statement that one pattern's recorded validation
 * evidence was read and turned into an exact per-firing static contract.
 *
 * <p>Same wheel as {@code ECOVerifiedFastPathRecipe}, one layer up:
 *
 * <ul>
 *   <li>The constructor is private and the factory is package-private, so only
 *       {@link PatternProfileValidator} can mint a profile. No caller can fabricate a "validated" pattern
 *       and walk it into the growth calculator.</li>
 *   <li>It pins its issuer, so a consumer can assert provenance ({@link #isIssuedBy}) instead of trusting
 *       an object it was handed.</li>
 *   <li>The per-firing vectors are only populated when {@link PatternCapability#NET_GROWTH_SAFE} was
 *       granted. A rejected pattern exposes no numbers at all, so no caller can accidentally do algebra on
 *       an indeterminate contract.</li>
 *   <li>Capabilities are independent bits, never a ladder: see {@link PatternCapability}.</li>
 * </ul>
 *
 * <p>Amounts are per single firing. {@code remainder} is the determinate static remainder contract; stage
 * one only grants the capability when it was resolved by the smart-pattern-bus validator, and the field exists so the
 * {@code production + remainder - consumption} identity is written once, here, rather than re-derived by
 * every consumer.
 */
public final class ValidatedPatternProfile {
    private final PatternProfileValidator issuer;
    private final CompiledPattern pattern;
    private final Set<PatternCapability> capabilities;
    private final Map<AEKey, Long> consumption;
    private final Map<AEKey, Long> production;
    private final Map<AEKey, Long> remainder;
    private final NetGrowthRejection netGrowthRejection;

    private ValidatedPatternProfile(PatternProfileValidator issuer, CompiledPattern pattern,
            Set<PatternCapability> capabilities, Map<AEKey, Long> consumption, Map<AEKey, Long> production,
            Map<AEKey, Long> remainder, NetGrowthRejection netGrowthRejection) {
        this.issuer = issuer;
        this.pattern = pattern;
        this.capabilities = Set.copyOf(capabilities);
        this.consumption = Map.copyOf(consumption);
        this.production = Map.copyOf(production);
        this.remainder = Map.copyOf(remainder);
        this.netGrowthRejection = netGrowthRejection;
    }

    /** Trusted construction. Only {@link PatternProfileValidator} may call this. */
    static ValidatedPatternProfile trusted(PatternProfileValidator issuer, CompiledPattern pattern,
            Set<PatternCapability> capabilities, Map<AEKey, Long> consumption, Map<AEKey, Long> production,
            Map<AEKey, Long> remainder, NetGrowthRejection netGrowthRejection) {
        return new ValidatedPatternProfile(issuer, pattern, capabilities, consumption, production, remainder,
            netGrowthRejection);
    }

    public CompiledPattern pattern() {
        return pattern;
    }

    public IPatternDetails details() {
        return pattern.details();
    }

    public Set<PatternCapability> capabilities() {
        return capabilities;
    }

    public boolean has(PatternCapability capability) {
        return capabilities.contains(capability);
    }

    public boolean fastPathSafe() {
        return has(PatternCapability.FAST_PATH_SAFE);
    }

    public boolean netGrowthSafe() {
        return has(PatternCapability.NET_GROWTH_SAFE);
    }

    public NetGrowthRejection netGrowthRejection() {
        return netGrowthRejection;
    }

    /** True only for the validator instance that produced this profile. */
    public boolean isIssuedBy(PatternProfileValidator candidate) {
        return this.issuer == candidate;
    }

    public Map<AEKey, Long> consumption() {
        return consumption;
    }

    public Map<AEKey, Long> production() {
        return production;
    }

    public Map<AEKey, Long> remainder() {
        return remainder;
    }

    public long consumptionOf(AEKey key) {
        return consumption.getOrDefault(key, 0L);
    }

    public long productionOf(AEKey key) {
        return production.getOrDefault(key, 0L);
    }

    public long remainderOf(AEKey key) {
        return remainder.getOrDefault(key, 0L);
    }

    /** Everything that comes back per firing: fresh production plus the determinate static remainder. */
    public long grossProductionOf(AEKey key) {
        return Math.addExact(productionOf(key), remainderOf(key));
    }

    /** {@code production[k] + remainder[k] - consumption[k]} for one firing. May be negative. */
    public long netDeltaPerFiring(AEKey key) {
        return Math.subtractExact(grossProductionOf(key), consumptionOf(key));
    }

    /** Every key the contract touches, in a deterministic order: consumed first, then produced. */
    public List<AEKey> touchedKeys() {
        Set<AEKey> keys = new LinkedHashSet<>(consumption.keySet());
        keys.addAll(production.keySet());
        keys.addAll(remainder.keySet());
        return List.copyOf(keys);
    }

    /**
     * Keys the pattern both consumes and returns, i.e. the candidates for a feedback loop the pattern
     * closes on its own. Order follows {@link #touchedKeys()}.
     */
    public List<AEKey> selfReferencingKeys() {
        List<AEKey> result = new ArrayList<>();
        for (AEKey key : touchedKeys()) {
            if (consumptionOf(key) > 0 && grossProductionOf(key) > 0) result.add(key);
        }
        return List.copyOf(result);
    }

    /** Net change of the whole contract per firing, positive and negative entries alike. */
    public Map<AEKey, Long> netDeltaPerFiring() {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (AEKey key : touchedKeys()) {
            long delta = netDeltaPerFiring(key);
            if (delta != 0) result.put(key, delta);
        }
        return Map.copyOf(result);
    }

    @Override
    public String toString() {
        return "ValidatedPatternProfile[pattern=" + pattern.id() + " capabilities=" + capabilities
            + (netGrowthSafe() ? "" : " netGrowthRejection=" + netGrowthRejection) + "]";
    }
}
