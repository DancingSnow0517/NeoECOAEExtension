package cn.dancingsnow.neoecoae.mixins.ae2;

import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.AECraftingPattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AECraftingPattern.class, remap = false)
public interface AECraftingPatternAccessor {
    @Accessor("definition")
    AEItemKey neoecoae$getDefinitionKey();
}
