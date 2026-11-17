package com.bi1kbu.qslgridmap.front;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QslGridMapBrowserScriptTest {

    @TempDir
    Path fixtures;

    @Test
    void generatedPageHandlesBasemapFailuresWithoutLosingGridData() throws Exception {
        writePage("tianditu.html", "tianditu", "test-key-123");
        writePage("no-key.html", "tianditu", "");
        writePage("openfreemap.html", "openfreemap", "test-key-123");
        writeCustomPage("openfreemap-custom.html", "openfreemap", "test-key-123",
            new int[] {4, 2, 8}, new int[] {6, 5, 7}, true);
        writeCustomPage("no-key-custom.html", "tianditu", "",
            new int[] {4, 2, 8}, new int[] {6, 5, 7}, true);
        writeCustomPage("fallback-custom.html", "tianditu", "test-key-123",
            new int[] {6, 2, 10}, new int[] {5, 5, 7}, true);
        writeCustomPage("fallback-lower.html", "tianditu", "test-key-123",
            new int[] {12, 10, 14}, new int[] {3, 2, 5}, false);
        writeCustomPage("fallback-higher.html", "tianditu", "test-key-123",
            new int[] {3, 2, 5}, new int[] {11, 10, 14}, true);

        var output = fixtures.resolve("node-test-output.txt");
        var processBuilder = new ProcessBuilder("node", "--test",
            Path.of("src/test/js/map-page.test.cjs").toAbsolutePath().toString());
        processBuilder.environment().put("QSL_MAP_TEST_FIXTURES", fixtures.toAbsolutePath().toString());
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(output.toFile());
        var process = processBuilder.start();
        var completed = process.waitFor(45, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
        }
        assertThat(completed).as("地图页面行为测试应在 45 秒内完成").isTrue();
        assertThat(process.exitValue())
            .withFailMessage("地图页面行为测试失败：%n%s", Files.readString(output))
            .isZero();
    }

    private void writePage(String fileName, String provider, String appKey) throws Exception {
        writeConfiguredPage(fileName, provider, appKey, new QslGridMapPageRenderService.MapViewSettings(), null, null);
    }

    private void writeCustomPage(String fileName, String provider, String appKey,
        int[] tiandituZoom, int[] openFreeMapZoom, boolean showHomeGrid) throws Exception {
        var common = new QslGridMapPageRenderService.MapViewSettings();
        common.setDefaultCenterGrid("FN20");
        common.setShowHomeGrid(showHomeGrid);
        common.setHomeGridBorderColor("#16a34a");
        writeConfiguredPage(fileName, provider, appKey, common, tiandituZoom, openFreeMapZoom);
    }

    private void writeConfiguredPage(String fileName, String provider, String appKey,
        QslGridMapPageRenderService.MapViewSettings common, int[] tiandituZoom, int[] openFreeMapZoom) throws Exception {
        var tianditu = new QslGridMapPageRenderService.TiandituSettings();
        tianditu.setAppKey(appKey);
        applyZoom(tianditu, tiandituZoom);
        var openFreeMap = new QslGridMapPageRenderService.ZoomSettings();
        applyZoom(openFreeMap, openFreeMapZoom);
        common.setProvider(provider);
        var settings = QslGridMapPageRenderService.normalizeSettings(tianditu, common, openFreeMap);
        var html = new QslGridMapPageRenderService()
            .render(QslGridMapPageRenderService.RenderOptions.empty(), settings);
        Files.writeString(fixtures.resolve(fileName), html, StandardCharsets.UTF_8);
    }

    private void applyZoom(QslGridMapPageRenderService.ZoomSettings settings, int[] values) {
        if (values == null) {
            return;
        }
        settings.setDefaultZoom(values[0]);
        settings.setMinZoom(values[1]);
        settings.setMaxZoom(values[2]);
    }
}
