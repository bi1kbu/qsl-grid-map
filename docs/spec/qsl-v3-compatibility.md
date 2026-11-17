# QSL 2.x／3.0 网格适配实施基线

日期：2026-11-17。用户明确授权在保留 2.x 支持的前提下实现；保留另一对话完成的 0.0.24 地图服务与独立缩放。本轮不改 QSL 业务模型，也不在地图插件注册业务 Scheme。

## 接口与职责

- 复用 Halo `BeforeSecurityWebFilter` 的精确匿名 GET 页面入口和 `CustomEndpoint` 原页面 API。
- `/gridmap` 在 QSL 3 下显示站点默认呼号，`/gridmap/{callsign}` 显示指定实例，`/gridmap/all` 显示全站统计。3.x 短路径直接返回同一页面，保留地址栏路径；2.x 原 `/gridmap` 的固定302和原API、短码保持。
- 通过 Halo `ReactiveExtensionClient` 只读已安装 `qsl-management` 的 Plugin 版本元数据选择适配器；不把 HTTP 错误当成旧版识别依据，不在 3.x 出错时回退读取冻结的2.x源。
- 2.x 浏览器请求原 `GET /apis/api.qsl-management.bi1kbu.com/v1alpha1/qso-public/grids?sceneType=QSO`。
- 3.x 浏览器请求 `GET /apis/api.qsl.halo.run/v1alpha1/public-grid-map`，可选 `callsign` 为主呼号或 all，无值由 QSL3 解析默认。响应为 `schemaVersion=1, scope, callsign, homeGrid, showCallsigns, totalGrids, totalRecords, items[{grid,count,callSigns}]`。
- 数据权限、全站计数、公开开关和只返回公开字段由 QSL3 负责。地图仅将两种响应规范为 grid/count/callSigns，3.x 不读取完整QSO；全站默认不显示对方呼号，关状态不渲染呼号行。
- 3.x 本站位置来自响应 `homeGrid`；无网格或全站时不画本站标记。地图默认中心与各服务缩放保持0.0.24行为，数据到达不重置当前视图。

## 安全与权限

- 只公开上述精确 GET 页面；主呼号路径仅允许单段 ASCII 字母数字，不接受外部 URL、额外路径段或可执行内容。大小写统一比较；all为特殊范围。
- HTML 返回 no-store，实例显示开关变化不由缓存延迟；接口失败明确显示，不当成空数据。查询只传经过校验的呼号以及宿主实际contextPath。
- 不新增地图插件写接口、原始业务读取权限或角色模板。配置继续使用宿主设置与既有权限；QSL3公开聚合与管理权限由QSL3维护。
- 保留原始API和短码兼容。两个版本的地图服务/缩放共用现有渲染逻辑，不改底图供应商或外部资源地址。

## 验证与待决

先测版本解析、路径/contextPath与输入边界、2.x正常数据、3.x聚合数据与呼号隐藏、3.x接口失败不回退、本站网格与既有OFM缩放。部署由主任务统一安排，不执行默认8091 reloadPlugin覆盖其他环境。

用户已确认2.x访问新增呼号/all路径直接404，原默认路径及原页面API保持。真实Halo运行证据在完成后补记，不把自动测试作为整套兼容已验。

## 官方依据

核验日期2026-11-17：[插件依赖](https://docs.halo.run/developer-guide/plugin/interaction/dependency)、[API变更](https://docs.halo.run/developer-guide/plugin/api-changelog)。插件间不引用对方实现类；仅声明经验证版本范围。此次基线覆盖旧文档“数据仅来自2.x／仅两条固定GET”的限制，其他地图与安全约定继续有效。


## 自动化结果（0.0.25）

2026-11-17：原build目录被正在运行的dev服务器锁定，首次测试未进入代码测试；使用单次Gradle init脚本隔离输出到tmp/build-v3-compat。第一次实际运行仅新增Node测试误用模拟器字段失败，修正测试后完整build通过：56个JUnit，其中1个调用43个Node页面测试，全部通过且不能重复累计。2.x原OFM/天地图/独立缩放35项Node回归继续通过；新增3.0聚合、隐藏呼号、本站位置、HTTP403/404/409/503不回退、协议错误及2.xHTTP错误8项通过。2.x真实运行结果见下节，3.0由主任务继续联调。

无新增业务存储或角色模板。依赖范围保持既有2.x下限并增加4.0.0上限；此范围不是对所有版本逐一实测的声明。3.0需要含本次公开接口的配套版本，较早3.0返回明确接口错误而不读取旧源。

## 2.x开发环境接口验证（0.0.25）

2026-11-17在原8091开发环境核验：Halo 2.25、QSL 2.3.35，沿用`workplace`原数据库、插件及配置。开发进程已结束且临时容器不存在时，通过官方`haloServer -x build`恢复同一工作目录，没有重新初始化站点。升级前记录公开接口基线，升级后官方`reloadPlugin -x build`报告插件已就绪。

- `/gridmap`继续302到原地图页面API，不转发查询参数中的呼号；原页面与2.x公开数据API均200。
- `/gridmap/all`、`/gridmap/bi1kbu`、大写呼号路径以及原页面API带`callsign`均返回404；额外路径段404，匿名POST短路径403。
- 页面明确选择旧公开数据地址，保留OpenFreeMap逻辑；升级前后旧数据JSON相同。该开发库当前没有QSO，因此这次真实接口验证覆盖空结果，不声称完成非空数据地图或浏览器视觉验收。
- 默认开发构建目录曾并存0.0.20至0.0.25多个JAR，旧组件索引使新DataSource未被注册；把旧构建JAR移入`tmp/live-2x-20261117/previous-build-jars`留存，仅让当前包参与开发类路径后，重载正常。未修改任何业务数据或邮件设置。

私有证据位于`tmp/live-2x-20261117`：`files-before.json`、`before-http.json`、`after-http.json`、`assemble.log`及`reload-current.log`。0.0.25默认输出和先前隔离测试JAR的代码与资源逐项相同，只有MANIFEST中的构建JDK标记不同（25/21）；编译目标仍是Java21。8091开发服务保持运行。3.0实测和视觉核验尚需分别完成，不能由本节推定通过。
