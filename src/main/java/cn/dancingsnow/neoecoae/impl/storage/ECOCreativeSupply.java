package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import java.math.BigInteger;

/** Live creative supply for exact orders only. Never infer infinity from a saturated counter. */
public final class ECOCreativeSupply {
    private ECOCreativeSupply() {}

    public static BigInteger extract(IGrid grid, AEKey key, BigInteger requested, IActionSource source) {
        if (requested.signum() <= 0) return BigInteger.ZERO;
        for (var drive : grid.getMachines(ECODriveBlockEntity.class)) {
            if (!drive.isMounted() || !drive.isOnline()
                    || !(drive.getCellInventory() instanceof ECOCreativeCell creative)
                    || !creative.configuredKeys().contains(key)) continue;
            // Keep the network's access check. A simulated probe never takes finite stock.
            if (grid.getStorageService().getInventory().extract(key, 1, Actionable.SIMULATE, source) != 1)
                return BigInteger.ZERO;
            // Creative cells generate resources without changing stored contents. One live delegate
            // probe validates the key; multiplying this supply needs no repeated long-sized calls.
            if (creative.extract(key, 1, Actionable.SIMULATE, source) == 1) return requested;
        }
        return BigInteger.ZERO;
    }
}