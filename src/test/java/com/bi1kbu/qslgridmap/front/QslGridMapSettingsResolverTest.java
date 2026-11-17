package com.bi1kbu.qslgridmap.front;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import tools.jackson.databind.json.JsonMapper;

class QslGridMapSettingsResolverTest {

    @Test
    void haloJsonMapperDeserializesInheritedTiandituZoomFields() {
        var mapper = JsonMapper.builder().build();
        var settings = mapper.convertValue(Map.of(
            "appKey", "test-key-123", "secretKey", "test-secret-456",
            "defaultZoom", 6, "minZoom", 5, "maxZoom", 7
        ), QslGridMapPageRenderService.TiandituSettings.class);

        assertThat(settings.getAppKey()).isEqualTo("test-key-123");
        assertThat(settings.getSecretKey()).isEqualTo("test-secret-456");
        assertThat(settings.getDefaultZoom()).isEqualTo(6);
        assertThat(settings.getMinZoom()).isEqualTo(5);
        assertThat(settings.getMaxZoom()).isEqualTo(7);
    }

    @Test
    void obsoleteMapGroupZoomFieldsDoNotFailDeserializationOrOverrideServiceDefaults() {
        var mapper = JsonMapper.builder().build();
        // 持久化配置中已移除的字段必须被忽略，不能恢复为旧缩放配置读取分支。
        var common = mapper.convertValue(Map.of(
            "provider", "openfreemap", "defaultCenterGrid", "FN20",
            "showHomeGrid", false, "homeGridBorderColor", "#16a34a",
            "defaultZoom", 12, "minZoom", 10, "maxZoom", 15
        ), QslGridMapPageRenderService.MapViewSettings.class);

        var settings = QslGridMapPageRenderService.normalizeSettings(null, common, null);

        assertThat(settings.provider()).isEqualTo("openfreemap");
        var expected = new QslGridMapPageRenderService.MapDisplaySettings("FN20", false, "#16a34a", 3, 3, 8);
        assertThat(settings.tianditu()).isEqualTo(expected);
        assertThat(settings.openfreemap()).isEqualTo(expected);
    }

    @Test
    void eachPageRequestReadsThePersistedProviderAndIndependentOpenFreeMapGroup() {
        var fetcher = mock(ReactiveSettingFetcher.class);
        var credentials = new QslGridMapPageRenderService.TiandituSettings();
        credentials.setAppKey("test-key-123");
        var tianditu = new QslGridMapPageRenderService.MapViewSettings();
        tianditu.setProvider("openfreemap");
        credentials.setMinZoom(2);
        credentials.setMaxZoom(8);
        var first = new QslGridMapPageRenderService.ZoomSettings();
        first.setMinZoom(5);
        first.setMaxZoom(7);
        var second = new QslGridMapPageRenderService.ZoomSettings();
        second.setMinZoom(10);
        second.setMaxZoom(14);
        when(fetcher.fetch("tianditu", QslGridMapPageRenderService.TiandituSettings.class))
            .thenReturn(Mono.just(credentials));
        when(fetcher.fetch("map", QslGridMapPageRenderService.MapViewSettings.class))
            .thenReturn(Mono.just(tianditu));
        when(fetcher.fetch("openfreemap", QslGridMapPageRenderService.ZoomSettings.class))
            .thenReturn(Mono.just(first), Mono.just(second));

        var initial = QslGridMapSettingsResolver.mapSettings(fetcher).block();
        var refreshed = QslGridMapSettingsResolver.mapSettings(fetcher).block();

        assertThat(initial).isNotNull();
        assertThat(initial.provider()).isEqualTo("openfreemap");
        assertThat(initial.openfreemap().minZoom()).isEqualTo(5);
        assertThat(initial.openfreemap().maxZoom()).isEqualTo(7);
        assertThat(refreshed).isNotNull();
        assertThat(refreshed.openfreemap().minZoom()).isEqualTo(10);
        assertThat(refreshed.openfreemap().maxZoom()).isEqualTo(14);
        assertThat(refreshed.tianditu()).isEqualTo(initial.tianditu());
        verify(fetcher, times(2)).fetch("openfreemap", QslGridMapPageRenderService.ZoomSettings.class);
    }

    @Test
    void emptyOpenFreeMapGroupDoesNotMakeOtherGroupsDisappear() {
        var fetcher = mock(ReactiveSettingFetcher.class);
        var tianditu = new QslGridMapPageRenderService.MapViewSettings();
        tianditu.setProvider("openfreemap");
        tianditu.setDefaultCenterGrid("FN20");
        when(fetcher.fetch("tianditu", QslGridMapPageRenderService.TiandituSettings.class))
            .thenReturn(Mono.empty());
        when(fetcher.fetch("map", QslGridMapPageRenderService.MapViewSettings.class))
            .thenReturn(Mono.just(tianditu));
        when(fetcher.fetch("openfreemap", QslGridMapPageRenderService.ZoomSettings.class))
            .thenReturn(Mono.empty());

        var settings = QslGridMapSettingsResolver.mapSettings(fetcher).block();

        assertThat(settings).isNotNull();
        assertThat(settings.provider()).isEqualTo("openfreemap");
        assertThat(settings.tianditu().defaultCenterGrid()).isEqualTo("FN20");
        assertThat(settings.openfreemap()).isEqualTo(new QslGridMapPageRenderService.MapDisplaySettings(
            "FN20", true, "#dc2626", 3, 3, 8));
    }
}
