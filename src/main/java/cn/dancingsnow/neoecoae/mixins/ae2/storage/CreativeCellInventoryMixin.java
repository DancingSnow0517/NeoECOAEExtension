package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import cn.dancingsnow.neoecoae.storage.transfer.ECOCreativeExtractionFilter;
import org.spongepowered.asm.mixin.Mixin;

/** Gives the package-private AE2 creative inventory an explicit, non-mutating IO identity. */
@Mixin(targets = "appeng.me.cells.CreativeCellInventory", remap = false)
public abstract class CreativeCellInventoryMixin implements ECOCreativeExtractionFilter.CreativeStorage {}
