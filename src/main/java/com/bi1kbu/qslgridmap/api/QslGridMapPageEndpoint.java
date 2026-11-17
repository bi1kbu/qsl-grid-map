package com.bi1kbu.qslgridmap.api;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import com.bi1kbu.qslgridmap.front.QslGridMapPageRenderService;
import com.bi1kbu.qslgridmap.front.QslGridMapDataSource;
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
    private final QslGridMapDataSource sources;

    public QslGridMapPageEndpoint(QslGridMapPageRenderService pageRenderService,
        ReactiveSettingFetcher settingFetcher, QslGridMapDataSource sources) {
        this.pageRenderService = pageRenderService;
        this.settingFetcher = settingFetcher;
        this.sources = sources;
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
        return sources.resolve(request.queryParam("callsign").orElse(""),
                request.requestPath().contextPath().value())
            .flatMap(source -> QslGridMapSettingsResolver.mapSettings(settingFetcher)
                .flatMap(settings -> ServerResponse.ok()
                    .header("Cache-Control", "no-store")
                    .contentType(MediaType.TEXT_HTML)
                    .bodyValue(pageRenderService.render(QslGridMapPageRenderService.RenderOptions.empty(), settings, source))));
    }
}
