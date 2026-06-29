package com.bi1kbu.qslgridmap.front;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QslGridMapPageRenderServiceTest {

    private final QslGridMapPageRenderService service = new QslGridMapPageRenderService();

    @Test
    void renderOnlyReadsQslManagementPublicGridApi() {
        var html = service.render(new QslGridMapPageRenderService.RenderOptions(
            "QSO",
            "2026-01-01",
            "2026-12-31",
            "OM89",
            "500",
            "1",
            "embed-1"
        ));

        assertThat(html).contains("/apis/api.qsl-management.bi1kbu.com/v1alpha1/qso-public/grids");
        assertThat(html).doesNotContain("/apis/qsl-grid-map.bi1kbu.com/v1alpha1/grids");
        assertThat(html).doesNotContain("tile.openstreetmap.org");
        assertThat(html).contains("QSL 通联网格地图");
        assertThat(html).contains("const EMBED_MODE = true;");
        assertThat(html).contains("请先在 Halo 后台的 QSL 通联网格地图插件设置中填写天地图应用 key");
    }

    @Test
    void renderUsesTiandituTileServiceWhenAppKeyConfigured() {
        var html = service.render(
            QslGridMapPageRenderService.RenderOptions.empty(),
            new QslGridMapPageRenderService.MapSettings("test-key-123", "secret-value", "OM89", 3)
        );

        assertThat(html).contains("https://t{s}.tianditu.gov.cn/DataServer?T=vec_w");
        assertThat(html).contains("https://t{s}.tianditu.gov.cn/DataServer?T=cva_w");
        assertThat(html).contains("tk=${TIANDITU_CONFIG.appKey}");
        assertThat(html).contains("appKey: \"test-key-123\"");
        assertThat(html).contains("centerGrid: \"OM89\"");
        assertThat(html).contains("zoom: Number(\"3\") || 3");
        assertThat(html).contains("setView(defaultMapCenter(), DEFAULT_MAP_VIEW.zoom)");
        assertThat(html).contains("state.map.attributionControl.setPrefix(false);");
        assertThat(html).doesNotContain("secret-value");
        assertThat(html).doesNotContain("tile.openstreetmap.org");
    }

    @Test
    void renderFiltersInvalidOptions() {
        var html = service.render(new QslGridMapPageRenderService.RenderOptions(
            "EYEBALL",
            "bad-date",
            "2026-12-31",
            "invalid",
            "99999",
            "true",
            "<bad>"
        ));

        assertThat(html).doesNotContain("sceneType: \"EYEBALL\"");
        assertThat(html).doesNotContain("dateFrom: \"bad-date\"");
        assertThat(html).doesNotContain("grid: \"invalid\"");
        assertThat(html).contains("value=\"2000\"");
        assertThat(html).contains("qsl-grid-map-default");
    }

    @Test
    void renderFiltersInvalidMapViewSettings() {
        var tiandituSettings = new QslGridMapPageRenderService.TiandituSettings();
        var mapViewSettings = new QslGridMapPageRenderService.MapViewSettings();
        mapViewSettings.setDefaultCenterGrid("bad");
        mapViewSettings.setDefaultZoom(99);

        var settings = QslGridMapPageRenderService.normalizeSettings(tiandituSettings, mapViewSettings);
        var html = service.render(QslGridMapPageRenderService.RenderOptions.empty(), settings);

        assertThat(html).contains("centerGrid: \"OM89\"");
        assertThat(html).contains("zoom: Number(\"18\") || 3");
    }
}
