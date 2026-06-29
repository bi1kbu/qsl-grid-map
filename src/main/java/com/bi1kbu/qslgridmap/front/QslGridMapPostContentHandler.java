package com.bi1kbu.qslgridmap.front;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.theme.ReactivePostContentHandler;

@Component
public class QslGridMapPostContentHandler implements ReactivePostContentHandler {

    private final QslGridMapContentTransformer transformer;

    public QslGridMapPostContentHandler(QslGridMapContentTransformer transformer) {
        this.transformer = transformer;
    }

    @Override
    public Mono<PostContentContext> handle(@NonNull PostContentContext postContent) {
        return Mono.just(PostContentContext.builder()
            .post(postContent.getPost())
            .content(transformer.transform(postContent.getContent()))
            .raw(postContent.getRaw())
            .rawType(postContent.getRawType())
            .build());
    }
}
