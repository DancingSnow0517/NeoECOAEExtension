package cn.dancingsnow.neoecoae.api.storage;

import cn.dancingsnow.neoecoae.api.extension.ECOExtensionRegistry;
import java.util.Objects;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/** Server-authored channel presentation. Aliases share a distribution segment; storage accounting is unchanged. */
public final class ECOStorageChannelDisplays {
    private static final ECOExtensionRegistry<Display> REGISTRY = new ECOExtensionRegistry<>();
    private ECOStorageChannelDisplays() {}
    public record Display(Identifier channel, Set<Identifier> aliases, Component name, int color,
                          @Nullable Identifier icon, @Nullable Identifier swatch, int order) {
        public Display {
            Objects.requireNonNull(channel, "channel");
            aliases = Set.copyOf(aliases);
            name = Objects.requireNonNull(name, "name").copy();
        }
        public boolean matches(Identifier resource) { return channel.equals(resource) || aliases.contains(resource); }
    }
    public static void register(Identifier id, int priority, Display display) { REGISTRY.register(id, priority, display); }
    public static boolean unregister(Identifier id) { return REGISTRY.unregister(id); }
    public static long revision() { return REGISTRY.revision(); }
    @Nullable public static Display resolve(@Nullable Identifier resource) {
        if (resource == null) return null;
        for (var entry : REGISTRY.entries()) if (entry.extension().matches(resource)) return entry.extension();
        return null;
    }
}
