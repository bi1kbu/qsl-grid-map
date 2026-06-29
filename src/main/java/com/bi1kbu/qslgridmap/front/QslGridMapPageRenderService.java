package com.bi1kbu.qslgridmap.front;

import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class QslGridMapPageRenderService {

    private static final Pattern GRID_PATTERN = Pattern.compile("^[A-R]{2}[0-9]{2}$");
    private static final Pattern TIANDITU_TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,128}$");
    private static final String DEFAULT_CENTER_GRID = "OM89";
    private static final int DEFAULT_ZOOM = 3;
    private static final int DEFAULT_MIN_ZOOM = 3;
    private static final int DEFAULT_MAX_ZOOM = 8;
    private static final int ABSOLUTE_MIN_ZOOM = 1;
    private static final int ABSOLUTE_MAX_ZOOM = 18;

    public String render(RenderOptions options) {
        return render(options, MapSettings.empty());
    }

    public String render(RenderOptions options, MapSettings settings) {
        var normalizedSettings = settings == null
            ? MapSettings.empty()
            : settings;
        return BASE_TEMPLATE
            .replace("__TIANDITU_CONFIGURED__", Boolean.toString(normalizedSettings.tiandituConfigured()))
            .replace("__TIANDITU_APP_KEY_JS__", escapeJs(normalizedSettings.tiandituAppKey()))
            .replace("__DEFAULT_CENTER_GRID_JS__", escapeJs(normalizedSettings.defaultCenterGrid()))
            .replace("__DEFAULT_ZOOM_JS__", Integer.toString(normalizedSettings.defaultZoom()))
            .replace("__MIN_ZOOM_JS__", Integer.toString(normalizedSettings.minZoom()))
            .replace("__MAX_ZOOM_JS__", Integer.toString(normalizedSettings.maxZoom()));
    }

    public static MapSettings normalizeTiandituSettings(TiandituSettings settings) {
        return normalizeSettings(settings, null);
    }

    public static MapSettings normalizeSettings(TiandituSettings tiandituSettings,
        MapViewSettings mapViewSettings) {
        var safeMapViewSettings = mapViewSettings == null
            ? new MapViewSettings()
            : mapViewSettings;
        var safeTiandituSettings = tiandituSettings == null
            ? new TiandituSettings()
            : tiandituSettings;
        var zoomRange = normalizeZoomRange(safeMapViewSettings.getMinZoom(), safeMapViewSettings.getMaxZoom());
        return new MapSettings(
            normalizeTiandituToken(safeTiandituSettings.getAppKey()),
            normalizeTiandituToken(safeTiandituSettings.getSecretKey()),
            normalizeDefaultCenterGrid(safeMapViewSettings.getDefaultCenterGrid()),
            normalizeDefaultZoom(safeMapViewSettings.getDefaultZoom(), zoomRange.minZoom(), zoomRange.maxZoom()),
            zoomRange.minZoom(),
            zoomRange.maxZoom()
        );
    }

    private static String normalizeDefaultCenterGrid(String value) {
        var normalized = value == null ? "" : value.trim().toUpperCase();
        return GRID_PATTERN.matcher(normalized).matches() ? normalized : DEFAULT_CENTER_GRID;
    }

    private static ZoomRange normalizeZoomRange(Integer minZoom, Integer maxZoom) {
        var normalizedMinZoom = normalizeZoom(minZoom, DEFAULT_MIN_ZOOM);
        var normalizedMaxZoom = normalizeZoom(maxZoom, DEFAULT_MAX_ZOOM);
        if (normalizedMinZoom > normalizedMaxZoom) {
            return new ZoomRange(DEFAULT_MIN_ZOOM, DEFAULT_MAX_ZOOM);
        }
        return new ZoomRange(normalizedMinZoom, normalizedMaxZoom);
    }

    private static int normalizeDefaultZoom(Integer value, int minZoom, int maxZoom) {
        var normalized = value == null ? DEFAULT_ZOOM : value;
        return Math.max(minZoom, Math.min(maxZoom, normalized));
    }

    private static int normalizeZoom(Integer value, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(ABSOLUTE_MIN_ZOOM, Math.min(ABSOLUTE_MAX_ZOOM, value));
    }

    private static String normalizeTiandituToken(String value) {
        var normalized = value == null ? "" : value.trim();
        return TIANDITU_TOKEN_PATTERN.matcher(normalized).matches() ? normalized : "";
    }

    private String escapeJs(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n");
    }

    public record RenderOptions(
        String sceneType,
        String dateFrom,
        String dateTo,
        String grid,
        String limit,
        String embed,
        String embedId
    ) {
        public static RenderOptions empty() {
            return new RenderOptions("", "", "", "", "", "", "");
        }
    }

    private record ZoomRange(int minZoom, int maxZoom) {
    }

    public record MapSettings(
        String tiandituAppKey,
        String tiandituSecretKey,
        String defaultCenterGrid,
        int defaultZoom,
        int minZoom,
        int maxZoom
    ) {
        public static MapSettings empty() {
            return new MapSettings("", "", DEFAULT_CENTER_GRID, DEFAULT_ZOOM, DEFAULT_MIN_ZOOM, DEFAULT_MAX_ZOOM);
        }

        public boolean tiandituConfigured() {
            return tiandituAppKey != null && !tiandituAppKey.isBlank();
        }
    }

    public static class TiandituSettings {
        private String appKey;
        private String secretKey;

        public String getAppKey() {
            return appKey;
        }

        public void setAppKey(String appKey) {
            this.appKey = appKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }
    }

    public static class MapViewSettings {
        private String defaultCenterGrid;
        private Integer defaultZoom;
        private Integer minZoom;
        private Integer maxZoom;

        public String getDefaultCenterGrid() {
            return defaultCenterGrid;
        }

        public void setDefaultCenterGrid(String defaultCenterGrid) {
            this.defaultCenterGrid = defaultCenterGrid;
        }

        public Integer getDefaultZoom() {
            return defaultZoom;
        }

        public void setDefaultZoom(Integer defaultZoom) {
            this.defaultZoom = defaultZoom;
        }

        public Integer getMinZoom() {
            return minZoom;
        }

        public void setMinZoom(Integer minZoom) {
            this.minZoom = minZoom;
        }

        public Integer getMaxZoom() {
            return maxZoom;
        }

        public void setMaxZoom(Integer maxZoom) {
            this.maxZoom = maxZoom;
        }
    }

    private static final String BASE_TEMPLATE = """
        <!doctype html>
        <html lang="zh-CN">
          <head>
            <meta charset="UTF-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
            <title>QSL 通联网格地图</title>
            <link
              rel="stylesheet"
              href="https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.css"
              integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY="
              crossorigin=""
            />
            <style>
              html,
              body,
              .qsl-map-panel,
              #qsl-map {
                width: 100%;
                height: 100%;
                margin: 0;
              }
              body {
                overflow: hidden;
                background: #dfe7ef;
                font-family: "PingFang SC", "Microsoft YaHei", "Noto Sans SC", Arial, sans-serif;
              }
              .qsl-map-panel {
                position: fixed;
                inset: 0;
              }
              #qsl-map {
                min-height: 100vh;
              }
              .qsl-map-notice {
                position: absolute;
                left: 12px;
                bottom: 12px;
                z-index: 500;
                max-width: min(520px, calc(100% - 24px));
                border: 1px solid #d6e4ff;
                border-radius: 6px;
                background: rgba(255, 255, 255, 0.94);
                color: #30445f;
                padding: 8px 10px;
                font-size: 13px;
                line-height: 1.45;
                box-shadow: 0 8px 22px rgba(25, 35, 50, 0.08);
              }
              .qsl-map-notice.hidden {
                display: none;
              }
              .qsl-popup {
                min-width: 220px;
              }
              .qsl-popup-title {
                margin: 0 0 6px;
                font-size: 14px;
                font-weight: 700;
              }
              .qsl-popup-meta {
                margin: 0;
                color: #526070;
                font-size: 12px;
                line-height: 1.5;
              }
            </style>
          </head>
          <body>
            <div class="qsl-map-panel">
              <div id="qsl-map"></div>
              <div id="qsl-map-notice" class="qsl-map-notice hidden"></div>
            </div>

            <script
              src="https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.js"
              integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo="
              crossorigin=""
            ></script>
            <script>
              (() => {
                const API_URL = "/apis/api.qsl-management.bi1kbu.com/v1alpha1/qso-public/grids";
                const TIANDITU_CONFIG = {
                  configured: __TIANDITU_CONFIGURED__,
                  appKey: "__TIANDITU_APP_KEY_JS__"
                };
                const DEFAULT_MAP_VIEW = {
                  centerGrid: "__DEFAULT_CENTER_GRID_JS__",
                  zoom: Number("__DEFAULT_ZOOM_JS__") || 3,
                  minZoom: Number("__MIN_ZOOM_JS__") || 3,
                  maxZoom: Number("__MAX_ZOOM_JS__") || 8
                };

                const mapNotice = document.getElementById("qsl-map-notice");
                const state = {
                  map: null,
                  layerGroup: null,
                  tileLayers: []
                };

                function safeText(value) {
                  return String(value ?? "");
                }

                function escapeHtml(value) {
                  return safeText(value)
                    .replaceAll("&", "&amp;")
                    .replaceAll("<", "&lt;")
                    .replaceAll(">", "&gt;")
                    .replaceAll('"', "&quot;")
                    .replaceAll("'", "&#39;");
                }

                function normalizeGrid(value) {
                  return safeText(value).trim().toUpperCase().slice(0, 4);
                }

                function showNotice(text, error = false) {
                  mapNotice.classList.remove("hidden");
                  mapNotice.textContent = text;
                  mapNotice.style.borderColor = error ? "#ffd8d2" : "#d6e4ff";
                  mapNotice.style.color = error ? "#b42318" : "#30445f";
                }

                function hideNotice() {
                  mapNotice.classList.add("hidden");
                }

                function getApiData(result) {
                  if (!result) {
                    throw new Error("接口返回为空。");
                  }
                  if (result.code && result.code !== "QSL-0000") {
                    throw new Error(result.message || "接口返回异常。");
                  }
                  return result.data ?? result;
                }

                function gridBounds(grid) {
                  const normalized = normalizeGrid(grid);
                  if (!/^[A-R]{2}[0-9]{2}$/.test(normalized)) {
                    return null;
                  }
                  const lonField = normalized.charCodeAt(0) - 65;
                  const latField = normalized.charCodeAt(1) - 65;
                  const lonSquare = Number(normalized[2]);
                  const latSquare = Number(normalized[3]);
                  const west = -180 + lonField * 20 + lonSquare * 2;
                  const south = -90 + latField * 10 + latSquare;
                  const east = west + 2;
                  const north = south + 1;
                  return [[south, west], [north, east]];
                }

                function centerOf(bounds) {
                  return [
                    (bounds[0][0] + bounds[1][0]) / 2,
                    (bounds[0][1] + bounds[1][1]) / 2
                  ];
                }

                function defaultMapCenter() {
                  const bounds = gridBounds(DEFAULT_MAP_VIEW.centerGrid);
                  return bounds ? centerOf(bounds) : [39.5, 117];
                }

                function initMap() {
                  if (!window.L) {
                    throw new Error("地图组件加载失败，请检查浏览器是否能访问 Leaflet 静态资源。");
                  }
                  state.map = L.map("qsl-map", {
                    worldCopyJump: true,
                    zoomControl: true,
                    minZoom: DEFAULT_MAP_VIEW.minZoom,
                    maxZoom: DEFAULT_MAP_VIEW.maxZoom
                  }).setView(defaultMapCenter(), DEFAULT_MAP_VIEW.zoom);
                  state.map.attributionControl.setPrefix(false);
                  if (TIANDITU_CONFIG.configured) {
                    const tileOptions = {
                      minZoom: DEFAULT_MAP_VIEW.minZoom,
                      maxZoom: DEFAULT_MAP_VIEW.maxZoom,
                      subdomains: "01234567",
                      attribution: '&copy; <a href="https://www.tianditu.gov.cn/">天地图</a>'
                    };
                    const baseLayer = L.tileLayer(`https://t{s}.tianditu.gov.cn/DataServer?T=vec_w&x={x}&y={y}&l={z}&tk=${TIANDITU_CONFIG.appKey}`, tileOptions)
                      .on("tileerror", () => {
                        showNotice("天地图瓦片加载失败，请检查插件设置中的应用 key、域名白名单和当前网络。", true);
                      })
                      .addTo(state.map);
                    const labelLayer = L.tileLayer(`https://t{s}.tianditu.gov.cn/DataServer?T=cva_w&x={x}&y={y}&l={z}&tk=${TIANDITU_CONFIG.appKey}`, tileOptions)
                      .addTo(state.map);
                    state.tileLayers = [baseLayer, labelLayer];
                  } else {
                    showNotice("请先在 Halo 后台的 QSL 通联网格地图插件设置中填写天地图应用 key。");
                  }
                  state.layerGroup = L.featureGroup().addTo(state.map);
                }

                function colorFor(count, maxCount) {
                  if (maxCount <= 1) {
                    return "#2f7dd1";
                  }
                  const ratio = Math.max(0, Math.min(1, count / maxCount));
                  if (ratio > 0.75) {
                    return "#d97706";
                  }
                  if (ratio > 0.45) {
                    return "#0f9f6e";
                  }
                  return "#2f7dd1";
                }

                function popupHtml(item) {
                  const records = Array.isArray(item.records) ? item.records : [];
                  const calls = Array.isArray(item.callSigns) ? item.callSigns : [];
                  return [
                    '<div class="qsl-popup">',
                    `<h3 class="qsl-popup-title">${escapeHtml(item.grid || "")}</h3>`,
                    `<p class="qsl-popup-meta">通联明细：${records.length}</p>`,
                    `<p class="qsl-popup-meta">呼号：${escapeHtml(calls.slice(0, 12).join("、"))}${calls.length > 12 ? " 等" : ""}</p>`,
                    "</div>"
                  ].join("");
                }

                function renderMap(items) {
                  state.layerGroup.clearLayers();
                  const validItems = items.filter((item) => gridBounds(item.grid));
                  const maxCount = Math.max(1, ...validItems.map((item) => (item.records || []).length));

                  for (const item of validItems) {
                    const bounds = gridBounds(item.grid);
                    const count = (item.records || []).length;
                    const color = colorFor(count, maxCount);
                    L.rectangle(bounds, {
                      color,
                      weight: 1,
                      fillColor: color,
                      fillOpacity: Math.max(0.18, Math.min(0.55, 0.18 + count / maxCount * 0.36))
                    })
                      .bindPopup(popupHtml(item))
                      .addTo(state.layerGroup);
                  }

                  if (validItems.length) {
                    hideNotice();
                    state.map.fitBounds(state.layerGroup.getBounds(), {
                      padding: [26, 26],
                      maxZoom: Math.min(6, DEFAULT_MAP_VIEW.maxZoom)
                    });
                  } else {
                    state.map.setView(defaultMapCenter(), DEFAULT_MAP_VIEW.zoom);
                    showNotice("没有符合条件的 QSO 通联网格数据。");
                  }
                }

                async function loadData() {
                  try {
                    const response = await fetch(`${API_URL}?sceneType=QSO`, {
                      method: "GET",
                      credentials: "same-origin",
                      headers: { "Accept": "application/json" }
                    });
                    const result = await response.json();
                    const data = getApiData(result);
                    const items = Array.isArray(data.items) ? data.items : [];
                    renderMap(items);
                  } catch (error) {
                    showNotice(error?.message || "地图数据加载失败。", true);
                  } finally {
                    setTimeout(() => {
                      if (state.map) {
                        state.map.invalidateSize();
                      }
                    }, 50);
                  }
                }

                try {
                  initMap();
                  loadData();
                } catch (error) {
                  showNotice(error?.message || "地图初始化失败。", true);
                }
              })();
            </script>
          </body>
        </html>
        """;
}
