package com.bi1kbu.qslgridmap.front;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class QslGridMapContentTransformer {

    private static final Pattern SHORTCODE_PATTERN = Pattern.compile("(?is)\\[qsl-grid-map([^\\]]*)\\]");
    private static final Pattern SCENE_TYPE_ATTR_PATTERN = attrPattern("sceneType");
    private static final Pattern DATE_FROM_ATTR_PATTERN = attrPattern("dateFrom");
    private static final Pattern DATE_TO_ATTR_PATTERN = attrPattern("dateTo");
    private static final Pattern GRID_ATTR_PATTERN = attrPattern("grid");
    private static final Pattern LIMIT_ATTR_PATTERN = attrPattern("limit");
    private static final Pattern SCENE_TYPE_PATTERN = Pattern.compile("^(QSO|SWL)$");
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern GRID_PATTERN = Pattern.compile("^[A-R]{2}[0-9]{2}$");

    public String transform(String content) {
        if (content == null || content.isBlank() || !content.contains("[qsl-grid-map")) {
            return content;
        }

        var matcher = SHORTCODE_PATTERN.matcher(content);
        var builder = new StringBuilder();
        var prefix = "qsl-grid-map-" + UUID.randomUUID().toString().replace("-", "");
        var sequence = 1;
        while (matcher.find()) {
            var attributes = matcher.group(1);
            var embedId = prefix + "-" + sequence++;
            var replacement = buildEmbedBlock(attributes, embedId);
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private String buildEmbedBlock(String attributes, String embedId) {
        var uriBuilder = UriComponentsBuilder
            .fromPath("/apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page")
            .queryParam("embed", "1")
            .queryParam("eid", embedId);
        addIfPresent(uriBuilder, "sceneType", normalizeSceneType(extractAttrValue(attributes, SCENE_TYPE_ATTR_PATTERN)));
        addIfPresent(uriBuilder, "dateFrom", normalizeDate(extractAttrValue(attributes, DATE_FROM_ATTR_PATTERN)));
        addIfPresent(uriBuilder, "dateTo", normalizeDate(extractAttrValue(attributes, DATE_TO_ATTR_PATTERN)));
        addIfPresent(uriBuilder, "grid", normalizeGrid(extractAttrValue(attributes, GRID_ATTR_PATTERN)));
        addIfPresent(uriBuilder, "limit", normalizeLimit(extractAttrValue(attributes, LIMIT_ATTR_PATTERN)));
        var src = uriBuilder.build().toUriString();

        return """
            <div class="qsl-grid-map-embed" style="margin: 16px 0;">
              <iframe
                src="%s"
                data-qsl-grid-map-embed-id="%s"
                style="width: 100%%; min-height: 560px; border: 0; border-radius: 8px; background: transparent;"
                loading="lazy"
                referrerpolicy="same-origin"
                title="QSL 通联网格地图"
              ></iframe>
            </div>
            <script>
              (function () {
                if (window.__qslGridMapEmbedResizeBound) {
                  return;
                }
                window.__qslGridMapEmbedResizeBound = true;
                window.addEventListener("message", function (event) {
                  var data = event.data;
                  if (!data || data.type !== "qsl-grid-map-height" || !data.embedId) {
                    return;
                  }
                  var iframe = document.querySelector('iframe[data-qsl-grid-map-embed-id="' + data.embedId + '"]');
                  if (!iframe) {
                    return;
                  }
                  var parsedHeight = Number(data.height);
                  if (!Number.isFinite(parsedHeight)) {
                    return;
                  }
                  iframe.style.height = Math.max(560, parsedHeight) + "px";
                });
              })();
            </script>
            """.formatted(src, embedId);
    }

    private static Pattern attrPattern(String attrName) {
        return Pattern.compile("(?i)" + attrName + "\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s\\]]+))");
    }

    private String extractAttrValue(String source, Pattern pattern) {
        if (source == null || source.isBlank()) {
            return "";
        }
        var matcher = pattern.matcher(source);
        if (!matcher.find()) {
            return "";
        }
        return firstNotBlank(matcher.group(2), matcher.group(3), matcher.group(4)).trim();
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

    private String normalizeLimit(String value) {
        var normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return "";
        }
        try {
            var parsed = Integer.parseInt(normalized);
            if (parsed <= 0) {
                return "";
            }
            return Integer.toString(Math.min(parsed, 2000));
        } catch (NumberFormatException error) {
            return "";
        }
    }

    private void addIfPresent(UriComponentsBuilder uriBuilder, String name, String value) {
        if (value != null && !value.isBlank()) {
            uriBuilder.queryParam(name, value);
        }
    }

    private String firstNotBlank(String... candidates) {
        if (candidates == null) {
            return "";
        }
        for (var candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }
}
