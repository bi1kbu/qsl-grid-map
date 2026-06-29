package com.bi1kbu.qslgridmap.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bi1kbu.qslgridmap.front.QslGridMapPageRenderService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;

class QslGridMapPublicPageFilterTest {

    private final ReactiveSettingFetcher settingFetcher = mock(ReactiveSettingFetcher.class);
    private final QslGridMapPublicPageFilter filter = new QslGridMapPublicPageFilter(
        new QslGridMapPageRenderService(),
        settingFetcher
    );

    @Test
    void rendersPublicMapPageBeforeSecurityChain() {
        var settings = new QslGridMapPageRenderService.TiandituSettings();
        settings.setAppKey("official-key-123");
        when(settingFetcher.fetch("tianditu", QslGridMapPageRenderService.TiandituSettings.class))
            .thenReturn(Mono.just(settings));
        var mapViewSettings = new QslGridMapPageRenderService.MapViewSettings();
        mapViewSettings.setDefaultCenterGrid("OM89");
        mapViewSettings.setDefaultZoom(3);
        mapViewSettings.setMinZoom(3);
        mapViewSettings.setMaxZoom(8);
        when(settingFetcher.fetch("map", QslGridMapPageRenderService.MapViewSettings.class))
            .thenReturn(Mono.just(mapViewSettings));
        var request = MockServerHttpRequest.get(
            "/apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page"
                + "?embed=1&sceneType=QSO&dateFrom=2026-01-01&grid=om89&limit=500")
            .build();
        var exchange = MockServerWebExchange.from(request);
        var chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertThat(chainCalled).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(exchange.getResponse().getHeaders().getContentType().toString())
            .isEqualTo("text/html;charset=UTF-8");
        assertThat(exchange.getResponse().getBodyAsString().block())
            .contains("QSL 通联网格地图")
            .contains("qsl-map-panel")
            .contains("`${API_URL}?sceneType=QSO`")
            .contains("configured: true")
            .contains("appKey: \"official-key-123\"")
            .contains("centerGrid: \"OM89\"")
            .contains("minZoom: Number(\"3\") || 3")
            .contains("maxZoom: Number(\"8\") || 8")
            .doesNotContain("qsl-grid-filter")
            .doesNotContain("qsl-list-panel")
            .doesNotContain("dateFrom")
            .doesNotContain("limit（上限）");
    }

    @Test
    void leavesOtherRequestsToHaloChain() {
        var request = MockServerHttpRequest
            .method(HttpMethod.POST, "/apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page")
            .build();
        var exchange = MockServerWebExchange.from(request);
        var chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertThat(chainCalled).isTrue();
    }
}
