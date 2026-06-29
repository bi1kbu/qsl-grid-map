package com.bi1kbu.qslgridmap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * 插件主类，负责管理插件生命周期。
 *
 * @author bi1kbu
 * @since 0.0.1
 */
@Component
public class QslGridMapPlugin extends BasePlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(QslGridMapPlugin.class);

    public QslGridMapPlugin(PluginContext pluginContext) {
        super(pluginContext);
    }

    @Override
    public void start() {
        LOGGER.info("QSL 通联网格地图插件启动成功。");
    }

    @Override
    public void stop() {
        LOGGER.info("QSL 通联网格地图插件已停止。");
    }
}
