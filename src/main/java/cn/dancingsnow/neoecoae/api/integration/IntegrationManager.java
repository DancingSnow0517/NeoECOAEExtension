package cn.dancingsnow.neoecoae.api.integration;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import lombok.extern.slf4j.Slf4j;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.neoforgespi.language.ModFileScanData;

import java.lang.annotation.ElementType;
import java.util.function.Consumer;

@Slf4j
public class IntegrationManager {

    private final Multimap<String, IntegrationInstance> instances = MultimapBuilder.hashKeys().hashSetValues().build();

    public static final String INTEGRATION_NAME = "L" + Integration.class.getName().replace(".", "/") + ";";

    public void compileContent() {
        ProgressMeter meter = StartupNotificationManager.addProgressBar("Load Integrations", FMLLoader.getCurrent().getLoadingModList().getModFiles().size());
        for (ModFileInfo modFile : FMLLoader.getCurrent().getLoadingModList().getModFiles()) {
            meter.increment();
            ModFileScanData scanData = modFile.getFile().getScanResult();
            for (ModFileScanData.AnnotationData annotation : scanData.getAnnotations()) {
                if (annotation.annotationType().getDescriptor().equals(INTEGRATION_NAME) && annotation.targetType() == ElementType.TYPE) {
                    String modid = (String) annotation.annotationData().get("value");
                    log.info("Considering integration {} for {}", annotation.memberName(), modid);
                    IntegrationInstance instance = new IntegrationInstance(
                        modid,
                        annotation.memberName()
                    );
                    this.instances.put(modid, instance);
                }
            }
        }
        StartupNotificationManager.popBar(meter);
    }

    public void load(String modid) {
        load(modid, false);
    }

    public void loadClient(String modid) {
        load(modid, true);
    }

    private void load(String modid, boolean client) {
        for (IntegrationInstance instance : instances.get(modid)) {
            instance.newInstance();
            log.info("Loading {}integration {} for {}.", client ? "client " : "", instance.instance(), modid);
            if (client) {
                instance.invokeClient();
            } else {
                instance.invoke();
            }
        }
    }

    public void loadAllIntegrations() {
        loadAll(this::load);
    }

    public void loadAllClientIntegrations() {
        loadAll(this::loadClient);
    }

    private void loadAll(Consumer<String> loader) {
        for (String key : instances.keySet()) {
            if (cn.dancingsnow.neoecoae.NeoECOAE.UNPORTED_INTEGRATIONS.contains(key)) {
                // 26.1 port: compiled against the 1.21.1 API of this mod only; never activate it.
                log.warn("Skipping integration for {}: not yet ported to 26.1", key);
                continue;
            }
            if (FMLLoader.getCurrent().getLoadingModList().getMods().stream().anyMatch(it -> it.getModId().equals(key))) {
                loader.accept(key);
            }
        }
    }
}
