package cn.dancingsnow.neoecoae.api.multiblock;

import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.extension.ECOExtensionRegistry;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Semantic part classification. Runtime cores/workers must still provide their corresponding ECO block entity. */
public final class ECOMultiblockParts {
    private static final ECOExtensionRegistry<Provider> REGISTRY = new ECOExtensionRegistry<>();
    private ECOMultiblockParts() {}
    public enum Role {
        CASING, INTERFACE, TRANSMITTER, COMPUTATION_DRIVE, COMPUTATION_PARALLEL_CORE,
        COMPUTATION_THREADING_CORE, COOLING_CONTROLLER, STORAGE_DRIVE, ENERGY_CELL,
        CRAFTING_WORKER, CRAFTING_PARALLEL_CORE, PATTERN_STORAGE, VENT
    }
    public enum DriveRow { UPPER, LOWER }
    public record Context(@Nullable Level level, BlockPos position, BlockState state, @Nullable BlockEntity blockEntity) {}
    public record Part(Set<Role> roles, @Nullable IECOTier tier, @Nullable DriveRow driveRow, int order) {
        public Part { roles = Set.copyOf(roles); }
        public static Part of(Role role, @Nullable IECOTier tier) { return new Part(Set.of(role), tier, null, 0); }
    }
    @FunctionalInterface public interface Provider { @Nullable Part describe(Context context); }
    public static void register(ResourceLocation id, int priority, Provider provider) { REGISTRY.register(id, priority, provider); }
    public static boolean unregister(ResourceLocation id) { return REGISTRY.unregister(id); }
    public static long revision() { return REGISTRY.revision(); }

    @Nullable public static Part describe(Context context) {
        for (var entry : REGISTRY.entries()) {
            Part part = entry.extension().describe(context);
            if (part != null) return part;
        }
        return context.state().getBlock() instanceof Provider provider ? provider.describe(context) : null;
    }

    public static Context context(Level level, BlockPos pos) {
        return new Context(level, pos.immutable(), level.getBlockState(pos), level.getBlockEntity(pos));
    }
    public static boolean matches(Context context, Role role, boolean fallback) {
        Part part = describe(context);
        return part == null ? fallback : part.roles().contains(role);
    }
    @Nullable public static IECOTier tier(Context context) {
        Part part = describe(context);
        if (part != null && part.tier() != null) return part.tier();
        return context.blockEntity() instanceof cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity<?, ?> member
            ? member.getTier() : null;
    }
    public static DriveRow driveRow(Context context, DriveRow fallback) {
        Part part = describe(context);
        return part == null || part.driveRow() == null ? fallback : part.driveRow();
    }
    public static int order(Context context) {
        Part part = describe(context);
        return part == null ? 0 : part.order();
    }

    public static int order(BlockEntity member) {
        BlockState state = member.getBlockState();
        return state == null ? 0 : order(new Context(member.getLevel(), member.getBlockPos(), state, member));
    }

    public static boolean matchesTiered(Context context, Role role, IECOTier hostTier, boolean fallback,
                                         net.minecraft.core.Direction facing, Class<? extends BlockEntity> runtimeType) {
        IECOTier memberTier = tier(context);
        return runtimeType.isInstance(context.blockEntity()) && matches(context, role, fallback)
            && memberTier != null && hostTier.supportsComponentTier(memberTier)
            && context.state().getOptionalValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
                .orElse(null) == facing;
    }

    /** Role-based alternatives to the built-in block classes, shared by formation predicates. */
    @Nullable public static Boolean acceptsExpected(Context context, BlockState expected) {
        Part part = describe(context);
        if (part == null) return null;
        Role role = builtinRole(expected);
        return role == null ? null : part.roles().contains(role)
            && ECOMultiblockRequirements.matchingProperties(expected, context.state());
    }

    @Nullable private static Role builtinRole(BlockState expected) {
        var block = expected.getBlock();
        if (block instanceof cn.dancingsnow.neoecoae.blocks.ECOMachineCasing<?>) return Role.CASING;
        if (block instanceof cn.dancingsnow.neoecoae.blocks.ECOMachineInterface<?>) return Role.INTERFACE;
        if (block instanceof cn.dancingsnow.neoecoae.blocks.computation.ECOComputationTransmitter) return Role.TRANSMITTER;
        if (block instanceof cn.dancingsnow.neoecoae.blocks.computation.ECOComputationDrive) return Role.COMPUTATION_DRIVE;
        if (block instanceof cn.dancingsnow.neoecoae.blocks.computation.ECOComputationParallelCore) return Role.COMPUTATION_PARALLEL_CORE;
        if (block instanceof cn.dancingsnow.neoecoae.blocks.computation.ECOComputationThreadingCore) return Role.COMPUTATION_THREADING_CORE;
        if (block instanceof cn.dancingsnow.neoecoae.blocks.computation.ECOComputationCoolingController) return Role.COOLING_CONTROLLER;
        if (block instanceof cn.dancingsnow.neoecoae.blocks.storage.ECODriveBlock) return Role.STORAGE_DRIVE;
        if (block instanceof cn.dancingsnow.neoecoae.blocks.storage.ECOEnergyCellBlock) return Role.ENERGY_CELL;
        if (block instanceof cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingWorker) return Role.CRAFTING_WORKER;
        if (block instanceof cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingParallelCore) return Role.CRAFTING_PARALLEL_CORE;
        if (block instanceof cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingPatternBus) return Role.PATTERN_STORAGE;
        if (block instanceof cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingVent
            || block instanceof cn.dancingsnow.neoecoae.blocks.storage.ECOStorageVentBlock) return Role.VENT;
        return null;
    }
}
