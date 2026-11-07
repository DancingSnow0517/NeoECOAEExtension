package cn.dancingsnow.neoecoae.mixins.compat.thunderbolt;

import cn.dancingsnow.neoecoae.crafting.ECOComponentInsensitiveProcessingPattern;
import com.moakiee.thunderbolt.core.crafting.pattern.FuzzyPatternInputs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** This metadata does not unwrap the task: its captured policy must survive provider lookup. */
@Mixin(value = ECOComponentInsensitiveProcessingPattern.class, remap = false)
public abstract class ECOProcessingPatternFuzzyMixin implements FuzzyPatternInputs {
    @Shadow public abstract boolean ignoresInputComponents(int slot);
    @Override public boolean acceptsSameIdVariants(int slot) { return ignoresInputComponents(slot); }
    @Override public boolean producesSameIdVariants(int slot) { return false; }
}
