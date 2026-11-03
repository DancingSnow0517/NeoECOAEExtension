package cn.dancingsnow.neoecoae.crafting.display.processing;

import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider.Target;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The recipe machines a running output is processed in, as resolved by the processing monitor's budgeted scan.
 * Lookups never load chunks; opening re-validates the block and uses the block's own interaction.
 */
public final class ECOProcessingMachineActions {
    public static final String OPEN_UNAVAILABLE = "gui.neoecoae.processing.open_unavailable";

    private ECOProcessingMachineActions() {}

    public static List<Machine> find(@Nullable MinecraftServer server, List<Target> targets) {
        if (server == null) return List.of();
        var machines = new LinkedHashMap<String, Machine>();
        for (var target : targets) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, target.dimension()));
            if (level == null || !level.hasChunkAt(target.position())) continue;
            var blockEntity = level.getBlockEntity(target.position());
            if (blockEntity == null || blockEntity.isRemoved()) continue;
            var machine = new Machine(level, target.position(), level.getBlockState(target.position()).getBlock().getName());
            machines.putIfAbsent(machine.id(), machine);
        }
        return new ArrayList<>(machines.values());
    }

    public record Machine(ServerLevel level, BlockPos pos, Component name) {
        public String id() {
            return level.dimension().location() + "@" + pos.asLong();
        }

        /** Same fields as a pattern provider entry, so the status menu lists both the same way. */
        public CompoundTag describe(HolderLookup.Provider registries) {
            var tag = new CompoundTag();
            tag.putString("id", id());
            tag.putString("name", Component.Serializer.toJson(name, registries));
            tag.putString("dimension", level.dimension().location().toString());
            tag.putLong("position", pos.asLong());
            return tag;
        }

        /** @return null when the machine's menu opened, otherwise the translation key of the failure */
        @Nullable
        public String open(ServerPlayer player) {
            // The client builds the screen from its own copy of the block, so it must be in a chunk the player sees.
            if (level != player.level() || !level.hasChunkAt(pos)
                || !player.getChunkTrackingView().contains(new ChunkPos(pos))
                || !level.mayInteract(player, pos)) return OPEN_UNAVAILABLE;
            var blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null || blockEntity.isRemoved()) return OPEN_UNAVAILABLE;
            var previous = player.containerMenu;
            ECORemoteMachineReach.mark(player, level, pos);
            try {
                level.getBlockState(pos).useWithoutItem(level, player,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            } catch (RuntimeException | LinkageError unsupported) {
                ECORemoteMachineReach.clear(player);
                return OPEN_UNAVAILABLE;
            }
            if (player.containerMenu == previous) {
                ECORemoteMachineReach.clear(player);
                return OPEN_UNAVAILABLE;
            }
            ECORemoteMachineReach.bind(player, player.containerMenu);
            return null;
        }
    }
}
