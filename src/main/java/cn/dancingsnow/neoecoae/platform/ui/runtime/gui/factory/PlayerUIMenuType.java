package cn.dancingsnow.neoecoae.platform.ui.runtime.gui.factory;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/** Player-bound UIs identified by a registered id; the server supplies all data, the client mirrors the tree. */
public final class PlayerUIMenuType {
    public interface PlayerUIHolder {
        @Nullable ModularUI createUI(Player player);

        default boolean isStillValid(Player player) {
            return true;
        }
    }

    private static final Map<ResourceLocation, Function<Player, PlayerUIHolder>> HOLDERS = new ConcurrentHashMap<>();

    private static final class Holder implements IUIHolder {
        private final Player player;
        private final ResourceLocation id;
        private final PlayerUIHolder target;

        private Holder(Player player, ResourceLocation id, PlayerUIHolder target) {
            this.player = player;
            this.id = id;
            this.target = target;
        }

        @Override public com.lowdragmc.lowdraglib.gui.modular.ModularUI createUI(Player player) {
            var ui = target.createUI(player);
            return ui == null ? null : ui.nativeUI(this);
        }
        @Override public boolean isInvalid() { return player.isRemoved() || !target.isStillValid(player); }
        @Override public boolean isRemote() { return player.level().isClientSide; }
        @Override public void markAsDirty() {}
    }

    private static final UIFactory<Holder> FACTORY = new UIFactory<>(new ResourceLocation("neoecoae", "player_ui")) {
        @Override protected com.lowdragmc.lowdraglib.gui.modular.ModularUI createUITemplate(Holder holder, Player player) {
            return holder.isInvalid() ? null : holder.createUI(player);
        }
        @OnlyIn(Dist.CLIENT)
        @Override protected Holder readHolderFromSyncData(FriendlyByteBuf buffer) { return ClientHolderReader.read(buffer); }
        @Override protected void writeHolderToSyncData(FriendlyByteBuf buffer, Holder holder) { buffer.writeResourceLocation(holder.id); }
    };

    // Keep client-only types out of the factory's verification during dedicated-server setup.
    @OnlyIn(Dist.CLIENT)
    private static final class ClientHolderReader {
        private static Holder read(FriendlyByteBuf buffer) {
            ResourceLocation id = buffer.readResourceLocation();
            Player player = net.minecraft.client.Minecraft.getInstance().player;
            var factory = HOLDERS.get(id);
            var target = factory == null || player == null ? null : factory.apply(player);
            return target == null ? null : new Holder(player, id, target);
        }
    }

    private PlayerUIMenuType() {}

    /** Called during common setup on both the client and the dedicated server. */
    public static void registerFactory() { UIFactory.register(FACTORY); }

    /** Both sides must register the same id before the UI is opened. */
    public static void register(ResourceLocation id, Function<Player, PlayerUIHolder> holder) { HOLDERS.put(id, holder); }

    public static boolean openUI(ServerPlayer player, ResourceLocation id) {
        var factory = HOLDERS.get(id);
        var target = factory == null ? null : factory.apply(player);
        return target != null && FACTORY.openUI(new Holder(player, id, target), player);
    }
}
