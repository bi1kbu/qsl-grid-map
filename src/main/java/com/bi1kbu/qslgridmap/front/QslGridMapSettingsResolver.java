package com.bi1kbu.qslgridmap.front;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;

public final class QslGridMapSettingsResolver {

    private static final Logger LOG = LoggerFactory.getLogger(QslGridMapSettingsResolver.class);
    private static final String TIANDITU_GROUP = "tianditu";
    private static final String MAP_GROUP = "map";

    private QslGridMapSettingsResolver() {
    }

    public static Mono<QslGridMapPageRenderService.MapSettings> mapSettings(
        ReactiveSettingFetcher settingFetcher) {
        var tiandituSettings = settingFetcher
            .fetch(TIANDITU_GROUP, QslGridMapPageRenderService.TiandituSettings.class)
            .defaultIfEmpty(new QslGridMapPageRenderService.TiandituSettings());
        var mapViewSettings = settingFetcher
            .fetch(MAP_GROUP, QslGridMapPageRenderService.MapViewSettings.class)
            .defaultIfEmpty(new QslGridMapPageRenderService.MapViewSettings());
        return Mono.zip(tiandituSettings, mapViewSettings)
            .map(tuple -> QslGridMapPageRenderService.normalizeSettings(tuple.getT1(), tuple.getT2()))
            .defaultIfEmpty(QslGridMapPageRenderService.MapSettings.empty())
            .onErrorResume(error -> {
                LOG.warn("读取地图插件设置失败，页面将以默认配置渲染。", error);
                return Mono.just(QslGridMapPageRenderService.MapSettings.empty());
            });
    }
}
