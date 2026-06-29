package com.bi1kbu.qslgridmap.front;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class QslGridMapContentTransformer {

    private static final Pattern SHORTCODE_PATTERN = Pattern.compile("(?is)\\[qsl-grid-map([^\\]]*)\\]");

    public String transform(String content) {
        if (content == null || content.isBlank() || !content.contains("[qsl-grid-map")) {
            return content;
        }

        var matcher = SHORTCODE_PATTERN.matcher(content);
        var builder = new StringBuilder();
        var prefix = "qsl-grid-map-" + UUID.randomUUID().toString().replace("-", "");
        var sequence = 1;
        while (matcher.find()) {
            var embedId = prefix + "-" + sequence++;
            var replacement = buildEmbedBlock(embedId);
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private String buildEmbedBlock(String embedId) {
        var src = "/apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page?embed=1&eid=" + embedId;

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
}
