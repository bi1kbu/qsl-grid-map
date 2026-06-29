# qsl-grid-map

QSL 通联网格地图是一个 Halo 2 前台展示插件，用于把 `qsl-management` 已公开的通联网格数据展示为交互地图。

## 简介

本插件提供公开地图页面、短码嵌入能力和天地图服务后台配置，不维护独立通联数据，不创建角色模板，不提供自定义写入接口。

公开页面：

```text
/apis/qsl-grid-map.bi1kbu.com/v1alpha1/map/page
```

短码：

```text
[qsl-grid-map]
```

数据来源：

```text
/apis/api.qsl-management.bi1kbu.com/v1alpha1/qso-public/grids
```

插件依赖：

```text
qsl-management >=2.3.21
```

## 配置

安装并启用插件后，在 Halo 后台进入插件设置，填写天地图服务配置：

1. `appKey（应用 key）`：必填，用作天地图瓦片服务的 `tk` 参数。
2. `secretKey（密钥）`：可选，当前不会输出到前台页面。

## 开发环境

- Java 21+
- Node.js 18+
- pnpm
- Halo >=2.25.0

## 开发

```bash
# 启用插件
./gradlew haloServer
```

开发服务器默认使用独立容器 `halo-qsl-grid-map-development`，访问地址：

```text
http://localhost:8091
```

## 构建

```bash
./gradlew build
```

构建完成后，可以在 `build/libs` 目录找到插件 jar 文件。

当前产物示例：

```text
build/libs/plugin-qsl-grid-map-0.0.4.jar
```

## 许可证

[GPL-3.0](./LICENSE) © bi1kbu 
