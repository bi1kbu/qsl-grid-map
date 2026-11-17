package com.bi1kbu.qslgridmap.front;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QslGridMapPageRenderServiceTest {

    private final QslGridMapPageRenderService service = new QslGridMapPageRenderService();

    @Test
    void normalizeProviderAllowsOnlyKnownServices() {
        var view = new QslGridMapPageRenderService.MapViewSettings();
        assertThat(QslGridMapPageRenderService.normalizeSettings(null, view, null).provider()).isEqualTo("tianditu");
        view.setProvider("openfreemap");
        assertThat(QslGridMapPageRenderService.normalizeSettings(null, view, null).provider()).isEqualTo("openfreemap");
        view.setProvider("</script><script>alert(1)</script>");
        var settings = QslGridMapPageRenderService.normalizeSettings(null, view, null);
        assertThat(settings.provider()).isEqualTo("tianditu");
        assertThat(service.render(QslGridMapPageRenderService.RenderOptions.empty(), settings))
            .doesNotContain("alert(1)").contains("const MAP_PROVIDER = \"tianditu\"");
    }

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
            new QslGridMapPageRenderService.MapSettings("test-key-123", "secret-value", "tianditu",
                new QslGridMapPageRenderService.MapDisplaySettings("OM89", true, "#16a34a", 3, 3, 8),
                QslGridMapPageRenderService.MapDisplaySettings.defaults())
        );

        assertThat(html).contains("https://t{s}.tianditu.gov.cn/DataServer?T=vec_w");
        assertThat(html).contains("https://t{s}.tianditu.gov.cn/DataServer?T=cva_w");
        assertThat(html).contains("tk=${TIANDITU_CONFIG.appKey}");
        assertThat(html).contains("appKey: \"test-key-123\"");
        assertThat(html).contains("centerGrid: \"OM89\"");
        assertThat(html).contains("showHomeGrid: true");
        assertThat(html).contains("homeGridBorderColor: \"#16a34a\"");
        assertThat(html).contains("zoom: 3");
        assertThat(html).contains("minZoom: 3");
        assertThat(html).contains("maxZoom: 8");
        assertThat(html).contains("setView(defaultMapCenter(), activeMapView.zoom)");
        assertThat(html).doesNotContain("fitBounds");
        assertThat(html).contains("minZoom: activeMapView.minZoom");
        assertThat(html).contains("maxZoom: activeMapView.maxZoom");
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

        var settings = QslGridMapPageRenderService.normalizeSettings(tiandituSettings, mapViewSettings, null);
        var html = service.render(QslGridMapPageRenderService.RenderOptions.empty(), settings);

        assertThat(html).contains("showHomeGrid: false");
        assertThat(html).contains("if (!activeMapView.showHomeGrid)");
    }

    @Test
    void renderFallsBackInvalidMapViewSettings() {
        var tiandituSettings = new QslGridMapPageRenderService.TiandituSettings();
        var mapViewSettings = new QslGridMapPageRenderService.MapViewSettings();
        mapViewSettings.setDefaultCenterGrid("bad");
        tiandituSettings.setDefaultZoom(99);
        tiandituSettings.setMinZoom(9);
        tiandituSettings.setMaxZoom(2);
        mapViewSettings.setHomeGridBorderColor("red");

        var settings = QslGridMapPageRenderService.normalizeSettings(tiandituSettings, mapViewSettings, null);
        var html = service.render(QslGridMapPageRenderService.RenderOptions.empty(), settings);

        assertThat(html).contains("centerGrid: \"OM89\"");
        assertThat(html).contains("showHomeGrid: true");
        assertThat(html).contains("homeGridBorderColor: \"#dc2626\"");
        assertThat(html).contains("zoom: 8");
        assertThat(html).contains("minZoom: 3");
        assertThat(html).contains("maxZoom: 8");
    }

    @Test
    void normalizeDefaultZoomWithinBackendZoomRange() {
        var tiandituSettings = new QslGridMapPageRenderService.TiandituSettings();
        var mapViewSettings = new QslGridMapPageRenderService.MapViewSettings();
        tiandituSettings.setMinZoom(5);
        tiandituSettings.setMaxZoom(8);

        var settings = QslGridMapPageRenderService.normalizeSettings(tiandituSettings, mapViewSettings, null);
        var html = service.render(QslGridMapPageRenderService.RenderOptions.empty(), settings);

        assertThat(html).contains("zoom: 5");
        assertThat(html).contains("minZoom: 5");
        assertThat(html).contains("maxZoom: 8");
    }

    @Test
    void servicesShareMapDisplaySettingsWhileNormalizingZoomSeparately() {
        var common = new QslGridMapPageRenderService.MapViewSettings();
        common.setProvider("openfreemap");
        common.setDefaultCenterGrid(" fn20 ");
        common.setShowHomeGrid(false);
        common.setHomeGridBorderColor("#16a34a");
        var tianditu = new QslGridMapPageRenderService.TiandituSettings();
        tianditu.setDefaultZoom(4);
        tianditu.setMinZoom(2);
        tianditu.setMaxZoom(8);
        var openFreeMap = new QslGridMapPageRenderService.ZoomSettings();
        openFreeMap.setDefaultZoom(12);
        openFreeMap.setMinZoom(5);
        openFreeMap.setMaxZoom(9);

        var settings = QslGridMapPageRenderService.normalizeSettings(tianditu, common, openFreeMap);

        assertThat(settings.provider()).isEqualTo("openfreemap");
        assertThat(settings.tianditu()).isEqualTo(new QslGridMapPageRenderService.MapDisplaySettings(
            "FN20", false, "#16a34a", 4, 2, 8));
        assertThat(settings.openfreemap()).isEqualTo(new QslGridMapPageRenderService.MapDisplaySettings(
            "FN20", false, "#16a34a", 9, 5, 9));
    }

    @Test
    void missingOpenFreeMapZoomUsesOwnDefaultsWhilePreservingSharedGridSettings() {
        var common = new QslGridMapPageRenderService.MapViewSettings();
        common.setDefaultCenterGrid("FN20");
        common.setShowHomeGrid(false);
        common.setHomeGridBorderColor("#16a34a");
        var tianditu = new QslGridMapPageRenderService.TiandituSettings();
        tianditu.setDefaultZoom(12);
        tianditu.setMinZoom(10);
        tianditu.setMaxZoom(15);

        var settings = QslGridMapPageRenderService.normalizeSettings(tianditu, common, null);

        assertThat(settings.openfreemap()).isEqualTo(new QslGridMapPageRenderService.MapDisplaySettings(
            "FN20", false, "#16a34a", 3, 3, 8));
        assertThat(settings.tianditu()).isEqualTo(new QslGridMapPageRenderService.MapDisplaySettings(
            "FN20", false, "#16a34a", 12, 10, 15));
    }

    @Test
    void invalidOpenFreeMapRangeIsIsolatedAndSharedUnsafeFieldsAreSanitized() {
        var common = new QslGridMapPageRenderService.MapViewSettings();
        common.setDefaultCenterGrid("</script><script>alert(1)</script>");
        common.setHomeGridBorderColor("</script><script>alert(2)</script>");
        var tianditu = new QslGridMapPageRenderService.TiandituSettings();
        tianditu.setMinZoom(4);
        tianditu.setMaxZoom(10);
        var openFreeMap = new QslGridMapPageRenderService.ZoomSettings();
        openFreeMap.setDefaultZoom(-100);
        openFreeMap.setMinZoom(12);
        openFreeMap.setMaxZoom(4);

        var settings = QslGridMapPageRenderService.normalizeSettings(tianditu, common, openFreeMap);

        assertThat(settings.tianditu()).isEqualTo(new QslGridMapPageRenderService.MapDisplaySettings(
            "OM89", true, "#dc2626", 4, 4, 10));
        assertThat(settings.openfreemap()).isEqualTo(new QslGridMapPageRenderService.MapDisplaySettings(
            "OM89", true, "#dc2626", 3, 3, 8));
        assertThat(service.render(QslGridMapPageRenderService.RenderOptions.empty(), settings))
            .doesNotContain("alert(1)", "alert(2)", "__OPENFREEMAP_VIEW_JS__", "__TIANDITU_VIEW_JS__");
    }

    @Test
    void openFreeMapZoomLimitsAreClampedToSupportedRange() {
        var openFreeMap = new QslGridMapPageRenderService.ZoomSettings();
        openFreeMap.setDefaultZoom(99);
        openFreeMap.setMinZoom(-5);
        openFreeMap.setMaxZoom(99);

        var settings = QslGridMapPageRenderService.normalizeSettings(null, null, openFreeMap);

        assertThat(settings.openfreemap().minZoom()).isEqualTo(1);
        assertThat(settings.openfreemap().maxZoom()).isEqualTo(18);
        assertThat(settings.openfreemap().defaultZoom()).isEqualTo(18);
        assertThat(settings.tianditu()).isEqualTo(QslGridMapPageRenderService.MapDisplaySettings.defaults());
    }
}
