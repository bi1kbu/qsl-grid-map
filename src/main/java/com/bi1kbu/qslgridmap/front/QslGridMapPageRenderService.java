package com.bi1kbu.qslgridmap.front;

import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class QslGridMapPageRenderService {

    private static final Pattern SCENE_TYPE_PATTERN = Pattern.compile("^(QSO|SWL)$");
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern GRID_PATTERN = Pattern.compile("^[A-R]{2}[0-9]{2}$");
    private static final Pattern EMBED_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,80}$");
    private static final Pattern TIANDITU_TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,128}$");
    private static final String DEFAULT_CENTER_GRID = "OM89";
    private static final int DEFAULT_ZOOM = 3;
    private static final int DEFAULT_LIMIT = 500;
    private static final int MAX_LIMIT = 2000;
    private static final int MIN_ZOOM = 1;
    private static final int MAX_ZOOM = 18;

    public String render(RenderOptions options) {
        return render(options, MapSettings.empty());
    }

    public String render(RenderOptions options, MapSettings settings) {
        var normalized = normalize(options);
        var normalizedSettings = settings == null
            ? MapSettings.empty()
            : settings;
        return BASE_TEMPLATE
            .replace("__TITLE__", normalized.embed() ? "QSL 通联网格地图" : "QSL 通联网格地图")
            .replace("__SCENE_TYPE_HTML__", escapeHtml(normalized.sceneType()))
            .replace("__DATE_FROM_HTML__", escapeHtml(normalized.dateFrom()))
            .replace("__DATE_TO_HTML__", escapeHtml(normalized.dateTo()))
            .replace("__GRID_HTML__", escapeHtml(normalized.grid()))
            .replace("__LIMIT_HTML__", Integer.toString(normalized.limit()))
            .replace("__SCENE_TYPE_JS__", escapeJs(normalized.sceneType()))
            .replace("__DATE_FROM_JS__", escapeJs(normalized.dateFrom()))
            .replace("__DATE_TO_JS__", escapeJs(normalized.dateTo()))
            .replace("__GRID_JS__", escapeJs(normalized.grid()))
            .replace("__LIMIT_JS__", Integer.toString(normalized.limit()))
            .replace("__EMBED_MODE__", Boolean.toString(normalized.embed()))
            .replace("__EMBED_ID__", escapeJs(normalized.embedId()))
            .replace("__TIANDITU_CONFIGURED__", Boolean.toString(normalizedSettings.tiandituConfigured()))
            .replace("__TIANDITU_APP_KEY_JS__", escapeJs(normalizedSettings.tiandituAppKey()))
            .replace("__DEFAULT_CENTER_GRID_JS__", escapeJs(normalizedSettings.defaultCenterGrid()))
            .replace("__DEFAULT_ZOOM_JS__", Integer.toString(normalizedSettings.defaultZoom()));
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
        return new MapSettings(
            normalizeTiandituToken(safeTiandituSettings.getAppKey()),
            normalizeTiandituToken(safeTiandituSettings.getSecretKey()),
            normalizeDefaultCenterGrid(safeMapViewSettings.getDefaultCenterGrid()),
            normalizeDefaultZoom(safeMapViewSettings.getDefaultZoom())
        );
    }

    private static String normalizeDefaultCenterGrid(String value) {
        var normalized = value == null ? "" : value.trim().toUpperCase();
        return GRID_PATTERN.matcher(normalized).matches() ? normalized : DEFAULT_CENTER_GRID;
    }

    private static int normalizeDefaultZoom(Integer value) {
        if (value == null) {
            return DEFAULT_ZOOM;
        }
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value));
    }

    private static String normalizeTiandituToken(String value) {
        var normalized = value == null ? "" : value.trim();
        return TIANDITU_TOKEN_PATTERN.matcher(normalized).matches() ? normalized : "";
    }

    private NormalizedRenderOptions normalize(RenderOptions options) {
        var safeOptions = options == null ? RenderOptions.empty() : options;
        return new NormalizedRenderOptions(
            normalizeSceneType(safeOptions.sceneType()),
            normalizeDate(safeOptions.dateFrom()),
            normalizeDate(safeOptions.dateTo()),
            normalizeGrid(safeOptions.grid()),
            normalizeLimit(safeOptions.limit()),
            parseEmbedFlag(safeOptions.embed()),
            normalizeEmbedId(safeOptions.embedId())
        );
    }

    private String normalizeSceneType(String value) {
        var normalized = value == null ? "" : value.trim().toUpperCase();
        return SCENE_TYPE_PATTERN.matcher(normalized).matches() ? normalized : "";
    }

    private String normalizeDate(String value) {
        var normalized = value == null ? "" : value.trim();
        return DATE_PATTERN.matcher(normalized).matches() ? normalized : "";
    }

    private String normalizeGrid(String value) {
        var normalized = value == null ? "" : value.trim().toUpperCase();
        return GRID_PATTERN.matcher(normalized).matches() ? normalized : "";
    }

    private int normalizeLimit(String value) {
        var normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return DEFAULT_LIMIT;
        }
        try {
            var parsed = Integer.parseInt(normalized);
            if (parsed <= 0) {
                return DEFAULT_LIMIT;
            }
            return Math.min(parsed, MAX_LIMIT);
        } catch (NumberFormatException error) {
            return DEFAULT_LIMIT;
        }
    }

    private boolean parseEmbedFlag(String value) {
        if (value == null) {
            return false;
        }
        var normalized = value.trim().toLowerCase();
        return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized);
    }

    private String normalizeEmbedId(String value) {
        var normalized = value == null ? "" : value.trim();
        if (!EMBED_ID_PATTERN.matcher(normalized).matches()) {
            return "qsl-grid-map-default";
        }
        return normalized;
    }

    private String escapeHtml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
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

    private record NormalizedRenderOptions(
        String sceneType,
        String dateFrom,
        String dateTo,
        String grid,
        int limit,
        boolean embed,
        String embedId
    ) {
    }

    public record MapSettings(
        String tiandituAppKey,
        String tiandituSecretKey,
        String defaultCenterGrid,
        int defaultZoom
    ) {
        public static MapSettings empty() {
            return new MapSettings("", "", DEFAULT_CENTER_GRID, DEFAULT_ZOOM);
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
    }

    private static final String BASE_TEMPLATE = """
        <!doctype html>
        <html lang="zh-CN">
          <head>
            <meta charset="UTF-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
            <title>__TITLE__</title>
            <link
              rel="stylesheet"
              href="https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.css"
              integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY="
              crossorigin=""
            />
            <style>
              :root {
                color-scheme: light;
                font-family: "PingFang SC", "Microsoft YaHei", "Noto Sans SC", Arial, sans-serif;
              }
              * {
                box-sizing: border-box;
              }
              body {
                margin: 0;
                background: #eef2f7;
                color: #18202f;
              }
              body.qsl-grid-map-embed {
                background: transparent;
              }
              button,
              input,
              select {
                font: inherit;
              }
              .qsl-grid-page {
                min-height: 100vh;
                padding: 18px;
              }
              .qsl-grid-page.embed {
                min-height: 0;
                padding: 0;
              }
              .qsl-grid-shell {
                max-width: 1320px;
                margin: 0 auto;
                display: grid;
                grid-template-rows: auto minmax(560px, calc(100vh - 160px));
                gap: 12px;
              }
              .qsl-grid-page.embed .qsl-grid-shell {
                max-width: none;
                grid-template-rows: auto minmax(520px, 68vh);
              }
              .qsl-grid-toolbar {
                background: #ffffff;
                border: 1px solid #d9e0ea;
                border-radius: 8px;
                padding: 12px;
                display: grid;
                gap: 12px;
              }
              .qsl-grid-title-row {
                display: flex;
                align-items: flex-start;
                justify-content: space-between;
                gap: 12px;
              }
              .qsl-grid-title {
                margin: 0;
                font-size: 20px;
                line-height: 1.25;
                font-weight: 700;
              }
              .qsl-grid-desc {
                margin: 4px 0 0;
                color: #526070;
                font-size: 13px;
                line-height: 1.5;
              }
              .qsl-grid-status {
                min-width: 150px;
                text-align: right;
                color: #526070;
                font-size: 13px;
              }
              .qsl-grid-filter {
                display: grid;
                grid-template-columns: 110px 150px 150px 120px 100px auto;
                gap: 10px;
                align-items: end;
              }
              .qsl-field {
                display: grid;
                gap: 4px;
              }
              .qsl-field label {
                color: #526070;
                font-size: 12px;
              }
              .qsl-field input,
              .qsl-field select {
                width: 100%;
                height: 36px;
                border: 1px solid #cbd5e1;
                border-radius: 6px;
                background: #ffffff;
                color: #18202f;
                padding: 0 10px;
                outline: none;
              }
              .qsl-field input:focus,
              .qsl-field select:focus {
                border-color: #2f7dd1;
                box-shadow: 0 0 0 3px rgba(47, 125, 209, 0.14);
              }
              .qsl-actions {
                display: flex;
                gap: 8px;
                align-items: center;
              }
              .qsl-button {
                height: 36px;
                border-radius: 6px;
                border: 1px solid #2f7dd1;
                background: #2f7dd1;
                color: #ffffff;
                padding: 0 14px;
                cursor: pointer;
                white-space: nowrap;
              }
              .qsl-button.secondary {
                border-color: #cbd5e1;
                background: #ffffff;
                color: #253347;
              }
              .qsl-button:hover {
                filter: brightness(0.97);
              }
              .qsl-grid-workspace {
                display: grid;
                grid-template-columns: minmax(0, 1fr) 380px;
                gap: 12px;
                min-height: 0;
              }
              .qsl-map-panel,
              .qsl-list-panel {
                border: 1px solid #d9e0ea;
                border-radius: 8px;
                background: #ffffff;
                overflow: hidden;
                min-height: 0;
              }
              .qsl-map-panel {
                position: relative;
              }
              #qsl-map {
                width: 100%;
                height: 100%;
                min-height: 560px;
                background: #dfe7ef;
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
              .qsl-grid-page.embed #qsl-map {
                min-height: 520px;
              }
              .qsl-list-panel {
                display: grid;
                grid-template-rows: auto auto minmax(0, 1fr);
              }
              .qsl-summary {
                display: grid;
                grid-template-columns: repeat(3, minmax(0, 1fr));
                border-bottom: 1px solid #e5eaf1;
              }
              .qsl-summary-item {
                padding: 12px;
                border-right: 1px solid #e5eaf1;
              }
              .qsl-summary-item:last-child {
                border-right: 0;
              }
              .qsl-summary-label {
                color: #6a7686;
                font-size: 12px;
              }
              .qsl-summary-value {
                margin-top: 4px;
                color: #18202f;
                font-size: 22px;
                font-weight: 700;
                line-height: 1.1;
              }
              .qsl-selected {
                padding: 12px;
                border-bottom: 1px solid #e5eaf1;
                background: #f8fafc;
              }
              .qsl-selected-title {
                margin: 0 0 6px;
                font-size: 15px;
                font-weight: 700;
              }
              .qsl-selected-meta {
                display: flex;
                flex-wrap: wrap;
                gap: 6px;
              }
              .qsl-chip {
                display: inline-flex;
                align-items: center;
                min-height: 24px;
                border-radius: 999px;
                background: #e9f3ff;
                color: #1f5f9f;
                padding: 2px 8px;
                font-size: 12px;
              }
              .qsl-records {
                overflow: auto;
              }
              .qsl-table {
                width: 100%;
                border-collapse: collapse;
                font-size: 13px;
              }
              .qsl-table th,
              .qsl-table td {
                padding: 9px 10px;
                border-bottom: 1px solid #eef2f7;
                text-align: left;
                vertical-align: top;
              }
              .qsl-table th {
                position: sticky;
                top: 0;
                z-index: 1;
                background: #f8fafc;
                color: #526070;
                font-size: 12px;
                font-weight: 600;
              }
              .qsl-table tbody tr {
                cursor: pointer;
              }
              .qsl-table tbody tr:hover {
                background: #f6f9fc;
              }
              .qsl-message {
                margin: 0;
                padding: 12px;
                color: #526070;
                font-size: 13px;
                line-height: 1.5;
              }
              .qsl-message.error {
                color: #b42318;
                background: #fff4f2;
                border-top: 1px solid #ffd8d2;
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
              @media (max-width: 980px) {
                .qsl-grid-page {
                  padding: 10px;
                }
                .qsl-grid-shell {
                  grid-template-rows: auto auto;
                }
                .qsl-grid-filter {
                  grid-template-columns: repeat(2, minmax(0, 1fr));
                }
                .qsl-actions {
                  grid-column: 1 / -1;
                }
                .qsl-grid-workspace {
                  grid-template-columns: 1fr;
                }
                .qsl-map-panel {
                  min-height: 440px;
                }
                #qsl-map {
                  min-height: 440px;
                }
                .qsl-list-panel {
                  min-height: 360px;
                }
              }
              @media (max-width: 560px) {
                .qsl-grid-title-row {
                  display: grid;
                }
                .qsl-grid-status {
                  text-align: left;
                }
                .qsl-grid-filter {
                  grid-template-columns: 1fr;
                }
                .qsl-actions {
                  display: grid;
                  grid-template-columns: 1fr 1fr;
                }
                .qsl-button {
                  width: 100%;
                }
              }
            </style>
          </head>
          <body>
            <main id="qsl-grid-page" class="qsl-grid-page">
              <section class="qsl-grid-shell">
                <header class="qsl-grid-toolbar">
                  <div class="qsl-grid-title-row">
                    <div>
                      <h1 class="qsl-grid-title">QSL 通联网格地图</h1>
                      <p class="qsl-grid-desc">按四位 Maidenhead 网格展示公开通联记录，数据来自 QSL 管理插件公开接口。</p>
                    </div>
                    <div id="qsl-grid-status" class="qsl-grid-status">等待加载</div>
                  </div>
                  <form id="qsl-grid-filter" class="qsl-grid-filter">
                    <div class="qsl-field">
                      <label for="qsl-scene-type">sceneType（场景类型）</label>
                      <select id="qsl-scene-type" name="sceneType">
                        <option value="">全部</option>
                        <option value="QSO">QSO</option>
                        <option value="SWL">SWL</option>
                      </select>
                    </div>
                    <div class="qsl-field">
                      <label for="qsl-date-from">dateFrom（开始日期）</label>
                      <input id="qsl-date-from" name="dateFrom" type="date" value="__DATE_FROM_HTML__" />
                    </div>
                    <div class="qsl-field">
                      <label for="qsl-date-to">dateTo（结束日期）</label>
                      <input id="qsl-date-to" name="dateTo" type="date" value="__DATE_TO_HTML__" />
                    </div>
                    <div class="qsl-field">
                      <label for="qsl-grid-input">grid（网格）</label>
                      <input id="qsl-grid-input" name="grid" maxlength="4" placeholder="如 OM89" value="__GRID_HTML__" />
                    </div>
                    <div class="qsl-field">
                      <label for="qsl-limit-input">limit（上限）</label>
                      <input id="qsl-limit-input" name="limit" type="number" min="1" max="2000" value="__LIMIT_HTML__" />
                    </div>
                    <div class="qsl-actions">
                      <button class="qsl-button" type="submit">刷新地图</button>
                      <button class="qsl-button secondary" id="qsl-reset-button" type="button">重置</button>
                    </div>
                  </form>
                </header>

                <section class="qsl-grid-workspace">
                  <div class="qsl-map-panel">
                    <div id="qsl-map"></div>
                    <div id="qsl-map-notice" class="qsl-map-notice hidden"></div>
                  </div>
                  <aside class="qsl-list-panel">
                    <div class="qsl-summary">
                      <article class="qsl-summary-item">
                        <div class="qsl-summary-label">网格总数</div>
                        <div id="qsl-summary-grids" class="qsl-summary-value">0</div>
                      </article>
                      <article class="qsl-summary-item">
                        <div class="qsl-summary-label">通联明细</div>
                        <div id="qsl-summary-records" class="qsl-summary-value">0</div>
                      </article>
                      <article class="qsl-summary-item">
                        <div class="qsl-summary-label">呼号数量</div>
                        <div id="qsl-summary-calls" class="qsl-summary-value">0</div>
                      </article>
                    </div>
                    <div class="qsl-selected">
                      <h2 id="qsl-selected-title" class="qsl-selected-title">未选择网格</h2>
                      <div id="qsl-selected-meta" class="qsl-selected-meta"></div>
                    </div>
                    <div class="qsl-records">
                      <table class="qsl-table">
                        <thead>
                          <tr>
                            <th>grid（网格）</th>
                            <th>callSign（呼号）</th>
                            <th>date（日期）</th>
                            <th>mode（模式）</th>
                          </tr>
                        </thead>
                        <tbody id="qsl-record-body"></tbody>
                      </table>
                      <p id="qsl-message" class="qsl-message">正在加载通联网格数据。</p>
                    </div>
                  </aside>
                </section>
              </section>
            </main>

            <script
              src="https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.js"
              integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo="
              crossorigin=""
            ></script>
            <script>
              (() => {
                const API_URL = "/apis/api.qsl-management.bi1kbu.com/v1alpha1/qso-public/grids";
                const EMBED_MODE = __EMBED_MODE__;
                const EMBED_ID = "__EMBED_ID__";
                const TIANDITU_CONFIG = {
                  configured: __TIANDITU_CONFIGURED__,
                  appKey: "__TIANDITU_APP_KEY_JS__"
                };
                const DEFAULT_MAP_VIEW = {
                  centerGrid: "__DEFAULT_CENTER_GRID_JS__",
                  zoom: Number("__DEFAULT_ZOOM_JS__") || 3
                };
                const initialState = {
                  sceneType: "__SCENE_TYPE_JS__",
                  dateFrom: "__DATE_FROM_JS__",
                  dateTo: "__DATE_TO_JS__",
                  grid: "__GRID_JS__",
                  limit: Number("__LIMIT_JS__") || 500
                };

                const page = document.getElementById("qsl-grid-page");
                const form = document.getElementById("qsl-grid-filter");
                const status = document.getElementById("qsl-grid-status");
                const sceneTypeInput = document.getElementById("qsl-scene-type");
                const dateFromInput = document.getElementById("qsl-date-from");
                const dateToInput = document.getElementById("qsl-date-to");
                const gridInput = document.getElementById("qsl-grid-input");
                const limitInput = document.getElementById("qsl-limit-input");
                const resetButton = document.getElementById("qsl-reset-button");
                const summaryGrids = document.getElementById("qsl-summary-grids");
                const summaryRecords = document.getElementById("qsl-summary-records");
                const summaryCalls = document.getElementById("qsl-summary-calls");
                const selectedTitle = document.getElementById("qsl-selected-title");
                const selectedMeta = document.getElementById("qsl-selected-meta");
                const recordBody = document.getElementById("qsl-record-body");
                const message = document.getElementById("qsl-message");
                const mapNotice = document.getElementById("qsl-map-notice");

                const state = {
                  map: null,
                  layerGroup: null,
                  rectangles: new Map(),
                  items: []
                };

                if (EMBED_MODE) {
                  document.body.classList.add("qsl-grid-map-embed");
                  page.classList.add("embed");
                }

                sceneTypeInput.value = initialState.sceneType;

                function notifyParentHeight() {
                  if (!EMBED_MODE || window.parent === window) {
                    return;
                  }
                  const height = Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);
                  window.parent.postMessage({ type: "qsl-grid-map-height", embedId: EMBED_ID, height }, "*");
                }

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

                function normalizeLimit(value) {
                  const parsed = Number.parseInt(value, 10);
                  if (!Number.isFinite(parsed) || parsed <= 0) {
                    return 500;
                  }
                  return Math.min(parsed, 2000);
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
                    zoomControl: true
                  }).setView(defaultMapCenter(), DEFAULT_MAP_VIEW.zoom);
                  state.map.attributionControl.setPrefix(false);
                  if (TIANDITU_CONFIG.configured) {
                    const tileOptions = {
                      minZoom: 1,
                      maxZoom: 18,
                      subdomains: "01234567",
                      attribution: '&copy; <a href="https://www.tianditu.gov.cn/">天地图</a>'
                    };
                    L.tileLayer(`https://t{s}.tianditu.gov.cn/DataServer?T=vec_w&x={x}&y={y}&l={z}&tk=${TIANDITU_CONFIG.appKey}`, tileOptions)
                      .on("tileerror", () => {
                        status.textContent = "天地图瓦片加载失败";
                        mapNotice.classList.remove("hidden");
                        mapNotice.textContent = "天地图瓦片加载失败，请检查插件设置中的应用 key、域名白名单和当前网络。";
                      })
                      .addTo(state.map);
                    L.tileLayer(`https://t{s}.tianditu.gov.cn/DataServer?T=cva_w&x={x}&y={y}&l={z}&tk=${TIANDITU_CONFIG.appKey}`, tileOptions)
                      .addTo(state.map);
                  } else {
                    status.textContent = "请先在插件设置中填写天地图应用 key";
                    mapNotice.classList.remove("hidden");
                    mapNotice.textContent = "请先在 Halo 后台的 QSL 通联网格地图插件设置中填写天地图应用 key。";
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
                  state.rectangles.clear();
                  const maxCount = Math.max(1, ...items.map((item) => (item.records || []).length));
                  const boundsList = [];

                  for (const item of items) {
                    const bounds = gridBounds(item.grid);
                    if (!bounds) {
                      continue;
                    }
                    boundsList.push(bounds);
                    const count = (item.records || []).length;
                    const color = colorFor(count, maxCount);
                    const rectangle = L.rectangle(bounds, {
                      color,
                      weight: 1,
                      fillColor: color,
                      fillOpacity: Math.max(0.18, Math.min(0.55, 0.18 + count / maxCount * 0.36))
                    }).bindPopup(popupHtml(item));
                    rectangle.on("click", () => selectGrid(item.grid));
                    rectangle.addTo(state.layerGroup);
                    state.rectangles.set(item.grid, rectangle);
                  }

                  if (boundsList.length) {
                    state.map.fitBounds(state.layerGroup.getBounds(), { padding: [26, 26], maxZoom: 6 });
                  } else {
                    state.map.setView(defaultMapCenter(), DEFAULT_MAP_VIEW.zoom);
                  }
                }

                function flattenRecords(items) {
                  const rows = [];
                  for (const item of items) {
                    const records = Array.isArray(item.records) ? item.records : [];
                    for (const record of records) {
                      rows.push({ grid: item.grid, ...record });
                    }
                  }
                  return rows;
                }

                function renderSummary(items, recordTotal) {
                  const callSigns = new Set();
                  for (const item of items) {
                    for (const callSign of item.callSigns || []) {
                      if (callSign) {
                        callSigns.add(callSign);
                      }
                    }
                  }
                  summaryGrids.textContent = String(items.length);
                  summaryRecords.textContent = String(recordTotal);
                  summaryCalls.textContent = String(callSigns.size);
                }

                function renderTable(items, activeGrid = "") {
                  const rows = flattenRecords(activeGrid ? items.filter((item) => item.grid === activeGrid) : items);
                  if (!rows.length) {
                    recordBody.innerHTML = "";
                    message.className = "qsl-message";
                    message.textContent = activeGrid ? "当前网格没有通联明细。" : "没有符合条件的通联网格数据。";
                    notifyParentHeight();
                    return;
                  }
                  message.textContent = "";
                  recordBody.innerHTML = rows.map((row) => {
                    const dateTime = [row.date, row.time].filter(Boolean).join(" ");
                    const modeBand = [row.mode, row.band || row.frequency].filter(Boolean).join(" / ");
                    return `<tr data-grid="${escapeHtml(row.grid)}">
                      <td>${escapeHtml(row.grid)}</td>
                      <td>${escapeHtml(row.callSign)}</td>
                      <td>${escapeHtml(dateTime)}</td>
                      <td>${escapeHtml(modeBand)}</td>
                    </tr>`;
                  }).join("");
                  for (const row of recordBody.querySelectorAll("tr[data-grid]")) {
                    row.addEventListener("click", () => selectGrid(row.getAttribute("data-grid") || ""));
                  }
                  notifyParentHeight();
                }

                function selectGrid(grid) {
                  const item = state.items.find((candidate) => candidate.grid === grid);
                  if (!item) {
                    return;
                  }
                  const records = Array.isArray(item.records) ? item.records : [];
                  const calls = Array.isArray(item.callSigns) ? item.callSigns : [];
                  selectedTitle.textContent = `${item.grid} 网格`;
                  selectedMeta.innerHTML = [
                    `<span class="qsl-chip">通联 ${records.length}</span>`,
                    `<span class="qsl-chip">呼号 ${calls.length}</span>`,
                    ...calls.slice(0, 8).map((callSign) => `<span class="qsl-chip">${escapeHtml(callSign)}</span>`)
                  ].join("");
                  renderTable(state.items, item.grid);
                  const rectangle = state.rectangles.get(item.grid);
                  if (rectangle) {
                    rectangle.openPopup();
                    const bounds = gridBounds(item.grid);
                    if (bounds) {
                      state.map.panTo(centerOf(bounds));
                    }
                  }
                }

                function setLoading(loading) {
                  status.textContent = loading ? "加载中" : "加载完成";
                  form.querySelectorAll("button, input, select").forEach((node) => {
                    node.disabled = loading;
                  });
                }

                function setError(error) {
                  status.textContent = "加载失败";
                  recordBody.innerHTML = "";
                  message.className = "qsl-message error";
                  message.textContent = error?.message || "地图数据加载失败。";
                  selectedTitle.textContent = "未选择网格";
                  selectedMeta.innerHTML = "";
                  renderSummary([], 0);
                  notifyParentHeight();
                }

                function currentQuery() {
                  return {
                    sceneType: sceneTypeInput.value.trim(),
                    dateFrom: dateFromInput.value.trim(),
                    dateTo: dateToInput.value.trim(),
                    grid: normalizeGrid(gridInput.value),
                    limit: normalizeLimit(limitInput.value)
                  };
                }

                async function loadData() {
                  setLoading(true);
                  message.className = "qsl-message";
                  message.textContent = "正在加载通联网格数据。";
                  try {
                    const query = currentQuery();
                    gridInput.value = query.grid;
                    limitInput.value = String(query.limit);
                    const params = new URLSearchParams();
                    for (const [key, value] of Object.entries(query)) {
                      if (value !== "" && value !== null && value !== undefined) {
                        params.set(key, String(value));
                      }
                    }
                    const response = await fetch(`${API_URL}?${params.toString()}`, {
                      method: "GET",
                      credentials: "same-origin",
                      headers: { "Accept": "application/json" }
                    });
                    const result = await response.json();
                    const data = getApiData(result);
                    const items = Array.isArray(data.items) ? data.items : [];
                    state.items = items;
                    renderSummary(items, Number(data.recordTotal ?? flattenRecords(items).length));
                    renderMap(items);
                    selectedTitle.textContent = "未选择网格";
                    selectedMeta.innerHTML = "";
                    renderTable(items);
                    status.textContent = `加载完成：${items.length} 个网格`;
                  } catch (error) {
                    setError(error);
                  } finally {
                    setLoading(false);
                    setTimeout(() => {
                      if (state.map) {
                        state.map.invalidateSize();
                      }
                      notifyParentHeight();
                    }, 50);
                  }
                }

                form.addEventListener("submit", (event) => {
                  event.preventDefault();
                  loadData();
                });

                resetButton.addEventListener("click", () => {
                  sceneTypeInput.value = "";
                  dateFromInput.value = "";
                  dateToInput.value = "";
                  gridInput.value = "";
                  limitInput.value = "500";
                  loadData();
                });

                if (window.ResizeObserver) {
                  const resizeObserver = new ResizeObserver(() => notifyParentHeight());
                  resizeObserver.observe(document.body);
                }
                window.addEventListener("load", notifyParentHeight);
                window.addEventListener("resize", notifyParentHeight);

                try {
                  initMap();
                  loadData();
                } catch (error) {
                  setError(error);
                }
              })();
            </script>
          </body>
        </html>
        """;
}
