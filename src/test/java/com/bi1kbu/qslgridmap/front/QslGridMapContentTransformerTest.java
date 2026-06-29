package com.bi1kbu.qslgridmap.front;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QslGridMapContentTransformerTest {

    private final QslGridMapContentTransformer transformer = new QslGridMapContentTransformer();

    @Test
    void transformShortcodeToEmbedPage() {
        var transformed = transformer.transform("""
            <p>前置内容</p>
            [qsl-grid-map sceneType="QSO" dateFrom="2026-01-01" dateTo="2026-12-31" grid="om89" limit="500"]
            """);

        assertThat(transformed).contains("/apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page");
        assertThat(transformed).contains("embed=1");
        assertThat(transformed).contains("sceneType=QSO");
        assertThat(transformed).contains("dateFrom=2026-01-01");
        assertThat(transformed).contains("dateTo=2026-12-31");
        assertThat(transformed).contains("grid=OM89");
        assertThat(transformed).contains("limit=500");
        assertThat(transformed).contains("QSL 通联网格地图");
    }

    @Test
    void ignoreInvalidShortcodeAttributes() {
        var transformed = transformer.transform("""
            [qsl-grid-map sceneType="ONLINE_EYEBALL" grid="<script>" limit="99999"]
            """);

        assertThat(transformed).doesNotContain("sceneType=ONLINE_EYEBALL");
        assertThat(transformed).doesNotContain("grid=%3Cscript%3E");
        assertThat(transformed).contains("limit=2000");
    }

    @Test
    void keepContentWithoutShortcode() {
        var content = "<p>普通内容</p>";

        assertThat(transformer.transform(content)).isEqualTo(content);
    }
}
