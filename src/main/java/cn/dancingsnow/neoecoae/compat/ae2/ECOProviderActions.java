package cn.dancingsnow.neoecoae.compat.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.me.service.CraftingService;
import appeng.parts.AEBasePart;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider.Target;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.PatternProviderLogicAccessor;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
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

    public static List<Source> findMachines(List<Target> targets, Level context) {
        if (context == null || context.isClientSide || context.getServer() == null) return List.of();
        var machines = new LinkedHashMap<String, Source>();
        for (var target : targets) {
            var level = context.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, target.dimension()));
            if (level == null || !level.hasChunkAt(target.position())) continue;
            var be = level.getBlockEntity(target.position());
            if (be == null || be.isRemoved()) continue;
            Object machine = member(be, "getMetaMachine");
            if (machine == null || member(machine, "getRecipeLogic") == null) continue;
            var source = new Source(machine, be, null, be.getBlockState().getBlock().getName());
            machines.putIfAbsent(source.id(), source);
        }
        return List.copyOf(machines.values());
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
            if (!available(player)) return false;
            if (host instanceof PatternProviderLogicHost patternHost) {
                var previous = player.containerMenu;
                patternHost.openMenu(player, new ECORemoteProviderLocator(blockEntity, side));
                return previous != player.containerMenu;
            }
            return ECORemoteMachineUI.open(blockEntity, host, player);
        }

        public boolean openMachine(ServerPlayer player) {
            return available(player) && member(blockEntity, "getMetaMachine") == host
                && member(host, "getRecipeLogic") != null && ECORemoteMachineUI.open(blockEntity, host, player);
        }

        private boolean available(ServerPlayer player) {
            var level = blockEntity.getLevel();
            return level != null && level == player.level() && level.hasChunkAt(blockEntity.getBlockPos())
                && !blockEntity.isRemoved() && level.getBlockEntity(blockEntity.getBlockPos()) == blockEntity
                && level.mayInteract(player, blockEntity.getBlockPos());
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
