package com.bi1kbu.qslgridmap.front;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QslGridMapPageRenderServiceTest {

    private final QslGridMapPageRenderService service = new QslGridMapPageRenderService();

    @Test
    void renderOnlyReadsQslManagementPublicQsoGridApi() {
        var html = service.render(new QslGridMapPageRenderService.RenderOptions(
            "SWL",
            "2026-01-01",
            "2026-12-31",
            "OM89",
            "500",
            "1",
            "embed-1"
        ));

        assertThat(html).contains("/apis/api.qsl-management.bi1kbu.com/v1alpha1/qso-public/grids");
        assertThat(html).contains("`${API_URL}?sceneType=QSO`");
        assertThat(html).doesNotContain("/apis/qsl-grid-map.bi1kbu.com/v1alpha1/grids");
        assertThat(html).doesNotContain("tile.openstreetmap.org");
        assertThat(html).doesNotContain("dateFrom");
        assertThat(html).doesNotContain("dateTo");
        assertThat(html).doesNotContain("limit（上限）");
        assertThat(html).doesNotContain("qsl-list-panel");
        assertThat(html).doesNotContain("qsl-grid-filter");
        assertThat(html).contains("qsl-map-panel");
        assertThat(html).contains("position: fixed;");
        assertThat(html).contains("inset: 0;");
    }

    @Test
    void renderUsesTiandituTileServiceAndBackendZoomSettings() {
        var html = service.render(
            QslGridMapPageRenderService.RenderOptions.empty(),
            new QslGridMapPageRenderService.MapSettings("test-key-123", "secret-value", "OM89", true, "#16a34a",
                3, 3, 8)
        );

        assertThat(html).contains("https://t{s}.tianditu.gov.cn/DataServer?T=vec_w");
        assertThat(html).contains("https://t{s}.tianditu.gov.cn/DataServer?T=cva_w");
        assertThat(html).contains("tk=${TIANDITU_CONFIG.appKey}");
        assertThat(html).contains("appKey: \"test-key-123\"");
        assertThat(html).contains("centerGrid: \"OM89\"");
        assertThat(html).contains("showHomeGrid: true");
        assertThat(html).contains("homeGridBorderColor: \"#16a34a\"");
        assertThat(html).contains("zoom: Number(\"3\") || 3");
        assertThat(html).contains("minZoom: Number(\"3\") || 3");
        assertThat(html).contains("maxZoom: Number(\"8\") || 8");
        assertThat(html).contains("setView(defaultMapCenter(), DEFAULT_MAP_VIEW.zoom)");
        assertThat(html).doesNotContain("fitBounds");
        assertThat(html).contains("minZoom: DEFAULT_MAP_VIEW.minZoom");
        assertThat(html).contains("maxZoom: DEFAULT_MAP_VIEW.maxZoom");
        assertThat(html).contains("state.map.attributionControl.setPrefix(false);");
        assertThat(html).contains("dashArray: \"6 4\"");
        assertThat(html).contains("interactive: true");
        assertThat(html).contains("本台网格：");
        assertThat(html).contains("state.homeGridLayer.bringToFront();");
        assertThat(html).doesNotContain("secret-value");
    }

    @Test
    void renderCanHideHomeGridLayer() {
        var tiandituSettings = new QslGridMapPageRenderService.TiandituSettings();
        var mapViewSettings = new QslGridMapPageRenderService.MapViewSettings();
        mapViewSettings.setShowHomeGrid(false);

        var settings = QslGridMapPageRenderService.normalizeSettings(tiandituSettings, mapViewSettings);
        var html = service.render(QslGridMapPageRenderService.RenderOptions.empty(), settings);

        assertThat(html).contains("showHomeGrid: false");
        assertThat(html).contains("if (DEFAULT_MAP_VIEW.showHomeGrid)");
    }

    @Test
    void renderFallsBackInvalidMapViewSettings() {
        var tiandituSettings = new QslGridMapPageRenderService.TiandituSettings();
        var mapViewSettings = new QslGridMapPageRenderService.MapViewSettings();
        mapViewSettings.setDefaultCenterGrid("bad");
        mapViewSettings.setDefaultZoom(99);
        mapViewSettings.setMinZoom(9);
        mapViewSettings.setMaxZoom(2);
        mapViewSettings.setHomeGridBorderColor("red");

        var settings = QslGridMapPageRenderService.normalizeSettings(tiandituSettings, mapViewSettings);
        var html = service.render(QslGridMapPageRenderService.RenderOptions.empty(), settings);

        assertThat(html).contains("centerGrid: \"OM89\"");
        assertThat(html).contains("showHomeGrid: true");
        assertThat(html).contains("homeGridBorderColor: \"#dc2626\"");
        assertThat(html).contains("zoom: Number(\"8\") || 3");
        assertThat(html).contains("minZoom: Number(\"3\") || 3");
        assertThat(html).contains("maxZoom: Number(\"8\") || 8");
    }

    @Test
    void normalizeDefaultZoomWithinBackendZoomRange() {
        var tiandituSettings = new QslGridMapPageRenderService.TiandituSettings();
        var mapViewSettings = new QslGridMapPageRenderService.MapViewSettings();
        mapViewSettings.setMinZoom(5);
        mapViewSettings.setMaxZoom(8);

        var settings = QslGridMapPageRenderService.normalizeSettings(tiandituSettings, mapViewSettings);
        var html = service.render(QslGridMapPageRenderService.RenderOptions.empty(), settings);

        assertThat(html).contains("zoom: Number(\"5\") || 3");
        assertThat(html).contains("minZoom: Number(\"5\") || 3");
        assertThat(html).contains("maxZoom: Number(\"8\") || 8");
    }
}
