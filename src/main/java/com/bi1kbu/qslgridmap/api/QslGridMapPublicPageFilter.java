package com.bi1kbu.qslgridmap.api;

import com.bi1kbu.qslgridmap.front.QslGridMapDataSource;
import com.bi1kbu.qslgridmap.front.QslGridMapPageRenderService;
import com.bi1kbu.qslgridmap.front.QslGridMapSettingsResolver;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.app.security.BeforeSecurityWebFilter;

@Component
public class QslGridMapPublicPageFilter implements BeforeSecurityWebFilter {
    private static final String PAGE_PATH = "/apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page";
    private static final String SHORT_LINK_PATH = "/gridmap";
    private final QslGridMapPageRenderService pageRenderService;
    private final ReactiveSettingFetcher settingFetcher;
    private final QslGridMapDataSource sources;

    public QslGridMapPublicPageFilter(QslGridMapPageRenderService pageRenderService,
        ReactiveSettingFetcher settingFetcher, QslGridMapDataSource sources) {
        this.pageRenderService = pageRenderService;
        this.settingFetcher = settingFetcher;
        this.sources = sources;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();
        if (!HttpMethod.GET.equals(request.getMethod())) {
            return chain.filter(exchange);
        }
        String path = request.getPath().pathWithinApplication().value();
        boolean shortPath = SHORT_LINK_PATH.equals(path);
        boolean scopedPath = path.matches("/gridmap/[A-Za-z0-9]{1,64}");
        if (!shortPath && !scopedPath && !PAGE_PATH.equals(path)) {
            return chain.filter(exchange);
        }
        String callsign = scopedPath ? path.substring(SHORT_LINK_PATH.length() + 1)
            : shortPath ? "" : request.getQueryParams().getFirst("callsign");
        String contextPath = request.getPath().contextPath().value();
        return sources.resolve(callsign, contextPath).flatMap(source -> {
            var response = exchange.getResponse();
            response.getHeaders().setCacheControl("no-store");
            response.getHeaders().set("X-Content-Type-Options", "nosniff");
            if (shortPath && !source.modern()) {
                // 2.x保留原固定站内跳转，不透传查询参数。
                response.setStatusCode(HttpStatus.FOUND);
                response.getHeaders().setLocation(URI.create(contextPath + PAGE_PATH));
                return response.setComplete();
            }
            var options = QslGridMapPageRenderService.RenderOptions.empty();
            return QslGridMapSettingsResolver.mapSettings(settingFetcher).flatMap(settings -> {
                response.getHeaders().setContentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8));
                String body = pageRenderService.render(options, settings, source);
                return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
            });
        }).onErrorResume(error -> {
            var response = exchange.getResponse();
            if (response.isCommitted()) { return Mono.error(error); }
            var status = error instanceof ResponseStatusException e ? e.getStatusCode() : HttpStatus.SERVICE_UNAVAILABLE;
            String message = error instanceof ResponseStatusException e && e.getReason() != null
                ? e.getReason() : "地图暂时无法读取，请稍后重试。";
            response.setStatusCode(status);
            response.getHeaders().setCacheControl("no-store");
            response.getHeaders().setContentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(response.bufferFactory().wrap(message.getBytes(StandardCharsets.UTF_8))));
        });
    }
}
