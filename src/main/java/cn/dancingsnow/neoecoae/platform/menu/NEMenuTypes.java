/*
 * Adapted from Applied Energistics 2's MenuTypeBuilder, copyright (c) 2021
 * TeamAppliedEnergistics, licensed under LGPL-3.0-or-later. Keeps the Forge 1.20.1 locator/open protocol while leaving
 * registry ownership with NeoECOAE's DeferredRegister.
 */
package cn.dancingsnow.neoecoae.platform.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.MenuTypeBuilder.MenuFactory;
import appeng.menu.locator.MenuLocator;
import appeng.menu.locator.MenuLocators;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Nameable;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.network.NetworkHooks;

public final class NEMenuTypes {
    private NEMenuTypes() {}

    public static <M extends AEBaseMenu, I> MenuType<M> create(
            ResourceLocation id, MenuFactory<M, I> factory, Class<I> hostInterface) {
        return new HostMenuFactory<>(id, factory, hostInterface).type;
    }

    private static final class HostMenuFactory<M extends AEBaseMenu, I> {
        private final ResourceLocation id;
        private final MenuFactory<M, I> factory;
        private final Class<I> hostInterface;
        private final MenuType<M> type;

        private HostMenuFactory(ResourceLocation id, MenuFactory<M, I> factory, Class<I> hostInterface) {
            this.id = id;
            this.factory = factory;
            this.hostInterface = hostInterface;
            this.type = IForgeMenuType.create(this::fromNetwork);
            MenuOpener.addOpener(type, this::open);
        }

        private M fromNetwork(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
            var locator = MenuLocators.readFromPacket(buffer);
            I host = locator.locate(inventory.player, hostInterface);
            if (host == null) {
                var connection = Minecraft.getInstance().getConnection();
                if (connection != null) connection.send(new ServerboundContainerClosePacket(containerId));
                throw new IllegalStateException("Couldn't find menu host at " + locator + " for " + id
                        + " on client. Closing menu.");
            }
            M menu = factory.create(containerId, inventory, host);
            menu.setReturnedFromSubScreen(buffer.readBoolean());
            return menu;
        }

        private boolean open(Player player, MenuLocator locator, boolean fromSubMenu) {
            if (!(player instanceof ServerPlayer serverPlayer)) return false;
            I host = locator.locate(player, hostInterface);
            if (host == null) return false;
            Component title = host instanceof Nameable nameable && nameable.hasCustomName()
                    ? nameable.getCustomName() : Component.empty();
            var provider = new SimpleMenuProvider((window, inventory, owner) -> {
                M menu = factory.create(window, inventory, host);
                menu.setLocator(locator);
                return menu;
            }, title);
            NetworkHooks.openScreen(serverPlayer, provider, buffer -> {
                MenuLocators.writeToPacket(buffer, locator);
                buffer.writeBoolean(fromSubMenu);
            });
            return true;
        }
    }
}
