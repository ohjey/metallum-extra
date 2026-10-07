package com.metallumextra;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

public final class MetallumExtra implements ClientModInitializer {
    public static final String MOD_ID = "metallum-extra";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        ExtraConfig config = ExtraConfig.get();
        String extraVersion = version(MOD_ID);
        String metallumVersion = version("metallum");
        List<String> gcs = ManagementFactory.getGarbageCollectorMXBeans().stream().map(GarbageCollectorMXBean::getName).toList();
        LOGGER.info("[Metallum Extra] {} on Metallum {} | Java {} | GC {} | max heap {} MB | cores {} | macOS {}",
                extraVersion,
                metallumVersion,
                Runtime.version(),
                gcs,
                Runtime.getRuntime().maxMemory() / (1024 * 1024),
                Runtime.getRuntime().availableProcessors(),
                System.getProperty("os.version"));
        LOGGER.info("[Metallum Extra] profiler={} directBufferUpload={} nonBlockingPresent={}", config.profilerEnabled, config.directBufferUpload, config.nonBlockingPresent);
        FrameProfiler.touch();
    }

    private static String version(final String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(ModContainer::getMetadata)
                .map(m -> m.getVersion().getFriendlyString())
                .orElse("?");
    }
}
