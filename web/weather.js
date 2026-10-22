/* Погода Норильска: Open-Meteo (как в Android), предупреждения и оценка дорог.
 * Пороги и тексты перенесены из android/.../data/network/WeatherAlerts.kt и RoadConditions.kt.
 */
(() => {
  "use strict";
  const LAT = 69.3498, LON = 88.2005;
  const URL = "https://api.open-meteo.com/v1/forecast?latitude=" + LAT + "&longitude=" + LON +
    "&current=temperature_2m,apparent_temperature,wind_speed_10m,wind_gusts_10m,weather_code,visibility" +
    "&hourly=temperature_2m,apparent_temperature,wind_speed_10m,wind_gusts_10m,weather_code,visibility" +
    "&forecast_hours=12&wind_speed_unit=ms&timezone=auto";
  const CACHE_KEY = "nt.weather.v1";
  const MAX_AGE = 15 * 60 * 1000;

  function codeText(c) {
    if (c === 0) return "Ясно";
    if (c === 1) return "Преимущественно ясно";
    if (c === 2) return "Переменная облачность";
    if (c === 3) return "Пасмурно";
    if (c === 45 || c === 48) return "Туман";
    if ([51, 53, 55].includes(c)) return "Морось";
    if ([56, 57].includes(c)) return "Ледяная морось";
    if ([61, 63, 65].includes(c)) return "Дождь";
    if ([66, 67].includes(c)) return "Ледяной дождь";
    if ([71, 73, 75].includes(c)) return "Снег";
    if (c === 77) return "Снежная крупа";
    if ([80, 81, 82].includes(c)) return "Ливень";
    if ([85, 86].includes(c)) return "Снегопад";
    if (c === 95) return "Гроза";
    if (c === 96 || c === 99) return "Гроза с градом";
    return "—";
  }
  function codeIcon(c) {
    if (c === 0 || c === 1) return "☀️";
    if (c === 2) return "⛅";
    if (c === 3) return "☁️";
    if (c === 45 || c === 48) return "🌫️";
    if ([71, 73, 75, 77, 85, 86].includes(c)) return "🌨️";
    if ([51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82].includes(c)) return "🌧️";
    if ([95, 96, 99].includes(c)) return "⛈️";
    return "🌡️";
  }

  const has = (d, words) => words.some((w) => d.includes(w));
  const hasSnow = (d) => has(d, ["снег", "метел", "пург", "крупа"]);
  const hasRain = (d) => has(d, ["дожд", "морос", "ливень"]);
  const hasFog = (d) => has(d, ["туман", "дымк", "мгла"]);
  const hasFreezing = (d) => has(d, ["ледян", "гололёд", "гололед", "изморозь"]);
  const hoursText = (h) => h % 10 === 1 && h % 100 !== 11 ? h + " час" : (h % 10 >= 2 && h % 10 <= 4 && !(h % 100 >= 12 && h % 100 <= 14)) ? h + " часа" : h + " часов";
  const cap = (s) => s.charAt(0).toUpperCase() + s.slice(1);
  const LEVEL = { ADVISORY: 0, WARNING: 1, SEVERE: 2 };

  function alertsFor(c, hoursAhead) {
    const out = [];
    const desc = c.description.toLowerCase();
    const snow = hasSnow(desc);
    const w = c.wind || 0, g = c.gusts || 0, peak = Math.max(w, g);
    const when = hoursAhead === 0 ? "сейчас" : "через " + hoursText(hoursAhead);
    const windText = g > w ? `порывы до ${Math.trunc(g)} м/с` : `ветер ${Math.trunc(w)} м/с`;
    const vis = c.visibility;
    const lowVis = vis != null && vis <= 500;
    const A = (kind, level, title, message, advice) => out.push({ kind, level, title, message, advice, hoursAhead, id: `${kind}_${level}_${hoursAhead === 0 ? "now" : "in" + hoursAhead}` });

    if (snow && (peak >= 15 || (peak >= 11 && lowVis))) {
      const severe = peak >= 25 || (peak >= 15 && lowVis);
      if (severe) A("BLIZZARD", "SEVERE", hoursAhead === 0 ? "Пурга" : "Ожидается пурга",
        `Метель, ${windText}${vis != null ? `, видимость около ${Math.trunc(vis)} м` : ""}, ${when}.`,
        "Автодороги между районами (Талнах, Кайеркан, Алыкель) в такую погоду могут закрыть, междугородние рейсы — отменить. Проверьте сообщения перед выходом и по возможности отложите поездку.");
      else A("BLIZZARD", "WARNING", hoursAhead === 0 ? "Метель" : "Ожидается метель",
        `Снег, ${windText} ${when}.`, "Автобусы могут идти с опозданием. Заложите на дорогу больше времени и ждите транспорт в укрытии.");
    } else if (peak >= 25 || g >= 30) {
      A("WIND", "SEVERE", hoursAhead === 0 ? "Очень сильный ветер" : "Ожидается очень сильный ветер", `${cap(windText)} ${when}.`,
        "Опасно находиться у зданий и рекламных конструкций. Движение транспорта может быть ограничено.");
    } else if (peak >= 15 || g >= 20) {
      A("WIND", "WARNING", hoursAhead === 0 ? "Сильный ветер" : "Ожидается сильный ветер", `${cap(windText)} ${when}.`,
        "На открытых участках идти тяжело. Ждите автобус в укрытии.");
    } else if (peak >= 8 && hoursAhead === 0) {
      A("WIND", "ADVISORY", "Усиление ветра", `${cap(windText)}.`, "На остановке будет ощутимо холоднее, чем показывает градусник.");
    }

    const feels = c.feelsLike != null ? c.feelsLike : c.temp;
    if (feels != null) {
      if (feels <= -45) A("FROST", "SEVERE", hoursAhead === 0 ? "Аномальный мороз" : "Ожидается аномальный мороз", `Ощущается как ${Math.trunc(feels)} °C ${when}.`,
        "Открытая кожа обмораживается за считанные минуты. Без крайней необходимости не выходите; ждите транспорт только в помещении.");
      else if (feels <= -35) A("FROST", "WARNING", hoursAhead === 0 ? "Сильный мороз" : "Ожидается сильный мороз", `Ощущается как ${Math.trunc(feels)} °C ${when}.`,
        "Закройте лицо, не ждите автобус на открытом месте дольше 10 минут.");
      else if (feels <= -25 && hoursAhead === 0) A("FROST", "ADVISORY", "Мороз", `Ощущается как ${Math.trunc(feels)} °C.`, "Оденьтесь теплее — на остановке придётся стоять на улице.");
    }

    const nearZero = c.temp != null && c.temp >= -6 && c.temp <= 2;
    if (hasFreezing(desc) || (nearZero && (snow || hasRain(desc))))
      A("ICE", "WARNING", hoursAhead === 0 ? "Гололёд" : "Ожидается гололёд", `Температура около нуля с осадками ${when}.`,
        "Скользко на тротуарах и у остановок. Держитесь за поручни в салоне.");

    if (vis != null && !snow) {
      if (vis <= 500) A("VISIBILITY", "WARNING", hoursAhead === 0 ? "Очень плохая видимость" : "Ожидается плохая видимость", `Видимость около ${Math.trunc(vis)} м ${when}.`,
        "Водитель может не заметить вас на остановке — стойте на виду, по возможности с включённым фонариком телефона.");
      else if (vis <= 1000 && hasFog(desc)) A("VISIBILITY", "ADVISORY", "Туман", `Видимость около ${Math.trunc(vis)} м ${when}.`, "Транспорт идёт медленнее обычного, возможны опоздания.");
    }
    return out;
  }

  function evaluateAlerts(wx, maxHours = 6) {
    const now = alertsFor(wx, 0);
    const worstNow = Math.max(-1, ...now.map((a) => LEVEL[a.level]));
    const forecast = wx.hourly.filter((h) => h.hoursFromNow >= 1 && h.hoursFromNow <= maxHours)
      .flatMap((h) => alertsFor(h, h.hoursFromNow)).filter((a) => LEVEL[a.level] > worstNow);
    const best = {};
    [...now, ...forecast].forEach((a) => {
      const cur = best[a.kind];
      if (!cur || LEVEL[a.level] > LEVEL[cur.level] || (LEVEL[a.level] === LEVEL[cur.level] && a.hoursAhead < cur.hoursAhead)) best[a.kind] = a;
    });
    return Object.values(best).sort((a, b) => LEVEL[b.level] - LEVEL[a.level] || a.hoursAhead - b.hoursAhead);
  }

  function roadConditions(wx) {
    const t = wx.temp, feels = wx.feelsLike, wind = wx.wind, d = wx.description.toLowerCase();
    const snow = has(d, ["снег", "метел", "пург"]), rain = hasRain(d), fog = hasFog(d), freezing = hasFreezing(d);
    const f = []; let sev = 0;
    if ((t >= -6 && t <= 2 && (snow || rain)) || freezing) { f.push("❄️ Возможен гололёд (температура около нуля)"); sev = Math.max(sev, 2); }
    if (wind >= 11 && (snow || t < -2)) { f.push(`🌬️ Метель и снежные заносы (ветер ${Math.trunc(wind)} м/с)`); sev = Math.max(sev, 2); }
    else if (wind >= 8) { f.push(`💨 Усиление ветра до ${Math.trunc(wind)} м/с`); sev = Math.max(sev, 1); }
    if (feels <= -35) { f.push(`🥶 Сильный мороз: ощущается ${Math.trunc(feels)}°C`); sev = Math.max(sev, 1); }
    if (fog || (snow && wind >= 11)) { f.push("🌫️ Ограниченная видимость"); sev = Math.max(sev, 1); }
    if (snow && wind < 11 && sev === 0) { f.push("🌨️ Снег — дорога может быть скользкой"); sev = 1; }
    if (!f.length) f.push("✅ Существенных рисков по погоде не выявлено");
    const [title, rec] = sev === 2 ? ["Опасные дорожные условия", "Соблюдайте дистанцию, снизьте скорость, избегайте резких манёвров. Закладывайте время на дорогу."]
      : sev === 1 ? ["Будьте внимательны на дороге", "Возможна скользкость. Двигайтесь аккуратно, увеличьте дистанцию."]
        : ["Дорожные условия в норме", "Существенных погодных рисков нет. Хорошей дороги!"];
    return { severity: sev, title, factors: f, recommendation: rec };
  }

  function parse(j) {
    const c = j.current, h = j.hourly || {};
    const times = h.time || [];
    let start = times.findIndex((x) => x >= c.time);
    if (start < 0) start = 0;
    const hourly = [];
    for (let i = start + 1; i < times.length; i++) {
      const code = h.weather_code ? h.weather_code[i] : null;
      hourly.push({
        hoursFromNow: i - start, time: times[i].slice(11, 16),
        temp: h.temperature_2m?.[i], feelsLike: h.apparent_temperature?.[i], wind: h.wind_speed_10m?.[i], gusts: h.wind_gusts_10m?.[i],
        visibility: h.visibility?.[i], code, description: code == null ? "—" : codeText(code)
      });
    }
    return {
      temp: c.temperature_2m, feelsLike: c.apparent_temperature, wind: c.wind_speed_10m, gusts: c.wind_gusts_10m,
      visibility: c.visibility, code: c.weather_code, description: codeText(c.weather_code), icon: codeIcon(c.weather_code),
      hourly, fetchedAt: Date.now()
    };
  }

  function cached() {
    try { const v = JSON.parse(localStorage.getItem(CACHE_KEY)); return v && v.temp != null ? v : null; } catch (e) { return null; }
  }

  async function load(force) {
    const c = cached();
    if (!force && c && Date.now() - c.fetchedAt < MAX_AGE) return c;
    try {
      let r = null, lastErr = null;
      for (let attempt = 0; attempt < 2 && !r; attempt++) {
        const ctl = new AbortController();
        const timer = setTimeout(() => ctl.abort(), 12000);
        try {
          const res = await fetch(URL, { cache: "no-store", signal: ctl.signal });
          if (!res.ok) throw new Error("HTTP " + res.status);
          r = res;
        } catch (e) { lastErr = e; } finally { clearTimeout(timer); }
      }
      if (!r) throw lastErr || new Error("weather");
      const wx = parse(await r.json());
      try { localStorage.setItem(CACHE_KEY, JSON.stringify(wx)); } catch (e) { /* ок */ }
      return wx;
    } catch (e) {
      if (c) return Object.assign({}, c, { stale: true });
      throw e;
    }
  }

  window.NTWeather = { load, cached, evaluateAlerts, roadConditions, codeIcon, LEVEL };
})();
