const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

// 执行 Java 渲染的真实页面脚本，以可控事件和时钟覆盖浏览器中的异步切换。
const fixtures = process.env.QSL_MAP_TEST_FIXTURES;
assert.ok(fixtures, "必须由 Java 测试提供页面样本目录。");

class Events {
  constructor() {
    this.events = new Map();
  }

  on(type, handler) {
    if (typeof type === "object") {
      for (const [name, callback] of Object.entries(type)) this.on(name, callback);
      return this;
    }
    const handlers = this.events.get(type) || [];
    handlers.push(handler);
    this.events.set(type, handlers);
    return this;
  }

  once(type, handler) {
    const wrapped = (event) => {
      this.off(type, wrapped);
      handler(event);
    };
    return this.on(type, wrapped);
  }

  off(type, handler) {
    if (type && typeof type === "object") {
      for (const [name, callback] of Object.entries(type)) this.off(name, callback);
      return this;
    }
    if (!type) this.events.clear();
    else if (!handler) this.events.delete(type);
    else this.events.set(type, (this.events.get(type) || []).filter((item) => item !== handler));
    return this;
  }

  fire(type, detail = {}) {
    for (const handler of [...(this.events.get(type) || [])]) {
      handler.call(this, { type, target: this, ...detail });
    }
    return this;
  }
}

async function page(fileName = "tianditu.html", options = {}) {
  const html = fs.readFileSync(path.join(fixtures, fileName), "utf8");
  const scripts = [...html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/g)]
    .filter((match) => !/\bsrc\s*=/.test(match[1])).map((match) => match[2]);
  const elements = new Map();
  const resources = [];
  const maps = [];
  const tiles = [];
  const glLayers = [];
  const groups = [];
  const rectangles = [];
  const warnings = [];
  const timers = new Map();
  let now = 0;
  let timerSequence = 0;
  let resolveFetch;
  const fetchPromise = new Promise((resolve) => { resolveFetch = resolve; });

  function element(tag = "div") {
    const result = new Events();
    const classes = new Set();
    result.tagName = tag.toUpperCase();
    result.children = [];
    result.style = {};
    result.dataset = {};
    result.textContent = "";
    result.classList = {
      add: (...names) => names.forEach((name) => classes.add(name)),
      remove: (...names) => names.forEach((name) => classes.delete(name)),
      contains: (name) => classes.has(name),
      toggle(name, force) {
        const include = force === undefined ? !classes.has(name) : force;
        if (include) classes.add(name); else classes.delete(name);
        return include;
      }
    };
    result.setAttribute = (name, value) => { result[name] = value; };
    result.getAttribute = (name) => result[name];
    result.addEventListener = result.on.bind(result);
    result.removeEventListener = result.off.bind(result);
    result.appendChild = (child) => {
      result.children.push(child);
      child.parentNode = result;
      if (child.tagName === "SCRIPT" || child.tagName === "LINK") {
        resources.push(child);
        if (options.resourceMode !== "pending") {
          queueMicrotask(() => {
            const event = options.resourceMode === "error" ? "error" : "load";
            if (event === "load" && child.tagName === "SCRIPT") {
              context.maplibregl = { supported: () => !options.unsupportedWebgl };
              context.L.maplibreGL = createGlLayer;
            }
            child[`on${event}`]?.({ target: child });
            child.fire(event);
          });
        }
      }
      return child;
    };
    result.append = (...children) => children.forEach(result.appendChild);
    result.removeChild = (child) => {
      result.children = result.children.filter((item) => item !== child);
      child.parentNode = null;
    };
    result.remove = () => result.parentNode?.removeChild(result);
    return result;
  }

  function layer(kind, settings = {}) {
    const result = new Events();
    result.kind = kind;
    result.options = settings;
    result.addTo = (map) => {
      result._map = map;
      map.addLayer(result);
      if (kind === "tile") {
        // Leaflet 通过图层的 remove 事件解除地图监听；提前 off 会误删该内部钩子。
        const onMovement = () => {
          assert.ok(result._map, "已移除的瓦片层不应继续响应地图移动或缩放。");
        };
        const events = { move: onMovement, zoom: onMovement };
        map.on(events);
        result.once("remove", () => map.off(events));
        result.fire("loading");
      }
      return result;
    };
    result.remove = () => { result._map?.removeLayer(result); return result; };
    result.bindPopup = (content) => { result.popup = content; return result; };
    result.bringToFront = () => result;
    return result;
  }

  function createGlLayer(settings) {
    const result = layer("gl", settings);
    const gl = new Events();
    gl.styles = [];
    gl.setStyle = (style) => {
      gl.styles.push(style);
      if (options.styleThrows) throw new Error("测试样式配置失败");
      return gl;
    };
    gl.loaded = () => gl.complete === true;
    gl.isStyleLoaded = gl.loaded;
    gl.areTilesLoaded = gl.loaded;
    gl.getCanvas = () => element("canvas");
    gl.remove = () => { gl.removed = true; };
    result.getMaplibreMap = () => options.glThrows ? undefined : gl;
    result.onRemove = () => {
      // 桥接库 0.1.4 在构造 GL 对象失败时仍会尝试调用未初始化对象的 remove。
      result.getMaplibreMap().remove();
    };
    const addTo = result.addTo;
    result.addTo = (map) => {
      addTo(map);
      if (options.glThrows) throw new Error("测试 WebGL 初始化失败");
      return result;
    };
    result.gl = gl;
    result.getEvents = () => ({});
    const container = element();
    result.getContainer = () => container;
    glLayers.push(result);
    return result;
  }

  const L = {
    map(id, settings) {
      const map = new Events();
      map.options = settings;
      map.layers = new Set();
      map.views = [];
      map.rangeChanges = [];
      map.attributions = new Set();
      map.attributionControl = {
        setPrefix() {},
        addAttribution: (value) => map.attributions.add(value),
        removeAttribution: (value) => map.attributions.delete(value)
      };
      map.setView = (center, zoom) => {
        const coordinates = Array.isArray(center) ? [...center] : [center.lat, center.lng];
        map.views.push({ center: coordinates, zoom: Math.max(map.options.minZoom, Math.min(map.options.maxZoom, zoom)) });
        return map;
      };
      map.getCenter = () => ({ lat: map.views.at(-1).center[0], lng: map.views.at(-1).center[1] });
      map.getZoom = () => map.views.at(-1).zoom;
      map.getMinZoom = () => map.options.minZoom;
      map.getMaxZoom = () => map.options.maxZoom;
      map.setZoom = (zoom) => map.setView(map.getCenter(), zoom);
      for (const [method, key] of [["setMinZoom", "minZoom"], ["setMaxZoom", "maxZoom"]]) {
        map[method] = (value) => {
          map.options[key] = value;
          map.rangeChanges.push([map.options.minZoom, map.options.maxZoom]);
          assert.ok(map.options.minZoom <= map.options.maxZoom, "更新顺序不能短暂产生相反的缩放上下界。");
          if (map.getZoom() < map.getMinZoom() || map.getZoom() > map.getMaxZoom()) {
            map.setZoom(map.getZoom());
          }
          return map;
        };
      }
      map.addLayer = (entry) => { map.layers.add(entry); return map; };
      map.removeLayer = (entry) => {
        if (!map.layers.has(entry)) return map;
        // 与 Leaflet 一致：onRemove 抛错时，注册表和 remove 事件均尚未处理。
        entry.onRemove?.(map);
        map.layers.delete(entry);
        entry.fire("remove");
        entry._map = null;
        return map;
      };
      map.hasLayer = (entry) => map.layers.has(entry);
      map.invalidateSize = () => map;
      map.setMaxBounds = () => map;
      maps.push(map);
      return map;
    },
    tileLayer(url, settings) {
      const result = layer("tile", settings);
      result.url = url;
      tiles.push(result);
      return result;
    },
    featureGroup() {
      const result = layer("group");
      result.layers = new Set();
      result.addLayer = (entry) => { result.layers.add(entry); return result; };
      result.clearLayers = () => result.layers.clear();
      groups.push(result);
      return result;
    },
    rectangle(bounds, settings) {
      const result = layer("rectangle", settings);
      result.bounds = bounds;
      rectangles.push(result);
      return result;
    }
  };
  const document = {
    createElement: element,
    getElementById(id) {
      if (!elements.has(id)) elements.set(id, element());
      return elements.get(id);
    },
    querySelector: () => null,
    head: element("head"),
    body: element("body")
  };
  const context = {
    document, L, URL, console: { ...console, warn: (...values) => warnings.push(values) }, Promise,
    fetch: () => fetchPromise,
    setTimeout(callback, delay) {
      const id = ++timerSequence;
      timers.set(id, { callback, at: now + Number(delay) });
      return id;
    },
    clearTimeout: (id) => timers.delete(id),
    addEventListener() {},
    removeEventListener() {}
  };
  context.window = context;
  vm.createContext(context);
  for (const script of scripts) vm.runInContext(script, context, { timeout: 1000 });

  async function flush() {
    for (let index = 0; index < 30; index++) await Promise.resolve();
  }

  async function advance(milliseconds) {
    const target = now + milliseconds;
    let iterations = 0;
    while (true) {
      const pending = [...timers].filter(([, timer]) => timer.at <= target)
        .sort((left, right) => left[1].at - right[1].at)[0];
      if (!pending) break;
      assert.ok(++iterations < 100, "计时器不应形成循环。");
      now = pending[1].at;
      timers.delete(pending[0]);
      pending[1].callback();
      await flush();
    }
    now = target;
    await flush();
  }

  await flush();
  return {
    maps, tiles, glLayers, groups, rectangles, resources, warnings, flush, advance,
    notice: () => [...elements.values()].filter((item) => !item.classList.contains("hidden"))
      .map((item) => item.textContent).join("\n"),
    noticeIsError: () => elements.get("qsl-map-notice")?.style.color === "#b42318",
    async data(items = [{ grid: "OM89", records: [{}], callSigns: ["TEST"] }]) {
      resolveFetch({ ok: true, json: async () => ({ data: { items } }) });
      await flush();
    },
    async loaded() {
      for (const tile of tiles) tile.fire("tileload").fire("load");
      await flush();
    },
    async glLoaded() {
      glLayers[0].gl.complete = true;
      glLayers[0].gl.fire("load");
      await flush();
    }
  };
}

test("天地图加载成功时不请求备用地图依赖", async () => {
  const browser = await page();
  assert.equal(browser.tiles.length, 2);
  await browser.loaded();
  await browser.advance(30000);
  assert.equal(browser.glLayers.length, 0);
  assert.equal(browser.resources.length, 0);
});

for (const [index, name] of [[0, "底图"], [1, "注记"]]) {
  test(`天地图${name}失败后只切换一次并移除两层`, async () => {
    const browser = await page();
    browser.tiles[index].fire("tileerror");
    browser.tiles[1 - index].fire("tileerror");
    browser.tiles[index].fire("tileerror");
    await browser.flush();
    assert.equal(browser.glLayers.length, 1);
    for (const tile of browser.tiles) assert.equal(browser.maps[0].hasLayer(tile), false);
    await browser.advance(15000);
    assert.equal(browser.glLayers.length, 1);
  });
}

test("天地图首批瓦片等待满十五秒后启用备用地图", async () => {
  const browser = await page();
  await browser.advance(14999);
  assert.equal(browser.glLayers.length, 0);
  await browser.advance(1);
  assert.equal(browser.glLayers.length, 1);
});

test("天地图后续移动加载的瓦片同样受十五秒超时约束", async () => {
  const browser = await page();
  await browser.loaded();
  await browser.advance(20000);
  browser.tiles[1].fire("loading");
  await browser.advance(14999);
  assert.equal(browser.glLayers.length, 0);
  await browser.advance(1);
  assert.equal(browser.glLayers.length, 1);
});

test("一层加载完成不能取消另一层的超时保护", async () => {
  const browser = await page();
  browser.tiles[0].fire("tileload").fire("load");
  await browser.advance(15000);
  assert.equal(browser.glLayers.length, 1);
});

for (const [fileName, name] of [["no-key.html", "未填写天地图应用密钥"], ["openfreemap.html", "后台选择 OpenFreeMap"]]) {
  test(`${name}时直接加载 OpenFreeMap`, async () => {
    const browser = await page(fileName);
    assert.equal(browser.tiles.length, 0);
    assert.equal(browser.glLayers.length, 1);
    assert.ok(browser.resources.length > 0);
  });
}

test("底图切换保持同一地图、当前视图、通联网格和本台网格", async () => {
  const browser = await page();
  await browser.data();
  const map = browser.maps[0];
  const group = browser.groups[0];
  const overlay = [...group.layers][0];
  const home = browser.rectangles.find((entry) => entry.options.dashArray);
  map.setView([48, -120], 5);
  browser.tiles[0].fire("tileerror");
  await browser.flush();
  await browser.glLoaded();
  assert.equal(browser.maps.length, 1);
  assert.equal(browser.groups.length, 1);
  assert.equal(map.getZoom(), 5);
  assert.equal(map.getCenter().lat, 48);
  assert.equal(map.getCenter().lng, -120);
  assert.ok(map.hasLayer(group));
  assert.ok(group.layers.has(overlay));
  assert.ok(map.hasLayer(home));
  assert.doesNotThrow(() => map.fire("move").fire("zoom"));
  assert.equal((map.events.get("move") || []).length, 0);
  assert.equal((map.events.get("zoom") || []).length, 0);
});

test("地图数据成功不能清除 OpenFreeMap 的加载错误", async () => {
  const browser = await page("tianditu.html", { resourceMode: "error" });
  browser.tiles[0].fire("tileerror");
  await browser.flush();
  assert.match(browser.notice(), /OpenFreeMap/);
  assert.match(browser.notice(), /失败|无法|不可用/);
  await browser.data();
  assert.match(browser.notice(), /OpenFreeMap/);
  assert.match(browser.notice(), /失败|无法|不可用/);
});

test("备用地图静态资源失败显示中文错误且不重新请求天地图", async () => {
  const browser = await page("openfreemap.html", { resourceMode: "error" });
  assert.match(browser.notice(), /OpenFreeMap/);
  assert.match(browser.notice(), /失败|无法|不可用/);
  assert.equal(browser.tiles.length, 0);
  assert.equal(browser.glLayers.length, 0);
});

test("备用地图静态资源超时可结束等待并显示中文错误", async () => {
  const browser = await page("openfreemap.html", { resourceMode: "pending" });
  await browser.advance(15000);
  assert.match(browser.notice(), /OpenFreeMap/);
  assert.match(browser.notice(), /超时|失败|无法|不可用/);
});

test("备用地图 WebGL 初始化异常显示中文错误", async () => {
  const browser = await page("openfreemap.html", { glThrows: true });
  assert.match(browser.notice(), /OpenFreeMap/);
  assert.match(browser.notice(), /初始化失败.*WebGL/);
  assert.equal(browser.maps[0].hasLayer(browser.glLayers[0]), false,
    "初始化失败的桥接图层必须从 Leaflet 注册表移除。");
  assert.doesNotThrow(() => browser.maps[0].fire("move").fire("zoom"));
});

test("备用地图同步样式配置异常显示样式错误并移除图层", async () => {
  const browser = await page("openfreemap.html", { styleThrows: true });
  assert.match(browser.notice(), /OpenFreeMap.*样式加载失败/);
  assert.equal(browser.maps[0].hasLayer(browser.glLayers[0]), false);
  await browser.advance(30000);
  assert.match(browser.notice(), /样式加载失败/);
});

test("备用地图样式或通用资源错误不会被完成事件误清除或循环切换", async () => {
  const browser = await page("openfreemap.html");
  const gl = browser.glLayers[0].gl;
  gl.fire("error", { error: new Error("测试样式或字形加载失败") });
  await browser.flush();
  assert.match(browser.notice(), /OpenFreeMap/);
  assert.match(browser.notice(), /样式或地图资源加载失败/);
  await browser.glLoaded();
  gl.fire("idle");
  await browser.data();
  assert.match(browser.notice(), /样式或地图资源加载失败/);
  assert.equal(browser.noticeIsError(), true);
  assert.equal(browser.tiles.length, 0);
  assert.equal(browser.glLayers.length, 1);
});

test("备用地图二十秒慢加载仅提示继续等待并在迟到的绘制完成后恢复", async () => {
  const browser = await page("openfreemap.html");
  await browser.advance(20000);
  assert.match(browser.notice(), /OpenFreeMap/);
  assert.match(browser.notice(), /加载较慢.*仍在继续/);
  assert.doesNotMatch(browser.notice(), /失败|不可用/);
  assert.equal(browser.noticeIsError(), false);
  await browser.advance(22000);
  await browser.glLoaded();
  assert.equal(browser.notice(), "");
  assert.equal(browser.glLayers.length, 1);
});

function sourceTile(sourceId = "ne2_shaded", z = 3, x = 4, y = 2) {
  const coord = { canonical: { z, x, y } };
  return { sourceId, tile: { tileID: coord }, coord, dataType: "source" };
}

test("局部瓦片失败保留地图和网格并在空闲事件后仍提示局部问题", async () => {
  const browser = await page("openfreemap.html");
  await browser.data();
  await browser.glLoaded();
  const gl = browser.glLayers[0].gl;
  const group = browser.groups[0];
  const overlay = [...group.layers][0];
  gl.fire("error", { ...sourceTile(), error: new Error("测试瓦片网络错误") });
  gl.fire("idle");
  assert.match(browser.notice(), /瓦片加载失败.*已加载区域仍可浏览/);
  assert.equal(browser.noticeIsError(), false);
  assert.ok(browser.maps[0].hasLayer(browser.glLayers[0]));
  assert.ok(browser.maps[0].hasLayer(group));
  assert.ok(group.layers.has(overlay));
  assert.equal(browser.tiles.length, 0);
  assert.equal(browser.glLayers.length, 1);
});

test("其他瓦片或其他来源同坐标成功不能消除原瓦片错误", async () => {
  const browser = await page("openfreemap.html");
  await browser.glLoaded();
  const gl = browser.glLayers[0].gl;
  const failure = sourceTile();
  gl.fire("error", { ...failure, error: new Error("测试瓦片网络错误") });
  gl.fire("sourcedata", sourceTile("ne2_shaded", 3, 5, 2));
  gl.fire("sourcedata", sourceTile("openmaptiles"));
  gl.fire("sourcedata", { dataType: "source", sourceId: failure.sourceId, sourceDataType: "metadata" });
  gl.fire("idle");
  assert.match(browser.notice(), /瓦片加载失败/);
});

test("失败瓦片本身成功后清除局部错误", async () => {
  const browser = await page("openfreemap.html");
  await browser.glLoaded();
  const gl = browser.glLayers[0].gl;
  const failure = sourceTile();
  gl.fire("error", { ...failure, error: new Error("测试瓦片网络错误") });
  gl.fire("sourcedata", failure);
  assert.equal(browser.notice(), "");
});

test("同来源同坐标的新瓦片对象成功后清除被替换瓦片的旧错误", async () => {
  const browser = await page("openfreemap.html");
  await browser.glLoaded();
  const gl = browser.glLayers[0].gl;
  const failure = sourceTile();
  // error 事件使用 tile.tileID，成功事件使用公开的 coord，两者不必共享对象。
  gl.fire("error", { sourceId: failure.sourceId, tile: failure.tile, error: new Error("测试瓦片网络错误") });
  const success = sourceTile();
  assert.notEqual(success.tile, failure.tile);
  gl.fire("sourcedata", success);
  assert.equal(browser.notice(), "");
});

test("所有当前瓦片失败时完成和空闲事件都不能误报加载成功", async () => {
  const browser = await page("openfreemap.html");
  const gl = browser.glLayers[0].gl;
  const first = sourceTile();
  const second = sourceTile("ne2_shaded", 3, 5, 2);
  gl.fire("error", { ...first, error: new Error("第一块瓦片失败") });
  gl.fire("error", { ...second, error: new Error("第二块瓦片失败") });
  // MapLibre 的 loaded / areTilesLoaded 会将 errored 瓦片视为已完成。
  await browser.glLoaded();
  gl.fire("idle");
  assert.equal(gl.areTilesLoaded(), true);
  assert.match(browser.notice(), /瓦片加载失败/);
  gl.fire("sourcedata", first).fire("idle");
  assert.match(browser.notice(), /瓦片加载失败/);
  gl.fire("sourcedata", second).fire("idle");
  assert.equal(browser.notice(), "");
});

test("数据源错误仅在对应来源元数据成功后恢复", async () => {
  const browser = await page("openfreemap.html");
  const gl = browser.glLayers[0].gl;
  gl.fire("error", { sourceId: "openmaptiles", error: new Error("测试数据源加载失败") });
  await browser.glLoaded();
  gl.fire("sourcedata", sourceTile("openmaptiles"));
  gl.fire("sourcedata", { dataType: "source", sourceId: "ne2_shaded", sourceDataType: "metadata" });
  gl.fire("idle");
  assert.match(browser.notice(), /数据源加载失败/);
  assert.equal(browser.noticeIsError(), true);
  gl.fire("sourcedata", { dataType: "source", sourceId: "openmaptiles", sourceDataType: "metadata" });
  assert.equal(browser.notice(), "");
});

test("备用地图 WebGL 上下文丢失的提示不被迟到的首次绘制事件清除", async () => {
  const browser = await page("openfreemap.html");
  const gl = browser.glLayers[0].gl;
  gl.fire("webglcontextlost");
  await browser.glLoaded();
  gl.fire("idle");
  assert.match(browser.notice(), /OpenFreeMap/);
  assert.match(browser.notice(), /上下文已丢失.*等待浏览器恢复/);
  assert.equal(browser.noticeIsError(), true);
});

test("备用地图 WebGL 上下文恢复后等待重新绘制再显示成功", async () => {
  const browser = await page("openfreemap.html");
  await browser.glLoaded();
  const gl = browser.glLayers[0].gl;
  gl.fire("webglcontextlost");
  gl.complete = false;
  gl.fire("webglcontextrestored").fire("idle");
  assert.match(browser.notice(), /正在加载 OpenFreeMap/);
  gl.complete = true;
  gl.fire("idle");
  assert.equal(browser.notice(), "");
});

test("绘图上下文恢复不能顺带清除仍失败的地图资源", async () => {
  const browser = await page("openfreemap.html");
  await browser.glLoaded();
  const gl = browser.glLayers[0].gl;
  gl.fire("error", { error: new Error("测试字形资源加载失败") });
  gl.fire("webglcontextlost").fire("webglcontextrestored").fire("idle");
  assert.match(browser.notice(), /样式或地图资源加载失败/);
  assert.equal(browser.noticeIsError(), true);
});

test("地图失败日志包含安全诊断而不泄露资源地址或令牌", async () => {
  const browser = await page("openfreemap.html");
  const gl = browser.glLayers[0].gl;
  const sensitiveUrl = "https://map.example.test/tile?key=private-key-987&token=private-token-654";
  const error = Object.assign(new Error(sensitiveUrl), { status: 503, url: sensitiveUrl });
  gl.fire("error", { ...sourceTile(), error });
  gl.fire("error", { sourceId: sensitiveUrl, error });
  gl.fire("error", { error });
  const output = JSON.stringify(browser.warnings);
  assert.ok(browser.warnings.length >= 3);
  assert.match(output, /地图瓦片/);
  assert.match(output, /ne2_shaded/);
  assert.match(output, /503/);
  assert.doesNotMatch(output, /https:\/\/|map\.example\.test|private-key|private-token|test-key-123/);
  assert.doesNotMatch(browser.notice(), /map\.example\.test|private-key|private-token/);
});

for (const [fileName, name] of [["openfreemap-custom.html", "直接首选 OFM"], ["no-key-custom.html", "天地图密钥为空"]]) {
  test(`${name}从首帧应用 OFM 独立缩放并使用共用中心网格及样式`, async () => {
    const browser = await page(fileName);
    const map = browser.maps[0];
    assert.equal(browser.tiles.length, 0);
    assert.equal(map.getZoom(), 6);
    assert.deepEqual(map.getCenter(), { lat: 40.5, lng: -75 });
    assert.deepEqual([map.getMinZoom(), map.getMaxZoom()], [5, 7]);
    assert.equal(map.views[0].zoom, 6, "初始画面不能先使用天地图默认级别再切换。");
    const home = browser.rectangles.find((entry) => entry.options.dashArray && map.hasLayer(entry));
    assert.ok(home);
    assert.equal(home.options.color, "#16a34a");
    assert.match(home.popup, /FN20/);
    assert.equal(browser.glLayers[0].options.interactive, false);
    assert.equal(browser.glLayers[0].options.minZoom, undefined, "桥接层不能收到未经级别换算的 Leaflet 下界。");
    assert.equal(browser.glLayers[0].options.maxZoom, undefined, "桥接层不能收到未经级别换算的 Leaflet 上界。");
  });
}

test("首选天地图使用自己的缩放范围和共用本台网格", async () => {
  const browser = await page("fallback-custom.html");
  const map = browser.maps[0];
  assert.equal(map.getZoom(), 6);
  assert.deepEqual([map.getMinZoom(), map.getMaxZoom()], [2, 10]);
  assert.deepEqual(map.getCenter(), { lat: 40.5, lng: -75 });
  const home = browser.rectangles.find((entry) => entry.options.dashArray);
  assert.equal(home.options.color, "#16a34a");
  assert.match(home.popup, /FN20/);
  assert.equal(browser.glLayers.length, 0);
});

for (const [oldZoom, newZoom, description] of [[4, 5, "低于下界"], [6, 6, "位于范围内"], [9, 7, "高于上界"]]) {
  test(`切换 OFM 时当前缩放${description}按独立范围处理且保持中心与通联网格`, async () => {
    const browser = await page("fallback-custom.html");
    await browser.data();
    const map = browser.maps[0];
    const group = browser.groups[0];
    const overlay = [...group.layers][0];
    const oldHome = browser.rectangles.find((entry) => entry.options.dashArray);
    map.setView([48, -120], oldZoom);
    browser.tiles[0].fire("tileerror");
    await browser.flush();
    assert.deepEqual([map.getMinZoom(), map.getMaxZoom()], [5, 7]);
    assert.equal(map.getZoom(), newZoom);
    assert.deepEqual(map.getCenter(), { lat: 48, lng: -120 });
    assert.equal(browser.maps.length, 1);
    assert.ok(map.hasLayer(group));
    assert.ok(group.layers.has(overlay));
    assert.ok(map.hasLayer(oldHome));
    assert.equal(oldHome.options.color, "#16a34a");
    assert.match(oldHome.popup, /FN20/);
  });
}

test("切换到完全更低的 OFM 范围时正确收缩上界并保持共用隐藏设置", async () => {
  const browser = await page("fallback-lower.html");
  const map = browser.maps[0];
  assert.equal(browser.rectangles.filter((entry) => entry.options.dashArray).length, 0);
  map.setView([48, -120], 12);
  browser.tiles[1].fire("tileerror");
  await browser.flush();
  assert.deepEqual([map.getMinZoom(), map.getMaxZoom()], [2, 5]);
  assert.equal(map.getZoom(), 5);
  assert.deepEqual(map.getCenter(), { lat: 48, lng: -120 });
  assert.equal(browser.rectangles.filter((entry) => entry.options.dashArray && map.hasLayer(entry)).length, 0);
  assert.ok(map.rangeChanges.every(([minimum, maximum]) => minimum <= maximum));
});

test("切换到完全更高的 OFM 范围时先扩展上界并保留共用本台网格", async () => {
  const browser = await page("fallback-higher.html");
  const map = browser.maps[0];
  const originalHome = browser.rectangles.find((entry) => entry.options.dashArray);
  map.setView([48, -120], 3);
  browser.tiles[1].fire("tileerror");
  await browser.flush();
  assert.deepEqual([map.getMinZoom(), map.getMaxZoom()], [10, 14]);
  assert.equal(map.getZoom(), 10);
  assert.deepEqual(map.getCenter(), { lat: 48, lng: -120 });
  const home = browser.rectangles.find((entry) => entry.options.dashArray && map.hasLayer(entry));
  assert.equal(home, originalHome);
  assert.match(home.popup, /FN20/);
  assert.equal(home.options.color, "#16a34a");
  assert.ok(map.rangeChanges.every(([minimum, maximum]) => minimum <= maximum));
});

test("切换 OFM 后晚到的空数据不得把视图复位为任一服务的默认中心或缩放", async () => {
  const browser = await page("fallback-custom.html");
  const map = browser.maps[0];
  map.setView([48, -120], 9);
  browser.tiles[0].fire("tileerror");
  await browser.flush();
  await browser.glLoaded();
  await browser.data([]);
  assert.deepEqual(map.getCenter(), { lat: 48, lng: -120 });
  assert.equal(map.getZoom(), 7);
  assert.deepEqual([map.getMinZoom(), map.getMaxZoom()], [5, 7]);
  assert.match(browser.notice(), /没有符合条件的 QSO/);
});
