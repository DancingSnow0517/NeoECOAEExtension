package cn.dancingsnow.neoecoae.client.texture;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.google.common.base.Suppliers;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.renderer.texture.atlas.sources.PalettedPermutations;
import net.minecraft.client.resources.metadata.animation.AnimationFrame;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.FastColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Recolors the shared masks on resource reload; the atlas animates working lights without section rebuilds. */
@EventBusSubscriber(modid = NeoECOAE.MOD_ID, value = Dist.CLIENT)
public record WorkstationSpriteSource(Map<String, ResourceLocation> palettes) implements SpriteSource {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    public static final MapCodec<WorkstationSpriteSource> CODEC = Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC)
        .fieldOf("palettes").xmap(WorkstationSpriteSource::new, WorkstationSpriteSource::palettes);
    private static final SpriteSourceType TYPE = new SpriteSourceType(CODEC);
    private static final String ROOT = "block/working_station/controller_formed/";
    private static final List<Layer> LAYERS = List.of(
        new Layer("controller_formed_screen_on/dark", 0, -1),
        new Layer("controller_formed_screen_on/middle", 1, -1),
        new Layer("controller_formed_screen_on/light", 2, -1),
        new Layer("controller_formed_light/dark", 3, 5),
        new Layer("controller_formed_light/light", 4, 6));

    public WorkstationSpriteSource {
        palettes = Map.copyOf(palettes);
    }

    @SubscribeEvent
    public static void register(RegisterSpriteSourceTypesEvent event) {
        event.register(NeoECOAE.id("workstation_palette"), TYPE);
    }

    @Override
    public SpriteSourceType type() {
        return TYPE;
    }

    @Override
    public void run(ResourceManager manager, Output output) {
        // Palette strips are inputs, not atlas sprites: their one-pixel height would lower the mipmap limit.
        output.removeAll(palettes.values()::contains);
        palettes.forEach((name, palette) -> {
            // Scope the cache to this reload so resource-pack changes replace every palette.
            Supplier<int[]> colors = Suppliers.memoize(() -> {
                try {
                    int[] loaded = PalettedPermutations.loadPaletteEntryFromImage(manager, palette);
                    if (loaded.length < 7) throw new IllegalArgumentException("Expected at least seven palette entries");
                    return loaded;
                } catch (IllegalArgumentException exception) {
                    LOGGER.warn("Unable to color workstation with {}; using the original masks", palette, exception);
                    return null;
                }
            });
            for (Layer layer : LAYERS) {
                var texture = TEXTURE_ID_CONVERTER.idToFile(NeoECOAE.id(ROOT + layer.path()));
                manager.getResource(texture).ifPresentOrElse(resource -> {
                    add(manager, output, resource, name, layer, colors, false);
                    if (layer.brightIndex() >= 0) add(manager, output, resource, name, layer, colors, true);
                }, () -> LOGGER.warn("Missing workstation mask {}", texture));
            }
        });
    }

    private static void add(ResourceManager manager, Output output, Resource mask, String palette, Layer layer,
                            Supplier<int[]> colors, boolean working) {
        var sprite = NeoECOAE.id(ROOT + "colored/" + palette + "/" + layer.path() + (working ? "_working" : ""));
        // An explicitly supplied sprite still takes precedence over the generated colors.
        var replacement = manager.getResource(TEXTURE_ID_CONVERTER.idToFile(sprite));
        if (replacement.isPresent()) {
            output.add(sprite, replacement.get());
        } else {
            output.add(sprite, loader -> loader.loadSprite(sprite, mask, (id, size, image, metadata) -> {
                int[] paletteColors = colors.get();
                if (paletteColors == null) return new SpriteContents(id, size, image, metadata);
                int color = paletteColors[layer.index()];
                if (working) return blinking(id, size, image, metadata, color, paletteColors[layer.brightIndex()]);
                // The supplied layers are gray masks, not white: replace RGB instead of multiplying it again.
                image.applyToAllPixels(pixel -> color(pixel, color));
                return new SpriteContents(id, size, image, metadata);
            }));
        }
    }

    private static SpriteContents blinking(ResourceLocation id, FrameSize size, NativeImage mask,
                                           ResourceMetadata metadata, int dim, int bright) {
        NativeImage image = null;
        try (mask) {
            image = new NativeImage(mask.getWidth(), Math.multiplyExact(mask.getHeight(), 2), false);
            for (int y = 0; y < mask.getHeight(); y++) {
                for (int x = 0; x < mask.getWidth(); x++) {
                    int pixel = mask.getPixelRGBA(x, y);
                    image.setPixelRGBA(x, y, color(pixel, dim));
                    image.setPixelRGBA(x, y + mask.getHeight(), color(pixel, bright));
                }
            }
            var animation = metadata.getSection(AnimationMetadataSection.SERIALIZER).orElseGet(() ->
                new AnimationMetadataSection(List.of(), size.width(), size.height(), 15, true));
            int count = (mask.getWidth() / size.width()) * (mask.getHeight() / size.height());
            List<AnimationFrame> frames = new ArrayList<>();
            animation.forEachFrame((frame, time) -> frames.add(new AnimationFrame(frame, time)));
            if (frames.isEmpty()) {
                for (int frame = 0; frame < count; frame++) frames.add(new AnimationFrame(frame, animation.getDefaultFrameTime()));
            }
            int dimFrames = frames.size();
            for (int frame = 0; frame < dimFrames; frame++) {
                var original = frames.get(frame);
                frames.add(new AnimationFrame(original.getIndex() + count, original.getTime(animation.getDefaultFrameTime())));
            }
            var animatedMetadata = new ResourceMetadata.Builder().put(AnimationMetadataSection.SERIALIZER,
                new AnimationMetadataSection(frames, size.width(), size.height(), animation.getDefaultFrameTime(),
                    animation.isInterpolatedFrames())).build();
            return new SpriteContents(id, size, image, animatedMetadata);
        } catch (RuntimeException | Error exception) {
            if (image != null) image.close();
            throw exception;
        }
    }

    private static int color(int mask, int palette) {
        return FastColor.ABGR32.color(FastColor.ABGR32.alpha(mask) * FastColor.ABGR32.alpha(palette) / 255, palette);
    }

    private record Layer(String path, int index, int brightIndex) { }
}
