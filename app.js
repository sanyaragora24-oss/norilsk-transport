/* Норильский транспорт — веб-версия (PWA).
 * Данные: data.json, собирается web/build.py из тех же файлов, что и Android-приложение.
 * Время — всегда норильское (Asia/Krasnoyarsk), независимо от часового пояса телефона.
 */
(() => {
  "use strict";

  const TZ = "Asia/Krasnoyarsk";
  const NORILSK = [69.3500, 88.2000];
  const FAV_KEY = "nt.favorites.v1";
  const $ = (s, el = document) => el.querySelector(s);
  const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

  // ---------- хранилище (может быть недоступно в приватном режиме Safari) ----------
  const store = {
    get(k, d) { try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : d; } catch (e) { return d; } },
    set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) { /* без сохранения */ } }
  };
  let favs = store.get(FAV_KEY, { routes: [], stops: [] });
  const isFavRoute = (n) => favs.routes.includes(n);
  const isFavStop = (k) => favs.stops.includes(k);
  function toggleFav(kind, key) {
    const arr = favs[kind];
    const i = arr.indexOf(key);
    if (i >= 0) arr.splice(i, 1); else arr.push(key);
    store.set(FAV_KEY, favs);
    return i < 0;
  }

  // ---------- время ----------
  const fmt = new Intl.DateTimeFormat("ru-RU", { timeZone: TZ, hour: "2-digit", minute: "2-digit", weekday: "short", hour12: false });
  function nowNorilsk() {
    const parts = Object.fromEntries(fmt.formatToParts(new Date()).map((p) => [p.type, p.value]));
    const h = parseInt(parts.hour, 10) % 24, m = parseInt(parts.minute, 10);
    const wd = parts.weekday.toLowerCase();
    return { min: h * 60 + m, hhmm: `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`, weekend: wd.startsWith("сб") || wd.startsWith("вс"), wd };
  }
  const toMin = (t) => { const m = /^(\d{1,2}):(\d{2})/.exec(t); return m ? (+m[1]) * 60 + (+m[2]) : null; };
  function inText(diff) {
    if (diff <= 0) return "сейчас";
    if (diff < 60) return `через ${diff} мин`;
    const h = Math.floor(diff / 60), m = diff % 60;
    return `через ${h} ч${m ? " " + m + " мин" : ""}`;
  }
  function nextFrom(times, now) {
    const mins = times.map(toMin).filter((x) => x != null);
    const i = mins.findIndex((m) => m >= now);
    return i < 0 ? null : { idx: i, time: times[i], diff: mins[i] - now };
  }

  // ---------- данные ----------
  let DATA = null, ROUTES = [], BY_ID = {}, GROUPS = [], STOPS = [], STOP_BY_KEY = {};
  const numKey = (n) => { const m = /^(\d+)(.*)$/.exec(n); return m ? [parseInt(m[1], 10), m[2]] : [999, n]; };
  const cmpNum = (a, b) => { const x = numKey(a), y = numKey(b); return x[0] - y[0] || x[1].localeCompare(y[1], "ru"); };
  const dist = (a, b) => {
    const R = 6371000, toR = Math.PI / 180;
    const dLat = (b[0] - a[0]) * toR, dLon = (b[1] - a[1]) * toR;
    const s = Math.sin(dLat / 2) ** 2 + Math.cos(a[0] * toR) * Math.cos(b[0] * toR) * Math.sin(dLon / 2) ** 2;
    return 2 * R * Math.asin(Math.sqrt(s));
  };
  const plural = (n, one, few, many) => { const a = n % 100, b = n % 10; return a > 10 && a < 20 ? many : b === 1 ? one : b >= 2 && b <= 4 ? few : many; };
  const short = (s) => s.replace("Административно-деловой центр", "АДЦ").replace("Торгово-бытовой комплекс", "ТБК");

  function index() {
    ROUTES = DATA.routes;
    ROUTES.forEach((r) => { BY_ID[r.id] = r; });
    const g = {};
    ROUTES.forEach((r) => { (g[r.n] = g[r.n] || []).push(r); });
    GROUPS = Object.keys(g).sort(cmpNum).map((n) => ({ n, c: g[n][0].c, dirs: g[n] }));

    // Остановки с одинаковым названием ближе 250 м — одна точка для пассажира.
    const groups = [];
    ROUTES.forEach((r) => r.stops.forEach(([id, name, lat, lon], i) => {
      let s = groups.find((x) => x.name === name && dist(x.p, [lat, lon]) < 250);
      if (!s) { s = { name, p: [lat, lon], ids: new Set(), visits: [] }; groups.push(s); }
      s.ids.add(id);
      if (!s.visits.some((v) => v.r === r && v.i === i)) s.visits.push({ r, i });
    }));
    groups.forEach((s) => {
      s.key = Math.min(...s.ids) + "";
      s.numbers = [...new Set(s.visits.map((v) => v.r.n))].sort(cmpNum);
      STOP_BY_KEY[s.key] = s;
    });
    STOPS = groups.sort((a, b) => a.name.localeCompare(b.name, "ru"));
  }

  // ---------- карта ----------
  const map = L.map("map", { zoomControl: false, attributionControl: true, preferCanvas: true }).setView(NORILSK, 12);
  L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 19, minZoom: 9, crossOrigin: true,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
  }).addTo(map);
  const routeLayer = L.layerGroup().addTo(map);
  const stopLayer = L.layerGroup().addTo(map);
  const meLayer = L.layerGroup().addTo(map);
  let me = null;

  function sheetHeightPx() { return $("#sheet").getBoundingClientRect().height; }
  function fit(bounds) {
    map.fitBounds(bounds, { paddingTopLeft: [24, 80], paddingBottomRight: [24, sheetHeightPx() + 16], maxZoom: 16 });
  }
  function dotIcon(color, selected, size = 14) {
    return L.divIcon({ className: "", html: `<div class="stop-dot${selected ? " sel" : ""}" style="--c:${color};width:${size}px;height:${size}px"></div>`, iconSize: [size, size], iconAnchor: [size / 2, size / 2] });
  }

  function drawRoute(r, focusStopIdx) {
    routeLayer.clearLayers(); stopLayer.clearLayers();
    L.polyline(r.line, { color: "#0b1630", weight: 8, opacity: .55 }).addTo(routeLayer);
    L.polyline(r.line, { color: r.c, weight: 5, opacity: 1 }).addTo(routeLayer);
    r.stops.forEach(([id, name, lat, lon], i) => {
      const end = i === 0 || i === r.stops.length - 1;
      const m = L.marker([lat, lon], { icon: dotIcon(r.c, i === focusStopIdx, end ? 18 : 13), title: name, keyboard: false }).addTo(stopLayer);
      m.on("click", () => { const s = findStop(id, lat, lon, name); if (s) go({ s: s.key }); });
    });
    if (focusStopIdx != null) {
      const s = r.stops[focusStopIdx];
      map.setView([s[2], s[3]], Math.max(map.getZoom(), 15));
    } else fit(L.latLngBounds(r.line));
  }
  function drawStop(s) {
    routeLayer.clearLayers(); stopLayer.clearLayers();
    L.marker(s.p, { icon: dotIcon("#1f6feb", true, 20), title: s.name }).addTo(stopLayer);
    map.setView(s.p, Math.max(map.getZoom(), 16));
  }
  function drawStops(list) {
    routeLayer.clearLayers(); stopLayer.clearLayers();
    list.forEach((s) => {
      L.marker(s.p, { icon: dotIcon("#1f6feb", false, 14), title: s.name }).on("click", () => go({ s: s.key })).addTo(stopLayer);
    });
  }
  function findStop(id, lat, lon, name) {
    return STOPS.find((s) => s.ids.has(id)) || STOPS.find((s) => s.name === name && dist(s.p, [lat, lon]) < 250);
  }

  // ---------- состояние / навигация (через #hash, работает «Назад» на iPhone) ----------
  let state = { tab: "routes", dayWeekend: null };
  function parseHash() {
    const p = new URLSearchParams(location.hash.slice(1));
    return { r: p.get("r"), s: p.get("s"), tab: p.get("tab") };
  }
  function go(o) {
    const p = new URLSearchParams();
    Object.entries(o).forEach(([k, v]) => v != null && p.set(k, v));
    const h = "#" + p.toString();
    if (location.hash !== h) location.hash = h; else render();
  }
  window.addEventListener("hashchange", render);

  function setSheet(mode) {
    const el = $("#sheet");
    el.classList.toggle("full", mode === "full");
    el.classList.toggle("mini", mode === "mini");
    setTimeout(() => map.invalidateSize(), 260);
  }
  $("#grip").addEventListener("click", () => {
    const el = $("#sheet");
    setSheet(el.classList.contains("full") ? "mini" : el.classList.contains("mini") ? "peek" : "full");
  });
  // жест: потянуть панель вверх/вниз за ручку
  (() => {
    let y0 = null;
    const grip = $("#grip");
    grip.addEventListener("touchstart", (e) => { y0 = e.touches[0].clientY; }, { passive: true });
    grip.addEventListener("touchend", (e) => {
      if (y0 == null) return;
      const dy = e.changedTouches[0].clientY - y0; y0 = null;
      const el = $("#sheet");
      const cur = el.classList.contains("full") ? 2 : el.classList.contains("mini") ? 0 : 1;
      if (Math.abs(dy) < 25) return;
      const next = Math.max(0, Math.min(2, cur + (dy < 0 ? 1 : -1)));
      setSheet(["mini", "peek", "full"][next]);
      e.preventDefault();
    });
  })();

  function toast(msg, ms = 3500) {
    const t = $("#toast"); t.textContent = msg; t.hidden = false;
    clearTimeout(toast._t); toast._t = setTimeout(() => { t.hidden = true; }, ms);
  }

  // ---------- экраны ----------
  const body = $("#sheet-body");

  function badge(n, c, extra = "") { return `<span class="badge" style="background:${c}">${esc(n)}${extra}</span>`; }
  function routeChips(nums) {
    return `<div class="chips">${nums.map((n) => { const g = GROUPS.find((x) => x.n === n); return badge(n, g ? g.c : "#888"); }).join("")}</div>`;
  }
  function stopRow(s, extra = "") {
    return `<button class="row" data-s="${s.key}"><span class="stopnum" style="color:#1f6feb"></span><span class="grow"><div class="title">${esc(s.name)}${isFavStop(s.key) ? " ★" : ""}</div>${extra ? `<div class="meta">${extra}</div>` : ""}${routeChips(s.numbers)}</span></button>`;
  }
  function tabs() {
    const t = (id, label) => `<button class="tab" role="tab" data-tab="${id}" aria-selected="${state.tab === id}">${label}</button>`;
    return `<div class="tabs" role="tablist">${t("routes", "Маршруты")}${t("near", "Рядом")}${t("fav", "Избранное")}</div>`;
  }

  function viewHome() {
    const q = $("#q").value.trim().toLowerCase();
    let html = tabs();
    if (q) {
      const rs = GROUPS.filter((g) => g.n.toLowerCase().startsWith(q) || g.dirs.some((r) => (r.from + " " + r.to).toLowerCase().includes(q)));
      const ss = STOPS.filter((s) => s.name.toLowerCase().includes(q)).slice(0, 40);
      html = "";
      if (rs.length) html += `<div class="section">Маршруты</div><div class="grid">${rs.map((g) => `<button class="badge" data-n="${esc(g.n)}" style="background:${g.c}">${esc(g.n)}</button>`).join("")}</div>`;
      if (ss.length) html += `<div class="section">Остановки</div><ul class="list">${ss.map((s) => `<li>${stopRow(s)}</li>`).join("")}</ul>`;
      if (!rs.length && !ss.length) html += `<div class="empty">Ничего не нашлось по запросу «${esc(q)}»</div>`;
      body.innerHTML = html;
      drawStops(ss.slice(0, 40));
      return;
    }
    if (state.tab === "routes") {
      html += `<div class="grid">${GROUPS.map((g) => `<button class="badge" data-n="${esc(g.n)}" style="background:${g.c}" aria-label="Маршрут ${esc(g.n)}">${esc(g.n)}${isFavRoute(g.n) ? '<span class="star">★</span>' : ""}</button>`).join("")}</div>`;
      html += `<p class="note">Расписание НПОПАТ от ${esc(DATA.dataDate)}. Время — норильское. Тестовая веб-версия, сборка ${esc(window.BUILD)}.</p>`;
      routeLayer.clearLayers(); stopLayer.clearLayers();
    } else if (state.tab === "near") {
      if (!me) {
        html += `<div class="empty">Нажмите <b>«Где я»</b> справа, чтобы найти ближайшие остановки.<br><br><button class="dir" id="near-locate">Определить местоположение</button></div>`;
      } else {
        const near = STOPS.map((s) => ({ s, d: dist(me, s.p) })).sort((a, b) => a.d - b.d).slice(0, 12);
        if (near[0].d > 30000) html += `<div class="empty">Вы далеко от Норильска — ближайшие остановки не показываем.</div>`;
        else html += `<ul class="list">${near.map(({ s, d }) => `<li>${stopRow(s, d < 1000 ? Math.round(d / 10) * 10 + " м" : (d / 1000).toFixed(1) + " км")}</li>`).join("")}</ul>`;
        if (near[0].d <= 30000) drawStops(near.map((x) => x.s));
      }
    } else {
      const fr = GROUPS.filter((g) => isFavRoute(g.n));
      const fs = favs.stops.map((k) => STOP_BY_KEY[k]).filter(Boolean);
      if (!fr.length && !fs.length) html += `<div class="empty">Здесь появятся маршруты и остановки, отмеченные звёздочкой ☆.</div>`;
      if (fr.length) html += `<div class="section">Маршруты</div><div class="grid">${fr.map((g) => `<button class="badge" data-n="${esc(g.n)}" style="background:${g.c}">${esc(g.n)}</button>`).join("")}</div>`;
      if (fs.length) html += `<div class="section">Остановки</div><ul class="list">${fs.map((s) => `<li>${stopRow(s)}</li>`).join("")}</ul>`;
      routeLayer.clearLayers(); stopLayer.clearLayers();
    }
    body.innerHTML = html;
    const nl = $("#near-locate"); if (nl) nl.onclick = locate;
  }

  function timetableHtml(r, weekend, now, focusStopIdx) {
    if (!r.sch) return `<div class="note">Для этого направления нет официального расписания.</div>`;
    let html = `<div class="seg" role="group" aria-label="Тип дня"><button data-day="wd" aria-pressed="${!weekend}">Будни</button><button data-day="we" aria-pressed="${weekend}">Выходные</button></div>`;
    const today = weekend === now.weekend;
    const order = r.sch.map((t, i) => i);
    if (r.dep != null) { order.splice(order.indexOf(r.dep), 1); order.unshift(r.dep); }
    order.map((i) => r.sch[i]).forEach((t, k) => {
      const times = weekend ? t.we : t.wd;
      const nx = today ? nextFrom(times, now.min) : null;
      html += `<div class="section">Отправление: ${esc(short(t.t))}${k === 0 && r.dep != null ? " · начало этого направления" : ""}</div>`;
      if (!times.length) { html += `<div class="note">Рейсов нет.</div>`; return; }
      if (today) {
        html += nx
          ? `<div class="next"><span>Ближайший</span><b>${esc(nx.time)}</b><span class="in">${inText(nx.diff)}</span></div>`
          : `<div class="next"><span>Сегодня рейсов больше нет. Первый завтра —</span><b>${esc(((now.wd.startsWith("пт") || now.wd.startsWith("сб")) ? t.we : t.wd)[0] || "—")}</b></div>`;
      }
      html += `<div class="times">${times.map((x, i) => `<span class="${today && nx ? (i < nx.idx ? "past" : i === nx.idx ? "soon" : "") : (today && !nx ? "past" : "")}">${esc(x)}</span>`).join("")}</div>`;
    });
    html += `<p class="note">Время отправления с конечной${focusStopIdx ? "; до выбранной остановки автобус идёт ещё какое-то время" : ""}. Праздничные дни не учитываются.${r.src ? ` <a href="${esc(r.src)}" target="_blank" rel="noopener">Источник</a>` : ""}</p>`;
    return html;
  }

  function viewRoute(id, focusStopIdx) {
    const r = BY_ID[id];
    if (!r) return go({});
    const g = GROUPS.find((x) => x.n === r.n);
    const now = nowNorilsk();
    const weekend = state.dayWeekend ?? now.weekend;
    let html = `<div class="head"><button class="back" data-back aria-label="Назад">‹</button>${badge(r.n, r.c)}<div class="grow"><h2>Маршрут ${esc(r.n)}</h2><div class="sub">${g.dirs.length} ${plural(g.dirs.length, "направление", "направления", "направлений")}</div></div><button class="fav" data-fav-route="${esc(r.n)}" aria-pressed="${isFavRoute(r.n)}" aria-label="В избранное">${isFavRoute(r.n) ? "★" : "☆"}</button></div>`;
    html += `<div class="dirs">${g.dirs.map((d) => `<button class="dir" data-r="${d.id}" aria-pressed="${d.id === r.id}">${esc(short(d.from))} → ${esc(short(d.to))}</button>`).join("")}</div>`;
    html += timetableHtml(r, weekend, now, focusStopIdx);
    html += `<div class="section">Остановки (${r.stops.length})</div><ul class="list">${r.stops.map(([sid, name, lat, lon], i) => `<li><button class="row" data-route-stop="${i}"><span class="stopnum" style="color:${r.c}"></span><span class="grow"><div class="title">${esc(name)}</div></span></button></li>`).join("")}</ul>`;
    body.innerHTML = html;
    drawRoute(r, focusStopIdx);
    body.querySelectorAll("[data-route-stop]").forEach((b) => {
      b.onclick = () => { const i = +b.dataset.routeStop; const s = r.stops[i]; const st = findStop(s[0], s[2], s[3], s[1]); if (st) go({ s: st.key }); };
    });
  }

  function viewStop(key) {
    const s = STOP_BY_KEY[key];
    if (!s) return go({});
    const now = nowNorilsk();
    let html = `<div class="head"><button class="back" data-back aria-label="Назад">‹</button><div class="grow"><h2>${esc(s.name)}</h2><div class="sub">Остановка · ${s.numbers.length} ${plural(s.numbers.length, "маршрут", "маршрута", "маршрутов")}</div></div><button class="fav" data-fav-stop="${s.key}" aria-pressed="${isFavStop(s.key)}" aria-label="В избранное">${isFavStop(s.key) ? "★" : "☆"}</button></div>`;
    const visits = s.visits.slice().sort((a, b) => cmpNum(a.r.n, b.r.n));
    html += `<ul class="list">${visits.map(({ r, i }) => {
      let meta = "Нет официального расписания";
      if (r.sch && r.dep == null) meta = "Время отправления — в расписании маршрута";
      else if (r.sch) {
        const t = r.sch[r.dep];
        const nx = nextFrom(now.weekend ? t.we : t.wd, now.min);
        meta = nx ? `С конечной «${esc(short(t.t))}»: ${esc(nx.time)}, ${inText(nx.diff)}` : `Сегодня с конечной «${esc(short(t.t))}» рейсов больше нет`;
      }
      const last = i === r.stops.length - 1;
      return `<li><button class="row" data-r="${r.id}" data-i="${i}">${badge(r.n, r.c)}<span class="grow"><div class="title">${last ? "Конечная · " : ""}${esc(short(r.from))} → ${esc(short(r.to))}</div><div class="meta">${meta}</div></span></button></li>`;
    }).join("")}</ul>`;
    html += `<p class="note">Показано отправление с начальной конечной; живых данных GPS об автобусах пока нет.</p>`;
    body.innerHTML = html;
    drawStop(s);
  }

  function render() {
    const h = parseHash();
    if (h.tab) state.tab = h.tab;
    if (h.r) { const [id, i] = h.r.split("@"); viewRoute(id, i != null ? +i : null); }
    else if (h.s) viewStop(h.s);
    else viewHome();
    body.scrollTop = 0;
  }

  // ---------- события ----------
  body.addEventListener("click", (e) => {
    const t = e.target.closest("button"); if (!t) return;
    if (t.dataset.tab) { state.tab = t.dataset.tab; go({ tab: state.tab }); }
    else if (t.dataset.n) { const g = GROUPS.find((x) => x.n === t.dataset.n); $("#q").value = ""; syncClear(); state.dayWeekend = null; go({ r: g.dirs[0].id }); }
    else if (t.dataset.r) { const r = BY_ID[t.dataset.r]; const p = parseHash(); go({ r: t.dataset.i != null ? `${r.id}@${t.dataset.i}` : r.id }); if (p.s) setSheet("peek"); }
    else if (t.dataset.s) { $("#q").blur(); go({ s: t.dataset.s }); }
    else if (t.dataset.day) { state.dayWeekend = t.dataset.day === "we"; render(); }
    else if (t.dataset.favRoute) { const on = toggleFav("routes", t.dataset.favRoute); toast(on ? "Маршрут добавлен в избранное" : "Убран из избранного"); render(); }
    else if (t.dataset.favStop) { const on = toggleFav("stops", t.dataset.favStop); toast(on ? "Остановка добавлена в избранное" : "Убрана из избранного"); render(); }
    else if (t.hasAttribute("data-back")) { if (history.length > 1) history.back(); else go({}); }
  });

  const q = $("#q");
  function syncClear() { $("#qclear").hidden = !q.value; }
  q.addEventListener("input", () => { syncClear(); if (location.hash && location.hash !== "#" && !location.hash.startsWith("#tab")) history.replaceState(null, "", "#"); viewHome(); });
  q.addEventListener("focus", () => setSheet("full"));
  $("#qclear").onclick = () => { q.value = ""; syncClear(); viewHome(); q.focus(); };

  function locate() {
    if (!("geolocation" in navigator)) return toast("Браузер не умеет определять местоположение");
    const fab = $("#locate"); fab.classList.add("busy");
    navigator.geolocation.getCurrentPosition((pos) => {
      fab.classList.remove("busy");
      me = [pos.coords.latitude, pos.coords.longitude];
      meLayer.clearLayers();
      L.circle(me, { radius: pos.coords.accuracy, color: "#1f6feb", weight: 1, fillOpacity: .08 }).addTo(meLayer);
      L.marker(me, { icon: L.divIcon({ className: "", html: '<div class="me-dot" style="width:18px;height:18px"></div>', iconSize: [18, 18], iconAnchor: [9, 9] }), interactive: false }).addTo(meLayer);
      state.tab = "near"; setSheet("peek");
      if (dist(me, NORILSK) > 60000) toast("Вы далеко от Норильска");
      else map.setView(me, 15);
      go({ tab: "near" });
    }, (err) => {
      fab.classList.remove("busy");
      toast(err.code === 1 ? "Нет доступа к геолокации. Разрешите её в Настройки → Конфиденциальность → Службы геолокации → Safari" : "Не удалось определить местоположение", 6000);
    }, { enableHighAccuracy: true, timeout: 15000, maximumAge: 30000 });
  }
  $("#locate").onclick = locate;

  function tick() {
    const n = nowNorilsk();
    $("#clock").innerHTML = `${n.hhmm}<small>Норильск</small>`;
  }
  tick(); setInterval(() => { const before = $("#clock").textContent; tick(); if ($("#clock").textContent !== before && (parseHash().r || parseHash().s)) render(); }, 15000);

  // ---------- старт ----------
  body.innerHTML = `<div class="empty">Загружаем маршруты…</div>`;
  fetch("data.json").then((r) => { if (!r.ok) throw new Error(r.status); return r.json(); }).then((d) => {
    DATA = d; index(); render();
  }).catch(() => {
    body.innerHTML = `<div class="empty">Не удалось загрузить маршруты. Проверьте интернет и обновите страницу.</div>`;
  });

  if ("serviceWorker" in navigator && location.protocol === "https:") {
    navigator.serviceWorker.register("sw.js").catch(() => {});
  }
})();
