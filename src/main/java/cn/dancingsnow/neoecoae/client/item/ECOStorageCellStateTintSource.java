package cn.dancingsnow.neoecoae.client.item;

import appeng.api.storage.cells.CellState;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public enum ECOStorageCellStateTintSource implements ItemTintSource {
    INSTANCE;

    public static final Identifier ID = NeoECOAE.id("eco_storage_cell_state");
    public static final MapCodec<ECOStorageCellStateTintSource> MAP_CODEC = MapCodec.unit(INSTANCE);

    @Override
    public int calculate(@NonNull ItemStack itemStack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        CellState cellState = getCellState(itemStack);
        return ARGB.opaque(cellState.getStateColor());
    }

    private static CellState getCellState(@NonNull ItemStack itemStack) {
        IECOStorageCell inventory = ECOStorageCells.getCellInventory(itemStack, null);
        return inventory == null ? CellState.EMPTY : inventory.getStatus();
    }

    @Override
    public MapCodec<? extends ItemTintSource> type() {
        return MAP_CODEC;
    }
}
