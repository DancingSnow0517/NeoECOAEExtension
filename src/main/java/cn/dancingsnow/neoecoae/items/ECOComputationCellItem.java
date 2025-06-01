package cn.dancingsnow.neoecoae.items;

import cn.dancingsnow.neoecoae.api.IECOTier;
import lombok.Getter;
import net.minecraft.world.item.Item;

public class ECOComputationCellItem extends Item {
    @Getter
    private final IECOTier tier;
    public ECOComputationCellItem(Properties properties, IECOTier tier) {
        super(properties);
        this.tier = tier;
    }
}
