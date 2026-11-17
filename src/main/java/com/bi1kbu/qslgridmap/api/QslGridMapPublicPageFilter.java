package com.bi1kbu.qslgridmap.api;

import com.bi1kbu.qslgridmap.front.QslGridMapPageRenderService;
import com.bi1kbu.qslgridmap.front.QslGridMapSettingsResolver;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
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

    public QslGridMapPublicPageFilter(QslGridMapPageRenderService pageRenderService,
        ReactiveSettingFetcher settingFetcher) {
        this.pageRenderService = pageRenderService;
        this.settingFetcher = settingFetcher;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();
        if (!HttpMethod.GET.equals(request.getMethod())) {
            return chain.filter(exchange);
        }
        var path = request.getPath().pathWithinApplication().value();
        if (SHORT_LINK_PATH.equals(path)) {
            // 固定站内目标，不透传查询参数，避免开放重定向和筛选参数扩散。
            var response = exchange.getResponse();
            response.setStatusCode(HttpStatus.FOUND);
            response.getHeaders().setLocation(
                URI.create(request.getPath().contextPath().value() + PAGE_PATH));
            return response.setComplete();
        }
        if (!PAGE_PATH.equals(path)) {
            return chain.filter(exchange);
        }

        var response = exchange.getResponse();
        response.getHeaders().setContentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8));
        var options = new QslGridMapPageRenderService.RenderOptions(
            queryParam(exchange, "sceneType", "st"),
            queryParam(exchange, "dateFrom", "df"),
            queryParam(exchange, "dateTo", "dt"),
            queryParam(exchange, "grid", "g"),
            queryParam(exchange, "limit", "l"),
            queryParam(exchange, "embed", "e"),
            queryParam(exchange, "embedId", "eid")
        );
        return QslGridMapSettingsResolver.mapSettings(settingFetcher)
            .flatMap(settings -> {
                var body = pageRenderService.render(options, settings);
                var buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
                return response.writeWith(Mono.just(buffer));
            });
    }

    private String queryParam(ServerWebExchange exchange, String primary, String alias) {
        var params = exchange.getRequest().getQueryParams();
        var primaryValue = params.getFirst(primary);
        if (primaryValue != null && !primaryValue.trim().isBlank()) {
            return primaryValue.trim();
        }
        var aliasValue = params.getFirst(alias);
        return aliasValue == null ? "" : aliasValue.trim();
    }
}
