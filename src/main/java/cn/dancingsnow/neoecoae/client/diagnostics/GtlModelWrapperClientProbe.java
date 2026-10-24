package cn.dancingsnow.neoecoae.client.diagnostics;

import cn.dancingsnow.neoecoae.client.model.CoolingControllerBakedModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraftforge.client.event.ModelEvent;
import org.slf4j.LoggerFactory;

import java.net.URLClassLoader;
import java.nio.file.Path;

/** Opt-in reproduction with the actual model decorator from an installed GTLCore JAR. */
public final class GtlModelWrapperClientProbe {
    private static URLClassLoader wrapperLoader;

    private GtlModelWrapperClientProbe() {}

    public static void wrap(ModelEvent.ModifyBakingResult event) {
        String jar = System.getProperty("neoecoae.gtlModelWrapperJar", "");
        if (jar.isBlank()) return;
        try {
            wrapperLoader = new URLClassLoader(new java.net.URL[]{Path.of(jar).toUri().toURL()},
                GtlModelWrapperClientProbe.class.getClassLoader());
            var type = Class.forName("org.gtlcore.gtlcore.client.bloom.EmissiveAliases$Model", true, wrapperLoader);
            var constructor = type.getDeclaredConstructor(BakedModel.class);
            constructor.setAccessible(true);
            int count = 0;
            for (var entry : event.getModels().entrySet()) {
                if (!(entry.getValue() instanceof CoolingControllerBakedModel)) continue;
                entry.setValue((BakedModel) constructor.newInstance(entry.getValue()));
                count++;
            }
            if (count != 24) throw new IllegalStateException("Expected 24 formed cooling models, got " + count);
            LoggerFactory.getLogger(GtlModelWrapperClientProbe.class)
                .info("GTL_MODEL_WRAPPER installed actual EmissiveAliases decorator on {} formed models", count);
        } catch (ReflectiveOperationException | java.net.MalformedURLException failure) {
            throw new IllegalStateException("Unable to reproduce the installed GTL model decorator", failure);
        }
    }
}
