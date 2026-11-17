package com.bi1kbu.qslgridmap.front;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.Plugin;
import run.halo.app.extension.ReactiveExtensionClient;

/** 只根据宿主已安装插件的版本选择公开接口，不在业务请求失败时回退旧数据。 */
@Component
public class QslGridMapDataSource {
    private final ReactiveExtensionClient client;

    public QslGridMapDataSource(ReactiveExtensionClient client) {
        this.client = client;
    }

    public Mono<Source> resolve(String callsign, String contextPath) {
        return Mono.defer(() -> {
            String target = normalizeCallsign(callsign);
            return client.fetch(Plugin.class, "qsl-management")
                .switchIfEmpty(Mono.error(unavailable()))
                .map(plugin -> {
                    var spec = plugin.getSpec();
                    if (spec == null || !Boolean.TRUE.equals(spec.getEnabled())) {
                        throw unavailable();
                    }
                    return forVersion(spec.getVersion(), target, contextPath);
                });
        });
    }

    static Source forVersion(String version, String callsign, String contextPath) {
        String prefix = contextPath == null ? "" : contextPath;
        if (version != null && version.matches("3\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.+-]+)?")) {
            String query = callsign.isEmpty() ? "" : "?callsign=" + callsign;
            return new Source(true, prefix + "/apis/api.qsl.halo.run/v1alpha1/public-grid-map" + query);
        }
        if (version != null && version.matches("2\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.+-]+)?")) {
            if (!callsign.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "地图不存在。");
            }
            return Source.legacy(prefix);
        }
        throw unavailable();
    }

    public static String normalizeCallsign(String value) {
        String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (!normalized.isEmpty() && !normalized.matches("[a-z0-9]{1,64}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "呼号路径须为单段字母数字。");
        }
        return normalized;
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "QSL 插件尚未就绪或版本不受支持。");
    }

    public record Source(boolean modern, String apiUrl) {
        public static Source legacy(String prefix) {
            return new Source(false, prefix + "/apis/api.qsl-management.bi1kbu.com/v1alpha1/qso-public/grids?sceneType=QSO");
        }
    }
}
