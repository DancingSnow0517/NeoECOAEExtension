package cn.dancingsnow.neoecoae.util;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStackResourceHandler;
import org.jspecify.annotations.NonNull;

public class CellHostItemHandler extends ItemStackResourceHandler {


    private final ICellHost host;

    public CellHostItemHandler(ICellHost host) {
        this.host = host;
    }

    @Override
    protected @NonNull ItemStack getStack() {
        return host.getCellStack();
    }

    @Override
    protected void setStack(@NonNull ItemStack stack) {
        host.setCellStack(stack);
    }

    @Override
    protected boolean isValid(ItemResource resource) {
        return host.isItemValid(resource.toStack());
    }
}
