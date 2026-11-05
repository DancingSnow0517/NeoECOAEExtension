package cn.dancingsnow.neoecoae.mixins.keyreuse;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.InternedTagData;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "appeng.api.stacks.AEItemKey$InternedTag", remap = false)
public interface InternedTagDataMixin extends InternedTagData {
    @Override @Accessor("tag") CompoundTag neoecoae$tag();
}
