package net.emc.emce.tasks;

import net.emc.emce.EarthMCEssentials;
import net.emc.emce.caches.Cache;
import net.emc.emce.caches.NationDataCache;
import net.emc.emce.caches.ServerDataCache;
import net.emc.emce.caches.TownDataCache;
import net.emc.emce.config.ModConfig;
import net.emc.emce.utils.EarthMCAPI;
import net.emc.emce.utils.ModUtils;
import net.emc.emce.utils.MsgUtils;
import net.minecraft.client.MinecraftClient;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TaskScheduler {
    public ScheduledExecutorService service;
    public boolean townlessRunning;
    public boolean nearbyRunning;
    public boolean cacheCheckRunning;
    public boolean newsRunning;

    private static final List<Cache<?>> CACHES = Arrays.asList(NationDataCache.INSTANCE, ServerDataCache.INSTANCE, TownDataCache.INSTANCE);

    public void start() {
        service = Executors.newScheduledThreadPool(1);
        final ModConfig config = ModConfig.instance();

        startTownless();
        startNearby();
        startNews();
        startCacheCheck();

        // Pre-fill data.
        if (config.general.enableMod) {
            if (config.townless.enabled)
                EarthMCAPI.getTownless().thenAccept(EarthMCEssentials.instance()::setTownlessResidents);
            if (config.nearby.enabled && ModUtils.isConnectedToEMC())
                EarthMCAPI.getNearby().thenAccept(EarthMCEssentials.instance()::setNearbyPlayers);
        }
    }

    public void stop() {
        service.shutdown();

        townlessRunning = false;
        nearbyRunning = false;
        newsRunning = false;
        cacheCheckRunning = false;
    }

    private void startTownless() {
        townlessRunning = true;
        final ModConfig config = ModConfig.instance();

        service.scheduleAtFixedRate(() -> {
            if (townlessRunning && config.general.enableMod && shouldRun()) {
                MsgUtils.sendDebugMessage("Starting townless task.");
                EarthMCAPI.getTownless().thenAccept(townless -> {
                    EarthMCEssentials.instance().setTownlessResidents(townless);
                    MsgUtils.sendDebugMessage("Finished townless task.");
                });
            }
        }, 0, Math.max(config.api.intervals.townless, 30), TimeUnit.SECONDS);
    }

    private void startNearby() {
        nearbyRunning = true;
        final ModConfig config = ModConfig.instance();

        service.scheduleAtFixedRate(() -> {
            if (nearbyRunning && ModUtils.isConnectedToEMC() && config.general.enableMod && shouldRun()) {
                MsgUtils.sendDebugMessage("Starting nearby task.");
                EarthMCAPI.getNearby().thenAccept(nearby -> {
                    EarthMCEssentials.instance().setNearbyPlayers(nearby);
                    MsgUtils.sendDebugMessage("Finished nearby task.");
                });
            }
        }, 0, Math.max(config.api.intervals.nearby, 10), TimeUnit.SECONDS);
    }

    private void startNews() {
        newsRunning = true;
        final ModConfig config = ModConfig.instance();

        service.scheduleAtFixedRate(() -> {
            if (newsRunning && config.general.enableMod && config.news.enabled && shouldRun()) {
                MsgUtils.sendDebugMessage("Starting news task.");
                EarthMCAPI.getNews().thenAccept(news -> {
                    EarthMCEssentials.instance().setNews(news);
                    MsgUtils.sendDebugMessage("Finished news task.");
                });
            }
        }, 0, Math.max(config.api.intervals.news, 10), TimeUnit.SECONDS);
    }

    private void startCacheCheck() {
        cacheCheckRunning = true;

        service.scheduleAtFixedRate(() -> {
            for (Cache<?> cache : CACHES)
                if (cache.needsUpdate())
                    cache.clearCache();
        }, 0, 5, TimeUnit.MINUTES);
    }

    private boolean shouldRun() {
        // Only run if the game isn't paused and the window is focused.
        return !MinecraftClient.getInstance().isPaused() && MinecraftClient.getInstance().isWindowFocused();
    }
}
