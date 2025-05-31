package cn.dancingsnow.neoecoae.api;

import appeng.api.networking.IGridNodeService;
import net.minecraft.world.item.ItemStack;

public interface IECOPatternStorage extends IGridNodeService {
    boolean insertPattern(ItemStack itemStack);
}
