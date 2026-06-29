package com.bi1kbu.qslgridmap.front;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.theme.ReactiveSinglePageContentHandler;

@Component
public class QslGridMapSinglePageContentHandler implements ReactiveSinglePageContentHandler {

    private final QslGridMapContentTransformer transformer;

    public QslGridMapSinglePageContentHandler(QslGridMapContentTransformer transformer) {
        this.transformer = transformer;
    }

    @Override
    public Mono<SinglePageContentContext> handle(@NonNull SinglePageContentContext singlePageContent) {
        return Mono.just(SinglePageContentContext.builder()
            .singlePage(singlePageContent.getSinglePage())
            .content(transformer.transform(singlePageContent.getContent()))
            .raw(singlePageContent.getRaw())
            .rawType(singlePageContent.getRawType())
            .build());
    }
}
