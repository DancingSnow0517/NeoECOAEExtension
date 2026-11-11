package cn.dancingsnow.neoecoae.client.texture;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.google.common.base.Suppliers;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.Codec;
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
import net.minecraft.util.FastColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Recolors the shared masks on resource reload; the atlas animates working lights without section rebuilds. */
public record WorkstationSpriteSource(Map<String, ResourceLocation> palettes) implements SpriteSource {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    public static final Codec<WorkstationSpriteSource> CODEC = Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC)
        .fieldOf("palettes").xmap(WorkstationSpriteSource::new, WorkstationSpriteSource::palettes).codec();
    private static final SpriteSourceType TYPE = cn.dancingsnow.neoecoae.mixins.client.minecraft.SpriteSourcesAccessor
        .neoecoae$register("neoecoae:workstation_palette", CODEC);
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

    public static void initialize() { /* Register before the first atlas load. */ }

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
            output.add(sprite, () -> {
                NativeImage image = null;
                try (var input = mask.open()) {
                    image = NativeImage.read(input);
                    var animation = mask.metadata().getSection(AnimationMetadataSection.SERIALIZER)
                        .orElse(AnimationMetadataSection.EMPTY);
                    var forge = mask.metadata().getSection(net.minecraftforge.client.textures.ForgeTextureMetadata.SERIALIZER).orElse(null);
                    FrameSize size = animation.calculateFrameSize(image.getWidth(), image.getHeight());
                    if (image.getWidth() % size.width() != 0 || image.getHeight() % size.height() != 0) {
                        throw new IllegalArgumentException("Invalid workstation mask frame size");
                    }
                    int[] paletteColors = colors.get();
                    if (paletteColors == null) return new SpriteContents(sprite, size, image, animation, forge);
                    int color = paletteColors[layer.index()];
                    if (working) {
                        var blinkingAnimation = mask.metadata().getSection(AnimationMetadataSection.SERIALIZER).orElseGet(() ->
                            new AnimationMetadataSection(List.of(), size.width(), size.height(), 15, true));
                        return blinking(sprite, size, image, blinkingAnimation, forge, color, paletteColors[layer.brightIndex()]);
                    }
                    image.applyToAllPixels(pixel -> color(pixel, color));
                    return new SpriteContents(sprite, size, image, animation, forge);
                } catch (java.io.IOException | RuntimeException exception) {
                    if (image != null) image.close();
                    LOGGER.warn("Unable to load workstation sprite {}", sprite, exception);
                    return null;
                }
            });
        }
    }

    private static SpriteContents blinking(ResourceLocation id, FrameSize size, NativeImage mask,
                                           AnimationMetadataSection animation, net.minecraftforge.client.textures.ForgeTextureMetadata forge, int dim, int bright) {
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
            var animated = new AnimationMetadataSection(frames, size.width(), size.height(),
                animation.getDefaultFrameTime(), animation.isInterpolatedFrames());
            return new SpriteContents(id, size, image, animated, forge);
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
