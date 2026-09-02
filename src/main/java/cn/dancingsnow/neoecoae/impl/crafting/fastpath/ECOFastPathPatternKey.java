package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import java.util.Objects;

/** Pattern-level identity. Concrete resolved inputs deliberately do not participate in this key. */
public final class ECOFastPathPatternKey {
    private final Object patternDefinition;
    private final long reloadGeneration;
    private final int hash;

    ECOFastPathPatternKey(Object patternDefinition, long reloadGeneration) {
        this.patternDefinition = Objects.requireNonNull(patternDefinition, "patternDefinition");
        this.reloadGeneration = reloadGeneration;
        this.hash = Objects.hash(patternDefinition, reloadGeneration);
    }

    public long reloadGeneration() {
        return reloadGeneration;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof ECOFastPathPatternKey other
            && reloadGeneration == other.reloadGeneration
            && patternDefinition.equals(other.patternDefinition);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
