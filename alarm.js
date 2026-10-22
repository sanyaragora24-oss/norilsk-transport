/* Будильник «скоро ваша остановка».
 * Логика стадий — как в Android (StopAlarmProximityTracker / StopAlarmPassageDetector):
 * «скоро» за 350 м (с учётом точности GPS), «выходите» в 40 м или при проезде мимо.
 *
 * Ограничение iPhone: веб-приложение получает GPS, только пока оно открыто на экране.
 * Поэтому экран удерживается включённым (Wake Lock), а сигнал — звук, голос и уведомление.
 */
(() => {
  "use strict";
  const KEY = "nt.alarm.v1";
  const APPROACH_M = 350, ARRIVAL_M = 40, MAX_ACC = 200, MAX_ARRIVAL_ACC = 75, MAX_MS = 4 * 3600 * 1000;

  function distM(a, b) {
    const R = 6371000, r = Math.PI / 180;
    const dLat = (b[0] - a[0]) * r, dLon = (b[1] - a[1]) * r;
    const s = Math.sin(dLat / 2) ** 2 + Math.cos(a[0] * r) * Math.cos(b[0] * r) * Math.sin(dLon / 2) ** 2;
    return 2 * R * Math.asin(Math.sqrt(s));
  }

  /** Проехали остановку между двумя точными замерами (быстрый автобус). */
  function passed(prev, cur, target) {
    if (!prev || prev.acc > 75 || cur.acc > 75) return false;
    const dt = cur.t - prev.t;
    if (dt < 1 || dt > 15000) return false;
    const k = 111320, kx = k * Math.cos(target[0] * Math.PI / 180);
    const bx = (prev.p[1] - target[1]) * kx, by = (prev.p[0] - target[0]) * k;
    const cx = (cur.p[1] - target[1]) * kx, cy = (cur.p[0] - target[0]) * k;
    const dx = cx - bx, dy = cy - by, len = Math.hypot(dx, dy);
    if (len < 1 || len / (dt / 1000) > 60) return false;
    const proj = -(bx * dx + by * dy) / (len * len);
    if (proj < 0 || proj > 1) return false;
    return Math.hypot(bx + proj * dx, by + proj * dy) <= ARRIVAL_M;
  }

  let audio = null, watchId = null, wakeLock = null, state = null, prevFix = null, listeners = [];

  const emit = () => listeners.forEach((f) => f(state));
  const save = () => { try { state ? localStorage.setItem(KEY, JSON.stringify(state)) : localStorage.removeItem(KEY); } catch (e) { /* ок */ } };

  function unlockSound() {
    try {
      audio = audio || new (window.AudioContext || window.webkitAudioContext)();
      if (audio.state === "suspended") audio.resume();
      const o = audio.createOscillator(), g = audio.createGain(); g.gain.value = 0.0001;
      o.connect(g).connect(audio.destination); o.start(); o.stop(audio.currentTime + 0.05);
    } catch (e) { /* без звука */ }
    try { if ("speechSynthesis" in window) { const u = new SpeechSynthesisUtterance(" "); u.volume = 0; speechSynthesis.speak(u); } } catch (e) { /* ок */ }
  }
  function beep(times) {
    if (!audio) return;
    for (let i = 0; i < times; i++) {
      const t = audio.currentTime + i * 0.45;
      const o = audio.createOscillator(), g = audio.createGain();
      o.type = "square"; o.frequency.value = i % 2 ? 1040 : 880;
      g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.35, t + 0.02); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.35);
      o.connect(g).connect(audio.destination); o.start(t); o.stop(t + 0.4);
    }
  }
  function say(text, force) {
    if (!("speechSynthesis" in window) || (!force && (!state || !state.voice))) return;
    try {
      speechSynthesis.cancel();
      const u = new SpeechSynthesisUtterance(text); u.lang = "ru-RU"; u.rate = 1;
      const v = speechSynthesis.getVoices().find((x) => x.lang && x.lang.startsWith("ru")); if (v) u.voice = v;
      speechSynthesis.speak(u);
    } catch (e) { /* ок */ }
  }
  async function notify(title, body) {
    try {
      if (!("Notification" in window) || Notification.permission !== "granted") return;
      const reg = await navigator.serviceWorker?.getRegistration();
      if (reg) reg.showNotification(title, { body, tag: "stop-alarm", renotify: true, icon: "icons/icon-192.png" });
    } catch (e) { /* ок */ }
  }

  async function keepAwake() {
    try { if ("wakeLock" in navigator && document.visibilityState === "visible") wakeLock = await navigator.wakeLock.request("screen"); } catch (e) { wakeLock = null; }
  }
  document.addEventListener("visibilitychange", () => { if (state && document.visibilityState === "visible") keepAwake(); });

  function fire(stage) {
    if (stage === "approach") {
      beep(4); say(`Скоро ваша остановка: ${state.name}. Приготовьтесь к выходу.`);
      notify("Скоро ваша остановка", `${state.name} — меньше ${APPROACH_M} м. Приготовьтесь к выходу.`);
    } else if (stage === "arrival") {
      beep(8); say(`Выходите. Остановка ${state.name}.`);
      notify("Выходите!", `Остановка «${state.name}».`);
    }
  }

  function onFix(pos) {
    if (!state) return;
    const p = [pos.coords.latitude, pos.coords.longitude], acc = pos.coords.accuracy, t = Date.now();
    const d = distM(p, state.p);
    const cur = { p, acc, t };
    const crossed = passed(prevFix, cur, state.p);
    prevFix = cur;
    state.distance = Math.round(d); state.accuracy = Math.round(acc); state.lastFixAt = t;
    if (acc <= MAX_ACC) {
      if (!state.approach && Math.max(0, d - acc) <= APPROACH_M) { state.approach = true; fire("approach"); }
      if (!state.arrival && acc <= MAX_ARRIVAL_ACC && (d <= ARRIVAL_M || crossed)) {
        if (!state.approach) { state.approach = true; }
        state.arrival = true; fire("arrival");
      }
    }
    if (t - state.startedAt > MAX_MS && !state.arrival) { state.timeout = true; stop(); return; }
    save(); emit();
  }
  function onErr(err) {
    if (!state) return;
    state.error = err.code === 1 ? "Нет доступа к геолокации" : "GPS пока не отвечает";
    emit();
  }
  function startWatch() {
    if (watchId != null || !("geolocation" in navigator)) return;
    watchId = navigator.geolocation.watchPosition(onFix, onErr, { enableHighAccuracy: true, maximumAge: 3000, timeout: 30000 });
    keepAwake();
  }

  async function start(stop, opts) {
    unlockSound(); // вызывается по нажатию — iPhone разрешает звук и голос только так
    try { if ("Notification" in window && Notification.permission === "default") await Notification.requestPermission(); } catch (e) { /* ок */ }
    state = { key: stop.key, name: stop.name, p: stop.p, startedAt: Date.now(), approach: false, arrival: false, voice: opts.voice !== false };
    prevFix = null; save(); startWatch(); emit();
  }
  function stop() {
    if (watchId != null) navigator.geolocation.clearWatch(watchId);
    watchId = null; prevFix = null;
    try { wakeLock && wakeLock.release(); } catch (e) { /* ок */ }
    wakeLock = null;
    const was = state; state = null; save(); emit(); return was;
  }
  function restore() {
    try { const s = JSON.parse(localStorage.getItem(KEY)); if (s && !s.arrival && Date.now() - s.startedAt < MAX_MS) { state = s; startWatch(); emit(); } else if (s) localStorage.removeItem(KEY); } catch (e) { /* ок */ }
  }
  const test = () => { unlockSound(); beep(3); say("Проверка звука. Будильник будет говорить так.", true); };

  window.NTAlarm = { start, stop, restore, test, unlock: unlockSound, get state() { return state; }, onChange: (f) => listeners.push(f), APPROACH_M, ARRIVAL_M };
})();
