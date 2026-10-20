package cn.dancingsnow.neoecoae.util;

import cn.dancingsnow.neoecoae.all.NEItems;
import com.tterrag.registrate.providers.loot.RegistrateBlockLootTables;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.ApplyExplosionDecay;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

public class LootTableUtil {
    public static <T extends Block> void energizedBud(RegistrateBlockLootTables prov, T block) {
        prov.add(block, prov.createSingleItemTableWithSilkTouch(block, NEItems.ENERGIZED_CRYSTAL_DUST));
    }

    public static <T extends Block> void energizedCluster(RegistrateBlockLootTables prov, T block) {
        prov.add(block, prov.createSilkTouchDispatchTable(block, LootItem.lootTableItem(NEItems.ENERGIZED_CRYSTAL)
            .apply(SetItemCountFunction.setCount(ConstantValue.exactly(4)))
            .apply(ApplyBonusCount.addUniformBonusCount(prov.getRegistries().holderOrThrow(Enchantments.FORTUNE)))
            .apply(ApplyExplosionDecay.explosionDecay())));
    }
}
