package cn.dancingsnow.neoecoae.integration.megacells.backend;

import appeng.api.stacks.AEItemKey;
import gripe._90.megacells.misc.CompressionChain;
import gripe._90.megacells.misc.CompressionService;
import net.minecraft.world.item.ItemStack;

/** Adapts MEGA Cells 2.4's key/factor records to the locked main's compression contracts. */
final class MegaCompressionChains {
    private static final CompressionChain EMPTY = new CompressionChain();

    private MegaCompressionChains() {}

    static CompressionChain getChain(AEItemKey key) {
        return CompressionService.INSTANCE.getChain(key).orElse(EMPTY);
    }

    static ItemStack item(CompressionChain chain, int index) {
        var variant = chain.get(index);
        return variant.item().toStack(variant.factor());
    }
}
