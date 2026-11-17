package com.bi1kbu.qslgridmap.front;

import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class QslGridMapPageRenderService {

    private static final Pattern GRID_PATTERN = Pattern.compile("^[A-R]{2}[0-9]{2}$");
    private static final Pattern TIANDITU_TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,128}$");
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final String DEFAULT_CENTER_GRID = "OM89";
    private static final String DEFAULT_HOME_GRID_BORDER_COLOR = "#dc2626";
    private static final int DEFAULT_ZOOM = 3;
    private static final int DEFAULT_MIN_ZOOM = 3;
    private static final int DEFAULT_MAX_ZOOM = 8;
    private static final int ABSOLUTE_MIN_ZOOM = 1;
    private static final int ABSOLUTE_MAX_ZOOM = 18;

    public String render(RenderOptions options) {
        return render(options, MapSettings.empty());
    }

    public String render(RenderOptions options, MapSettings settings) {
        return render(options, settings, QslGridMapDataSource.Source.legacy(""));
    }

    public String render(RenderOptions options, MapSettings settings, QslGridMapDataSource.Source source) {
        var normalizedSettings = settings == null
            ? MapSettings.empty()
            : settings;
        return BASE_TEMPLATE
            .replace("__API_URL_JS__", escapeJs(source.apiUrl()))
            .replace("__MODERN_API__", Boolean.toString(source.modern()))
            .replace("__MAP_PROVIDER_JS__", normalizeProvider(normalizedSettings.provider()))
            .replace("__TIANDITU_CONFIGURED__", Boolean.toString(normalizedSettings.tiandituConfigured()))
            .replace("__TIANDITU_APP_KEY_JS__", escapeJs(normalizedSettings.tiandituAppKey()))
            .replace("__TIANDITU_VIEW_JS__", renderMapView(normalizedSettings.tianditu()))
            .replace("__OPENFREEMAP_VIEW_JS__", renderMapView(normalizedSettings.openfreemap()));
    }

    private String renderMapView(MapDisplaySettings settings) {
        return """
            {
              centerGrid: "%s",
              showHomeGrid: %s,
              homeGridBorderColor: "%s",
              zoom: %d,
              minZoom: %d,
              maxZoom: %d
            }
            """.formatted(escapeJs(settings.defaultCenterGrid()), settings.showHomeGrid(),
            escapeJs(settings.homeGridBorderColor()), settings.defaultZoom(), settings.minZoom(), settings.maxZoom());
    }

    public static MapSettings normalizeSettings(TiandituSettings tiandituSettings,
        MapViewSettings mapViewSettings, ZoomSettings openFreeMapSettings) {
        var safeTiandituSettings = tiandituSettings == null
            ? new TiandituSettings()
            : tiandituSettings;
        return new MapSettings(
            normalizeTiandituToken(safeTiandituSettings.getAppKey()),
            normalizeTiandituToken(safeTiandituSettings.getSecretKey()),
            normalizeProvider(mapViewSettings == null ? null : mapViewSettings.getProvider()),
            normalizeMapView(mapViewSettings, safeTiandituSettings),
            normalizeMapView(mapViewSettings, openFreeMapSettings)
        );
    }

    private static MapDisplaySettings normalizeMapView(MapViewSettings settings, ZoomSettings zoomSettings) {
        var safeMapViewSettings = settings == null ? new MapViewSettings() : settings;
        var safeZoomSettings = zoomSettings == null ? new ZoomSettings() : zoomSettings;
        var zoomRange = normalizeZoomRange(safeZoomSettings.getMinZoom(), safeZoomSettings.getMaxZoom());
        return new MapDisplaySettings(
            normalizeDefaultCenterGrid(safeMapViewSettings.getDefaultCenterGrid()),
            normalizeShowHomeGrid(safeMapViewSettings.getShowHomeGrid()),
            normalizeHomeGridBorderColor(safeMapViewSettings.getHomeGridBorderColor()),
            normalizeDefaultZoom(safeZoomSettings.getDefaultZoom(), zoomRange.minZoom(), zoomRange.maxZoom()),
            zoomRange.minZoom(),
            zoomRange.maxZoom()
        );
    }

    private static String normalizeProvider(String value) {
        return "openfreemap".equals(value) ? "openfreemap" : "tianditu";
    }

    private static String normalizeDefaultCenterGrid(String value) {
        var normalized = value == null ? "" : value.trim().toUpperCase();
        return GRID_PATTERN.matcher(normalized).matches() ? normalized : DEFAULT_CENTER_GRID;
    }

    private static boolean normalizeShowHomeGrid(Boolean value) {
        return value == null || value;
    }

    private static String normalizeHomeGridBorderColor(String value) {
        var normalized = value == null ? "" : value.trim();
        return HEX_COLOR_PATTERN.matcher(normalized).matches() ? normalized : DEFAULT_HOME_GRID_BORDER_COLOR;
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
        String provider,
        MapDisplaySettings tianditu,
        MapDisplaySettings openfreemap
    ) {
        public static MapSettings empty() {
            return normalizeSettings(null, null, null);
        }

        public boolean tiandituConfigured() {
            return tiandituAppKey != null && !tiandituAppKey.isBlank();
        }
    }

    public record MapDisplaySettings(
        String defaultCenterGrid,
        boolean showHomeGrid,
        String homeGridBorderColor,
        int defaultZoom,
        int minZoom,
        int maxZoom
    ) {
        public static MapDisplaySettings defaults() {
            return normalizeMapView(null, null);
        }
    }

    public static class TiandituSettings extends ZoomSettings {
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
        private String provider;
        private String defaultCenterGrid;
        private Boolean showHomeGrid;
        private String homeGridBorderColor;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getDefaultCenterGrid() {
            return defaultCenterGrid;
        }

        public void setDefaultCenterGrid(String defaultCenterGrid) {
            this.defaultCenterGrid = defaultCenterGrid;
        }

        public Boolean getShowHomeGrid() {
            return showHomeGrid;
        }

        public void setShowHomeGrid(Boolean showHomeGrid) {
            this.showHomeGrid = showHomeGrid;
        }

        public String getHomeGridBorderColor() {
            return homeGridBorderColor;
        }

        public void setHomeGridBorderColor(String homeGridBorderColor) {
            this.homeGridBorderColor = homeGridBorderColor;
        }
    }

    public static class ZoomSettings {
        private Integer defaultZoom;
        private Integer minZoom;
        private Integer maxZoom;

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
                white-space: pre-line;
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
              <div id="qsl-map-notice" class="qsl-map-notice hidden" role="status" aria-live="polite"></div>
            </div>

            <script
              src="https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.js"
              integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo="
              crossorigin=""
            ></script>
            <script>
              (() => {
                const API_URL = "__API_URL_JS__";
                const MODERN_API = __MODERN_API__;
                const MAP_PROVIDER = "__MAP_PROVIDER_JS__";
                const TILE_LOAD_TIMEOUT = 15000;
                const OPENFREEMAP_STYLE = "https://tiles.openfreemap.org/styles/liberty";
                const TIANDITU_CONFIG = {
                  configured: __TIANDITU_CONFIGURED__,
                  appKey: "__TIANDITU_APP_KEY_JS__"
                };
                const MAP_VIEWS = {
                  tianditu: __TIANDITU_VIEW_JS__,
                  openfreemap: __OPENFREEMAP_VIEW_JS__
                };
                let activeMapView = MAP_VIEWS[
                  MAP_PROVIDER === "openfreemap" || !TIANDITU_CONFIG.configured ? "openfreemap" : "tianditu"
                ];

                const mapNotice = document.getElementById("qsl-map-notice");
                const state = {
                  map: null,
                  layerGroup: null,
                  homeGridLayer: null,
                  tileLayers: [],
                  provider: null,
                  tileTimers: new Map(),
                  notices: new Map(),
                  homeGrid: null,
                  showCallsigns: !MODERN_API
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

                function updateNotice() {
                  const notices = [...state.notices.values()];
                  mapNotice.classList.toggle("hidden", notices.length === 0);
                  mapNotice.textContent = notices.map((notice) => notice.text).join("\\n");
                  const error = notices.some((notice) => notice.error);
                  mapNotice.style.borderColor = error ? "#ffd8d2" : "#d6e4ff";
                  mapNotice.style.color = error ? "#b42318" : "#30445f";
                }

                function showNotice(text, error = false, channel = "data") {
                  state.notices.set(channel, { text, error });
                  updateNotice();
                }

                function hideNotice(channel = "data") {
                  state.notices.delete(channel);
                  updateNotice();
                }

                function getApiData(result) {
                  if (!result) {
                    throw new Error("接口返回为空。");
                  }
                  if (result.code && result.code !== "QSL-0000") {
                    throw new Error(result.message || "接口返回异常。");
                  }
                  const data = MODERN_API ? result : (result.data ?? result);
                  if (!Array.isArray(data.items)) {
                    throw new Error("通联网格接口格式不正确。");
                  }
                  if (MODERN_API && (data.schemaVersion !== 1
                      || !["INSTANCE", "ALL"].includes(data.scope)
                      || typeof data.showCallsigns !== "boolean")) {
                    throw new Error("QSL 3.0 网格接口版本或格式不受支持。");
                  }
                  return data;
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
                  const bounds = gridBounds(activeMapView.centerGrid);
                  return bounds ? centerOf(bounds) : [39.5, 117];
                }

                function loadMapResource(tag, url, integrity) {
                  return new Promise((resolve, reject) => {
                    const element = document.createElement(tag);
                    const timer = setTimeout(() => finish(false), TILE_LOAD_TIMEOUT);
                    function finish(success) {
                      clearTimeout(timer);
                      element.onload = null;
                      element.onerror = null;
                      if (success) {
                        resolve();
                      } else {
                        element.remove();
                        reject(new Error("开放地图组件加载失败或超时。"));
                      }
                    }
                    element.integrity = integrity;
                    element.crossOrigin = "anonymous";
                    element.onload = () => finish(true);
                    element.onerror = () => finish(false);
                    if (tag === "link") {
                      element.rel = "stylesheet";
                      element.href = url;
                    } else {
                      element.src = url;
                      element.async = true;
                    }
                    document.head.appendChild(element);
                  });
                }

                async function loadOpenFreeMapResources() {
                  await Promise.all([
                    loadMapResource("link", "https://cdn.jsdelivr.net/npm/maplibre-gl@5.24.0/dist/maplibre-gl.css",
                      "sha384-uTttxo/aOKbdE5RlD/SPzSDoDmNvGlUYPjONi2MN/b7c9HPSvW07OIuyP7uL6jxK"),
                    loadMapResource("script", "https://cdn.jsdelivr.net/npm/maplibre-gl@5.24.0/dist/maplibre-gl.js",
                      "sha384-5+cfbwT0iiub6VsQAdn6yz16nr6sDiQoHx6tm4O8OVYXHYOxcffFmCJBL0dgdvGp")
                  ]);
                  await loadMapResource("script",
                    "https://cdn.jsdelivr.net/npm/@maplibre/maplibre-gl-leaflet@0.1.4/leaflet-maplibre-gl.js",
                    "sha384-tXYNKOHx4T02jMP7YYCtBxPIv1B5gaA5mcVPBzqMp6d7VzWzxJgI2aWF/nJLrQdS");
                }

                async function useOpenFreeMap(reason = "") {
                  // 先锁定服务，阻止同一批失败瓦片重复创建地图与下载资源。
                  if (state.provider === "openfreemap") {
                    return;
                  }
                  state.provider = "openfreemap";
                  state.tileTimers.forEach((timer) => clearTimeout(timer));
                  state.tileTimers.clear();
                  state.tileLayers.forEach((layer) => {
                    // 先触发 Leaflet 的移除钩子，清理地图事件和旧服务署名。
                    layer.remove();
                    layer.off();
                  });
                  state.tileLayers = [];
                  applyOpenFreeMapView();
                  showNotice(`${reason}正在加载 OpenFreeMap（开放地图）…`, false, "basemap");
                  let layer;
                  let timer;
                  let phase = "组件资源";
                  const status = {
                    rendered: false,
                    slow: false,
                    contextLost: false,
                    resourceError: false,
                    failedTiles: new Set(),
                    failedSources: new Set()
                  };
                  const diagnose = (stage, event = {}) => {
                    // 不输出原始异常、资源地址或查询参数，防止诊断日志包含令牌。
                    console.warn("OpenFreeMap（开放地图）加载诊断", {
                      阶段: stage,
                      来源: ["ne2_shaded", "openmaptiles"].includes(event.sourceId) ? event.sourceId : "未指定",
                      状态码: Number.isInteger(event.error?.status) ? event.error.status : "未知"
                    });
                  };
                  const tileKey = (event) => {
                    const coordinate = event.coord ?? event.tile?.tileID;
                    const canonical = coordinate?.canonical;
                    // 同坐标瓦片重新创建后仍可消除旧错误，世界副本共用同一资源。
                    return canonical
                      ? `${event.sourceId}:${canonical.z}/${canonical.x}/${canonical.y}`
                      : event.tile;
                  };
                  const refreshStatus = () => {
                    if (status.contextLost) {
                      showNotice("OpenFreeMap（开放地图）绘图上下文已丢失，正在等待浏览器恢复。", true, "basemap");
                    } else if (status.resourceError) {
                      showNotice("OpenFreeMap（开放地图）样式或地图资源加载失败，请检查地图服务连接后刷新重试。", true, "basemap");
                    } else if (status.failedSources.size) {
                      showNotice("OpenFreeMap（开放地图）有数据源加载失败，请检查地图服务连接后重试。", true, "basemap");
                    } else if (status.failedTiles.size) {
                      showNotice("OpenFreeMap（开放地图）有地图瓦片加载失败，已加载区域仍可浏览；请稍后重试。", false, "basemap");
                    } else if (status.slow) {
                      showNotice("OpenFreeMap（开放地图）加载较慢，仍在继续，请稍候。", false, "basemap");
                    } else if (!status.rendered) {
                      showNotice(`${reason}正在加载 OpenFreeMap（开放地图）…`, false, "basemap");
                    } else if (reason) {
                      showNotice(`${reason}已切换至 OpenFreeMap（开放地图）。`, false, "basemap");
                    } else {
                      hideNotice("basemap");
                    }
                  };
                  const watchLoading = () => {
                    clearTimeout(timer);
                    timer = setTimeout(() => {
                      status.slow = true;
                      refreshStatus();
                    }, 20000);
                  };
                  try {
                    await loadOpenFreeMapResources();
                    phase = "地图初始化";
                    layer = L.maplibreGL({
                      style: null,
                      interactive: false,
                      attributionControl: {
                        customAttribution: '<a href="https://openfreemap.org/">OpenFreeMap（开放地图）</a> · &copy; <a href="https://www.openmaptiles.org/">OpenMapTiles</a> · &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap 贡献者</a>'
                      }
                    });
                    layer.addTo(state.map);
                    const glMap = layer.getMaplibreMap();
                    glMap.on("error", (event) => {
                      if (event.tile) {
                        const key = tileKey(event);
                        if (!status.failedTiles.has(key)) {
                          diagnose("地图瓦片", event);
                        }
                        status.failedTiles.add(key);
                      } else if (event.sourceId) {
                        diagnose("地图数据源", event);
                        status.failedSources.add(event.sourceId);
                      } else {
                        diagnose("地图样式或资源", event);
                        status.resourceError = true;
                      }
                      refreshStatus();
                    });
                    glMap.on("sourcedata", (event) => {
                      let recovered = false;
                      if (event.tile) {
                        recovered = status.failedTiles.delete(tileKey(event));
                      }
                      if (event.sourceDataType === "metadata") {
                        recovered = status.failedSources.delete(event.sourceId) || recovered;
                      }
                      if (recovered) {
                        refreshStatus();
                      }
                    });
                    glMap.on("webglcontextlost", () => {
                      clearTimeout(timer);
                      status.contextLost = true;
                      diagnose("绘图上下文丢失");
                      refreshStatus();
                    });
                    glMap.on("webglcontextrestored", () => {
                      status.contextLost = false;
                      status.rendered = false;
                      status.slow = false;
                      watchLoading();
                      refreshStatus();
                    });
                    const rendered = () => {
                      if (status.contextLost) {
                        return;
                      }
                      clearTimeout(timer);
                      status.rendered = true;
                      status.slow = false;
                      // 完成事件也可能包含失败瓦片，只有对应资源成功才清除其错误。
                      refreshStatus();
                    };
                    glMap.on("load", rendered);
                    glMap.on("idle", () => {
                      if (glMap.loaded()) {
                        rendered();
                      }
                    });
                    phase = "地图样式";
                    watchLoading();
                    glMap.setStyle(OPENFREEMAP_STYLE);
                  } catch (error) {
                    if (layer) {
                      try {
                        // 桥接库 0.1.4 在 WebGL 构造失败后没有可销毁的实例。
                        // 使用安全移除钩子，让 Leaflet 继续释放图层注册表与事件。
                        if (!layer.getMaplibreMap()) {
                          layer.onRemove = () => layer.getContainer()?.remove();
                        }
                        layer.remove();
                      } catch (cleanupError) {
                        // 桥接库初始化 WebGL 失败时仍可能留下事件，解除绑定以保留网格交互。
                        state.map.off(layer.getEvents(), layer);
                        layer.getContainer()?.remove();
                      }
                    }
                    clearTimeout(timer);
                    diagnose(phase);
                    const message = phase === "组件资源"
                      ? "组件资源加载失败或超时，请检查静态资源访问后刷新重试。"
                      : phase === "地图初始化"
                        ? "初始化失败，请检查浏览器 WebGL 支持后刷新重试。"
                        : "样式加载失败，请检查地图服务连接后刷新重试。";
                    showNotice(`OpenFreeMap（开放地图）${message}`, true, "basemap");
                  }
                }

                function applyOpenFreeMapView() {
                  if (activeMapView === MAP_VIEWS.openfreemap) {
                    return;
                  }
                  const center = state.map.getCenter();
                  const zoom = state.map.getZoom();
                  activeMapView = MAP_VIEWS.openfreemap;
                  // 范围互不相交时先扩展对应边界，避免中间出现最小值大于最大值。
                  if (activeMapView.minZoom > state.map.getMaxZoom()) {
                    state.map.setMaxZoom(activeMapView.maxZoom);
                    state.map.setMinZoom(activeMapView.minZoom);
                  } else {
                    state.map.setMinZoom(activeMapView.minZoom);
                    state.map.setMaxZoom(activeMapView.maxZoom);
                  }
                  const boundedZoom = Math.max(activeMapView.minZoom, Math.min(activeMapView.maxZoom, zoom));
                  state.map.setView(center, boundedZoom, { animate: false });
                }

                function initBaseMap() {
                  if (MAP_PROVIDER === "openfreemap" || !TIANDITU_CONFIG.configured) {
                    useOpenFreeMap(MAP_PROVIDER === "openfreemap" ? "" : "未配置天地图应用 key，");
                    return;
                  }
                  state.provider = "tianditu";
                  const tileOptions = {
                    minZoom: activeMapView.minZoom,
                    maxZoom: activeMapView.maxZoom,
                    subdomains: "01234567",
                    attribution: '&copy; <a href="https://www.tianditu.gov.cn/">天地图</a>'
                  };
                  const baseLayer = L.tileLayer(`https://t{s}.tianditu.gov.cn/DataServer?T=vec_w&x={x}&y={y}&l={z}&tk=${TIANDITU_CONFIG.appKey}`, tileOptions);
                  const labelLayer = L.tileLayer(`https://t{s}.tianditu.gov.cn/DataServer?T=cva_w&x={x}&y={y}&l={z}&tk=${TIANDITU_CONFIG.appKey}`, tileOptions);
                  state.tileLayers = [baseLayer, labelLayer];
                  state.tileLayers.forEach((layer) => {
                    const stopTimer = () => {
                      clearTimeout(state.tileTimers.get(layer));
                      state.tileTimers.delete(layer);
                    };
                    layer.on("loading", () => {
                      stopTimer();
                      state.tileTimers.set(layer, setTimeout(() => {
                        useOpenFreeMap("天地图瓦片加载超时，");
                      }, TILE_LOAD_TIMEOUT));
                    });
                    layer.on("load", stopTimer);
                    layer.on("tileerror", () => useOpenFreeMap("天地图瓦片加载失败，"));
                    layer.addTo(state.map);
                  });
                }

                function initMap() {
                  if (!window.L) {
                    throw new Error("地图组件加载失败，请检查浏览器是否能访问 Leaflet 静态资源。");
                  }
                  state.map = L.map("qsl-map", {
                    worldCopyJump: true,
                    zoomControl: true,
                    minZoom: activeMapView.minZoom,
                    maxZoom: activeMapView.maxZoom
                  }).setView(defaultMapCenter(), activeMapView.zoom);
                  state.map.attributionControl.setPrefix(false);
                  state.layerGroup = L.featureGroup().addTo(state.map);
                  renderHomeGrid();
                  initBaseMap();
                }

                function renderHomeGrid() {
                  if (state.homeGridLayer) {
                    state.homeGridLayer.remove();
                    state.homeGridLayer = null;
                  }
                  if (!activeMapView.showHomeGrid) {
                    return;
                  }
                  const homeGrid = MODERN_API ? state.homeGrid : activeMapView.centerGrid;
                  const bounds = gridBounds(homeGrid);
                  if (!bounds) {
                    return;
                  }
                  state.homeGridLayer = L.rectangle(bounds, {
                    color: activeMapView.homeGridBorderColor,
                    weight: 3,
                    fill: false,
                    dashArray: "6 4",
                    interactive: true
                  })
                    .bindPopup(`<div class="qsl-popup"><h3 class="qsl-popup-title">本台网格：${escapeHtml(homeGrid)}</h3></div>`)
                    .addTo(state.map);
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
                  const calls = Array.isArray(item.callSigns) ? item.callSigns : [];
                  return [
                    '<div class="qsl-popup">',
                    `<h3 class="qsl-popup-title">${escapeHtml(item.grid || "")}</h3>`,
                    `<p class="qsl-popup-meta">${MODERN_API ? "通联记录数" : "通联数量"}：${item.count}</p>`,
                    state.showCallsigns
                      ? `<p class="qsl-popup-meta">呼号：${escapeHtml(calls.slice(0, 12).join("、"))}${calls.length > 12 ? " 等" : ""}</p>`
                      : "",
                    "</div>"
                  ].join("");
                }

                function renderMap(items) {
                  state.layerGroup.clearLayers();
                  const validItems = items.filter((item) => gridBounds(item.grid));
                  const maxCount = Math.max(1, ...validItems.map((item) => item.count));

                  for (const item of validItems) {
                    const bounds = gridBounds(item.grid);
                    const count = item.count;
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
                  if (state.homeGridLayer) {
                    state.homeGridLayer.bringToFront();
                  }

                  if (validItems.length) {
                    hideNotice();
                  } else {
                    showNotice("没有符合条件的 QSO 通联网格数据。");
                  }
                }

                async function loadData() {
                  try {
                    const response = await fetch(API_URL, {
                      method: "GET",
                      credentials: "same-origin",
                      headers: { "Accept": "application/json" }
                    });
                    const result = await response.json();
                    if (!response.ok) {
                      throw new Error(result?.message || `通联网格读取失败（HTTP ${response.status}）。`);
                    }
                    const data = getApiData(result);
                    const items = data.items.map((item) => {
                      const count = MODERN_API ? item.count : (Array.isArray(item.records) ? item.records.length : 0);
                      if (!Number.isSafeInteger(count) || count < 0) {
                        throw new Error("通联网格数量格式不正确。");
                      }
                      return { grid: item.grid, count, callSigns: item.callSigns };
                    });
                    if (MODERN_API) {
                      state.homeGrid = data.scope === "INSTANCE" ? data.homeGrid : null;
                      state.showCallsigns = data.showCallsigns;
                      renderHomeGrid();
                    }
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
