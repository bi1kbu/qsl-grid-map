package com.bi1kbu.qslgridmap.front;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.Plugin;
import run.halo.app.extension.ReactiveExtensionClient;

class QslGridMapDataSourceTest {
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final QslGridMapDataSource source = new QslGridMapDataSource(client);

    @Test
    void selectsModernFromInstalledVersionAndKeepsContextPath() {
        install("3.0.0-rc.45", true);
        var actual = source.resolve(" BI1KBU ", "/blog").block();
        assertThat(actual.modern()).isTrue();
        assertThat(actual.apiUrl()).isEqualTo("/blog/apis/api.qsl.halo.run/v1alpha1/public-grid-map?callsign=bi1kbu");
        assertThat(source.resolve("all", "").block().apiUrl()).endsWith("?callsign=all");
        assertThat(source.resolve(null, "").block().apiUrl()).doesNotContain("?");
    }

    @Test
    void keepsLegacyDefaultButRejectsNewScopes() {
        install("2.3.35", true);
        assertThat(source.resolve(null, "/blog").block()).isEqualTo(QslGridMapDataSource.Source.legacy("/blog"));
        for (String call : new String[] {"all", "BI1KBU"}) {
            assertThatThrownBy(() -> source.resolve(call, "").block())
                .isInstanceOfSatisfying(ResponseStatusException.class,
                    error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "4.0.0", "latest", "3", "2.3.x", "3.0.0<script>"})
    void unknownVersionCannotSilentlySelectLegacy(String version) {
        install(version, true);
        assertUnavailable(() -> source.resolve("", "").block());
    }

    @Test
    void missingOrDisabledPluginIsUnavailable() {
        when(client.fetch(Plugin.class, "qsl-management")).thenReturn(Mono.empty());
        assertUnavailable(() -> source.resolve("", "").block());
        install("3.0.0-rc.45", false);
        assertUnavailable(() -> source.resolve("", "").block());
    }

    @ParameterizedTest
    @ValueSource(strings = {"BI1KBU/P", "../all", "<script>", "//evil.invalid", "a?callsign=all", "a&scope=all"})
    void rejectsParametersThatCouldChangeTargetOrEscapeScript(String callsign) {
        assertThatThrownBy(() -> source.resolve(callsign, "").block())
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(400));
    }

    private void install(String version, boolean enabled) {
        var plugin = new Plugin();
        var spec = new Plugin.PluginSpec();
        spec.setVersion(version);
        spec.setEnabled(enabled);
        plugin.setSpec(spec);
        when(client.fetch(Plugin.class, "qsl-management")).thenReturn(Mono.just(plugin));
    }

    private void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode().value()).isEqualTo(503));
    }
}
