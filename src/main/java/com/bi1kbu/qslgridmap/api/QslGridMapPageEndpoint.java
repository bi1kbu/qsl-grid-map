package com.bi1kbu.qslgridmap.api;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import com.bi1kbu.qslgridmap.front.QslGridMapPageRenderService;
import com.bi1kbu.qslgridmap.front.QslGridMapSettingsResolver;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.plugin.ReactiveSettingFetcher;

@Component
public class QslGridMapPageEndpoint implements CustomEndpoint {

    private final QslGridMapPageRenderService pageRenderService;
    private final ReactiveSettingFetcher settingFetcher;

    public QslGridMapPageEndpoint(QslGridMapPageRenderService pageRenderService,
        ReactiveSettingFetcher settingFetcher) {
        this.pageRenderService = pageRenderService;
        this.settingFetcher = settingFetcher;
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return route(GET("/map/page"), this::renderMapPage);
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("qsl-grid-map.bi1kbu.com/v1alpha1");
    }

    private Mono<ServerResponse> renderMapPage(ServerRequest request) {
        var options = new QslGridMapPageRenderService.RenderOptions(
            queryParam(request, "sceneType", "st"),
            queryParam(request, "dateFrom", "df"),
            queryParam(request, "dateTo", "dt"),
            queryParam(request, "grid", "g"),
            queryParam(request, "limit", "l"),
            queryParam(request, "embed", "e"),
            queryParam(request, "embedId", "eid")
        );
        return QslGridMapSettingsResolver.mapSettings(settingFetcher)
            .flatMap(settings -> ServerResponse.ok()
                .contentType(MediaType.TEXT_HTML)
                .bodyValue(pageRenderService.render(options, settings)));
    }

    private String queryParam(ServerRequest request, String primary, String alias) {
        var primaryValue = request.queryParam(primary).orElse("").trim();
        if (!primaryValue.isBlank()) {
            return primaryValue;
        }
        return request.queryParam(alias).orElse("").trim();
    }
}
