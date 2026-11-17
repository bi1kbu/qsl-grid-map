package com.bi1kbu.qslgridmap.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bi1kbu.qslgridmap.front.QslGridMapPageRenderService;
import com.bi1kbu.qslgridmap.front.QslGridMapDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatchers;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;

class QslGridMapPublicPageFilterTest {

    private final ReactiveSettingFetcher settingFetcher = mock(ReactiveSettingFetcher.class);
    private final QslGridMapDataSource sources = mock(QslGridMapDataSource.class);
    private final QslGridMapPublicPageFilter filter = new QslGridMapPublicPageFilter(
        new QslGridMapPageRenderService(),
        settingFetcher, sources
    );

    @BeforeEach
    void legacySource() {
        when(sources.resolve(ArgumentMatchers.any(), ArgumentMatchers.anyString()))
            .thenAnswer(invocation -> Mono.just(QslGridMapDataSource.Source.legacy(invocation.getArgument(1))));
    }

    @Test
    void rendersPublicMapPageBeforeSecurityChain() {
        var settings = new QslGridMapPageRenderService.TiandituSettings();
        settings.setAppKey("official-key-123");
        when(settingFetcher.fetch("tianditu", QslGridMapPageRenderService.TiandituSettings.class))
            .thenReturn(Mono.just(settings));
        var mapViewSettings = new QslGridMapPageRenderService.MapViewSettings();
        mapViewSettings.setDefaultCenterGrid("OM89");
        settings.setDefaultZoom(3);
        settings.setMinZoom(3);
        settings.setMaxZoom(8);
        when(settingFetcher.fetch("map", QslGridMapPageRenderService.MapViewSettings.class))
            .thenReturn(Mono.just(mapViewSettings));
        when(settingFetcher.fetch("openfreemap", QslGridMapPageRenderService.ZoomSettings.class))
            .thenReturn(Mono.empty());
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
            .contains("qso-public/grids?sceneType=QSO")
            .contains("configured: true")
            .contains("appKey: \"official-key-123\"")
            .contains("centerGrid: \"OM89\"")
            .contains("minZoom: 3")
            .contains("maxZoom: 8")
            .doesNotContain("qsl-grid-filter")
            .doesNotContain("qsl-list-panel")
            .doesNotContain("dateFrom")
            .doesNotContain("limit（上限）");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/gridmap",
        "/gridmap?url=https://example.com&redirect=//example.com&sceneType=SWL&embed=1"
    })
    void redirectsShortLinkToFixedPublicPageWithoutReadingSettings(String path) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        var chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertThat(chainCalled).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(exchange.getResponse().getHeaders().getLocation()).hasToString(
            "/apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page");
        assertThat(exchange.getResponse().getBodyAsString().block()).isEmpty();
        verifyNoInteractions(settingFetcher);
    }

    @Test
    void shortLinkPreservesApplicationContextPath() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/blog/gridmap")
            .contextPath("/blog").build());

        filter.filter(exchange, chain -> Mono.error(new AssertionError("短链接不应进入后续过滤链")))
            .block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(exchange.getResponse().getHeaders().getLocation()).hasToString(
            "/blog/apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page");
        verifyNoInteractions(settingFetcher);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/gridmap/BI1KBU", "/gridmap/all", "/gridmap"})
    void modernShortPathsRenderDirectlyAndIgnoreQueryOverrides(String path) {
        when(sources.resolve(ArgumentMatchers.any(), ArgumentMatchers.anyString()))
            .thenAnswer(invocation -> {
                String target = invocation.getArgument(0);
                String query = target.isEmpty() ? "" : "?callsign=" + target.toLowerCase(java.util.Locale.ROOT);
                return Mono.just(new QslGridMapDataSource.Source(true,
                    invocation.getArgument(1) + "/apis/api.qsl.halo.run/v1alpha1/public-grid-map" + query));
            });
        emptySettings();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
            .get("/blog" + path + "?callsign=other&url=https://evil.invalid").contextPath("/blog").build());

        filter.filter(exchange, chain -> Mono.error(new AssertionError("短路径应由地图处理"))).block();

        assertThat(exchange.getResponse().getHeaders().getLocation()).isNull();
        assertThat(exchange.getResponse().getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(exchange.getResponse().getBodyAsString().block())
            .contains("const MODERN_API = true")
            .contains("/blog/apis/api.qsl.halo.run/v1alpha1/public-grid-map")
            .doesNotContain("callsign=other", "evil.invalid");
    }

    @Test
    void versionFailureCannotRenderLegacyPage() {
        when(sources.resolve(ArgumentMatchers.any(), ArgumentMatchers.anyString()))
            .thenReturn(Mono.error(new IllegalStateException("private error")));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/gridmap/BI1KBU").build());

        filter.filter(exchange, chain -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getBodyAsString().block()).doesNotContain("private error", "qso-public");
        verifyNoInteractions(settingFetcher);
    }

    private void emptySettings() {
        when(settingFetcher.fetch("tianditu", QslGridMapPageRenderService.TiandituSettings.class)).thenReturn(Mono.empty());
        when(settingFetcher.fetch("map", QslGridMapPageRenderService.MapViewSettings.class)).thenReturn(Mono.empty());
        when(settingFetcher.fetch("openfreemap", QslGridMapPageRenderService.ZoomSettings.class)).thenReturn(Mono.empty());
    }

    @ParameterizedTest
    @CsvSource({
        "POST, /apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page",
        "POST, /gridmap",
        "PUT, /gridmap",
        "PATCH, /gridmap",
        "DELETE, /gridmap",
        "HEAD, /gridmap",
        "OPTIONS, /gridmap",
        "GET, /gridmap/",
        "GET, /gridmap-other",
        "GET, /gridmap/bi1kbu/extra",
        "GET, /gridmap/bi1kbu%2Fall",
        "POST, /gridmap/all",
        "GET, /Gridmap",
        "GET, /apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page/private"
    })
    void leavesOtherRequestsToHaloChain(String method, String path) {
        var request = MockServerHttpRequest
            .method(HttpMethod.valueOf(method), path)
            .build();
        var exchange = MockServerWebExchange.from(request);
        var chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertThat(chainCalled).isTrue();
        assertThat(exchange.getResponse().getHeaders().getLocation()).isNull();
        verifyNoInteractions(settingFetcher);
    }
}
