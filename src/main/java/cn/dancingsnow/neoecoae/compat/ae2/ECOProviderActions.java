package cn.dancingsnow.neoecoae.compat.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.me.service.CraftingService;
import appeng.parts.AEBasePart;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.PatternProviderLogicAccessor;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

/** User-triggered lookup of the current job's real providers; never scans neighbors or loads chunks. */
public final class ECOProviderActions {
    private ECOProviderActions() {}

    public static List<Source> find(List<IPatternDetails> patterns, CraftingService crafting) {
        var sources = new LinkedHashMap<String, Source>();
        for (var pattern : patterns) for (var provider : crafting.getProviders(pattern)) {
            Source source;
            try { source = locate(provider); }
            catch (RuntimeException | LinkageError unsupported) { continue; }
            if (source != null) sources.putIfAbsent(source.id(), source);
        }
        return List.copyOf(sources.values());
    }

    static Source locate(ICraftingProvider provider) {
        Object current = provider;
        for (int depth = 0; current != null && depth < 8; depth++) {
            Object host = current instanceof PatternProviderLogicAccessor access ? access.neoecoae$getHost()
                : current instanceof PatternProviderLogicHost ? current : member(current, "host");
            if (host instanceof PatternProviderLogicHost patternHost) {
                var be = patternHost.getBlockEntity();
                return be.getLevel() == null ? null : new Source(patternHost, be,
                    host instanceof AEBasePart part ? part.getSide() : null,
                    patternHost.getTerminalIcon().getDisplayName());
            }
            Object holder = member(current, "getHolder");
            if (holder instanceof BlockEntity be && be.getLevel() != null && member(be, "getMetaMachine") == current) {
                return new Source(current, be, null, be.getBlockState().getBlock().getName());
            }
            Object next = member(current, "getLogic");
            if (next == current) break;
            current = next;
        }
        return null;
    }

    public record Source(Object host, BlockEntity blockEntity, Direction side, Component name) {
        public String id() {
            return blockEntity.getLevel().dimension().location() + "@" + blockEntity.getBlockPos().asLong() + ":" + side;
        }

        public CompoundTag describe() {
            var tag = new CompoundTag();
            tag.putString("id", id());
            tag.putString("name", Component.Serializer.toJson(name));
            tag.putString("dimension", blockEntity.getLevel().dimension().location().toString());
            tag.putLong("position", blockEntity.getBlockPos().asLong());
            if (side != null) tag.putString("side", side.getName());
            return tag;
        }

        public boolean open(ServerPlayer player) {
            var level = blockEntity.getLevel();
            if (level == null || level != player.level() || !level.hasChunkAt(blockEntity.getBlockPos())
                || blockEntity.isRemoved() || level.getBlockEntity(blockEntity.getBlockPos()) != blockEntity
                || !level.mayInteract(player, blockEntity.getBlockPos())) return false;
            if (host instanceof PatternProviderLogicHost patternHost) {
                var previous = player.containerMenu;
                patternHost.openMenu(player, new ECORemoteProviderLocator(blockEntity, side));
                return previous != player.containerMenu;
            }
            return ECORemoteMachineUI.open(blockEntity, host, player);
        }
    }

    static Object member(Object object, String name) {
        if (object == null) return null;
        try {
            try {
                var method = object.getClass().getMethod(name);
                if (method.trySetAccessible()) return method.invoke(object);
            } catch (NoSuchMethodException ignored) {}
            for (var type = object.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    var field = type.getDeclaredField(name);
                    if (field.trySetAccessible()) return field.get(object);
                } catch (NoSuchFieldException ignored) {}
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) {}
        return null;
    }
}
