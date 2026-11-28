package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import appeng.menu.implementations.MenuTypeBuilder;
import cn.dancingsnow.neoecoae.gui.computation.ECOCraftingStatusUI;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Avoids closing the client screen first when returning from the ECO crafting status UI to an AE2 menu. */
@Mixin(MenuTypeBuilder.class)
public abstract class MenuTypeBuilderMixin {
    @WrapOperation(
        method = "open",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;openMenu(Lnet/minecraft/world/MenuProvider;Ljava/util/function/Consumer;)Ljava/util/OptionalInt;"
        )
    )
    private OptionalInt neoecoae$keepCursorFromEcoStatus(
            Player player,
            MenuProvider provider,
            Consumer<RegistryFriendlyByteBuf> extraDataWriter,
            Operation<OptionalInt> original) {
        if (!ECOCraftingStatusUI.isCraftingStatusMenu(player.containerMenu)) {
            return original.call(player, provider, extraDataWriter);
        }
        MenuProvider wrapped = new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return provider.getDisplayName();
            }

            @Override
            public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player menuPlayer) {
                return provider.createMenu(containerId, inventory, menuPlayer);
            }

            @Override
            public boolean shouldTriggerClientSideContainerClosingOnOpen() {
                return false;
            }
        };
        return original.call(player, wrapped, extraDataWriter);
    }
}
