/**
 * УЗЕЛОК-БОТ — второй мозг в Telegram.
 *
 * Живёт внутри Google Таблицы (Расширения → Apps Script).
 * Сервера не надо, платить за хостинг не надо, VPN не надо.
 *
 * Что делает:
 *   • ловит из Telegram текст, голосовые, фото и файлы;
 *   • расшифровывает голос, читает фото словами;
 *   • раскладывает по шести листам этой же таблицы;
 *   • отвечает на вопросы вроде «что мне нужно сделать на этой неделе»;
 *   • около 8:30 сам присылает, что сегодня и что горит.
 *
 * Порядок настройки — в docs/nastroyka.md. Коротко:
 *   1. подставить три значения ниже;
 *   2. Развернуть → Новое развёртывание → Веб-приложение;
 *   3. запустить функцию Настроить();
 *   4. запустить функцию Проверка().
 */

/* ══════════════════════════════════════════════════════════════════
   1. ТРИ ЗНАЧЕНИЯ, КОТОРЫЕ НАДО ПОДСТАВИТЬ
   Больше в этом файле менять нечего.
   ══════════════════════════════════════════════════════════════════ */

/** Токен от @BotFather, вида 7712345678:AAF... */
var BOT_TOKEN = 'СЮДА_ТОКЕН_ОТ_BOTFATHER';

/** Ключ от ProxyAPI (или другого шлюза), вида sk-... */
var API_KEY = 'СЮДА_КЛЮЧ_ОТ_PROXYAPI';

/** Твой Telegram ID, только цифры. Не знаешь — напиши боту /id, он ответит. */
var MY_CHAT_ID = 0;


/* ══════════════════════════════════════════════════════════════════
   2. НАСТРОЙКИ, КОТОРЫЕ ОБЫЧНО НЕ ТРОГАЮТ
   ══════════════════════════════════════════════════════════════════ */

/** Адрес шлюза. AITunnel: https://api.aitunnel.ru/v1 ; Polza AI: https://api.polza.ai/api/v1 */
var API_BASE = 'https://api.proxyapi.ru/openai/v1';

/** Модель-«мозг». Если шлюз её не отдаёт — посмотри список моделей в его кабинете. */
var MODEL_TEXT = 'gpt-4.1-mini';

/** Модель расшифровки голосовых. */
var MODEL_VOICE = 'whisper-1';

/** Во сколько присылать утреннюю сводку (Google фиксирует час, минуты — примерно). */
var MORNING_HOUR = 8;
var MORNING_MINUTE = 30;

/** Часовой пояс для дат. Норильск — Asia/Krasnoyarsk. */
var TZ = 'Asia/Krasnoyarsk';

/** Папка на Google Диске, куда складываются присланные фото и файлы. */
var DRIVE_FOLDER = 'Узелок — файлы';

/** Сколько дней вперёд считать «горит на этой неделе». */
var HORIZON_DAYS = 7;


/* ══════════════════════════════════════════════════════════════════
   3. ШЕСТЬ ЯЩИКОВ
   Названия листов и колонок должны совпадать с промптом буква в букву.
   Меняешь тут — поменяй и в PROMPT ниже.
   ══════════════════════════════════════════════════════════════════ */

var SHEETS = [
  { name: 'Дела',          cols: ['дата_создания', 'текст', 'срок', 'статус', 'источник'] },
  { name: 'Документы',     cols: ['дата', 'тип', 'описание', 'сумма', 'ссылка_на_файл'] },
  { name: 'Покупки',       cols: ['что', 'где', 'примерная_цена', 'куплено'] },
  { name: 'Идеи',          cols: ['дата', 'идея', 'тема'] },
  { name: 'Напоминания',   cols: ['когда', 'текст', 'повтор', 'отправлено'] },
  { name: 'Личная память', cols: ['факт', 'категория', 'дата_обновления'] }
];

var PROMPT = [
  'Ты — второй мозг своего человека. Он присылает тебе всё подряд: голосовые на ходу,',
  'фото чеков и документов, обрывки мыслей. Твоя работа — разобрать это и вернуть в нужный момент.',
  '',
  'ПРИ КАЖДОМ ВХОДЯЩЕМ:',
  '1. Определи, к чему это относится, и запиши ровно в один раздел:',
  '   • Дела — то, что надо сделать. Всегда пытайся вытащить срок.',
  '   • Документы — фото и файлы: чеки, договоры, полисы, справки.',
  '   • Покупки — что купить, где, за сколько.',
  '   • Идеи — мысли без срока, к которым он захочет вернуться.',
  '   • Напоминания — привязанное к конкретной дате и времени.',
  '   • Личная память — устойчивые факты о нём, людях и привычках.',
  '2. Одно сообщение может дать несколько записей. Дроби.',
  '3. Даты приводи к формату ГГГГ-ММ-ДД. «На следующей неделе» считай от сегодня.',
  '4. Не выдумывай срок, которого не было. Пустое поле лучше вымысла.',
  '5. Для фото: сначала опиши, что на нём и какие там цифры, даты, названия — потом решай раздел.',
  '',
  'ОТВЕЧАЙ КОРОТКО. Одна строка: что записал, куда, с каким сроком.',
  'Если непонятно, к чему относить — задай ровно один уточняющий вопрос, не больше.',
  '',
  'КОГДА ОН СПРАШИВАЕТ:',
  'Сначала прочитай Личную память, потом нужные разделы. Отвечай списком, самое срочное сверху,',
  'с датами. Не пересказывай всё подряд — только то, о чём спросили.',
  '',
  'ТОН: спокойный, без восторгов, без эмодзи. Как хороший помощник, который давно с ним работает.',
  '',
  'ВЕРНИ СТРОГО JSON, без пояснений и без markdown-обёртки:',
  '{"ответ": "строка для Telegram", "записи": [{"раздел": "...", "поля": {"...": "..."}}]}',
  'Для вопроса "записи" — пустой массив.',
  '',
  'ДОПУСТИМЫЕ РАЗДЕЛЫ И ПОЛЯ (другие не выдумывай):',
  SHEETS.map(function (s) { return '  ' + s.name + ': ' + s.cols.join(', '); }).join('\n')
].join('\n');


/* ══════════════════════════════════════════════════════════════════
   4. НАСТРОЙКА — запускается один раз руками
   ══════════════════════════════════════════════════════════════════ */

/**
 * Создаёт шесть листов, вешает утренний будильник и говорит Telegram,
 * куда присылать сообщения. Запускать ПОСЛЕ развёртывания веб-приложения.
 */
function Настроить() {
  var log = [];

  // 4.1 Листы
  var ss = SpreadsheetApp.getActive();
  SHEETS.forEach(function (spec) {
    var sh = ss.getSheetByName(spec.name);
    if (!sh) {
      sh = ss.insertSheet(spec.name);
      log.push('создан лист «' + spec.name + '»');
    }
    var first = sh.getRange(1, 1, 1, spec.cols.length);
    var now = first.getValues()[0].join('');
    if (now !== spec.cols.join('')) {
      first.setValues([spec.cols]).setFontWeight('bold');
      sh.setFrozenRows(1);
      log.push('заголовки листа «' + spec.name + '» приведены к образцу');
    }
    sh.autoResizeColumns(1, spec.cols.length);
  });

  // Убираем пустой «Лист1», который Google создаёт вместе с таблицей.
  var lishniy = ss.getSheetByName('Лист1') || ss.getSheetByName('Sheet1');
  if (lishniy && lishniy.getLastRow() === 0 && ss.getSheets().length > 1) {
    ss.deleteSheet(lishniy);
    log.push('удалён пустой Лист1');
  }

  // 4.2 Утренний будильник
  ScriptApp.getProjectTriggers().forEach(function (t) {
    if (t.getHandlerFunction() === 'УтренняяСводка') ScriptApp.deleteTrigger(t);
  });
  ScriptApp.newTrigger('УтренняяСводка')
    .timeBased().atHour(MORNING_HOUR).nearMinute(MORNING_MINUTE).everyDays(1).create();
  log.push('будильник поставлен на ~' + MORNING_HOUR + ':' + pad2(MORNING_MINUTE));

  // 4.3 Webhook — адрес, по которому Telegram будет стучаться
  var url = '';
  try { url = ScriptApp.getService().getUrl() || ''; } catch (err) { url = ''; }

  if (!url) {
    log.push('ВЕБ-ПРИЛОЖЕНИЕ ЕЩЁ НЕ РАЗВЁРНУТО.');
    log.push('Сделай: Развернуть → Новое развёртывание → Веб-приложение,');
    log.push('доступ «Все», выполнять «от моего имени». Потом запусти Настроить() ещё раз.');
  } else if (isPlaceholder(BOT_TOKEN)) {
    log.push('Токен бота ещё не подставлен — webhook не тронут.');
  } else {
    var r = tg('setWebhook', {
      url: url,
      drop_pending_updates: true,
      allowed_updates: JSON.stringify(['message'])
    });
    log.push(r && r.ok ? 'Telegram теперь пишет сюда: ' + url
                       : 'НЕ УДАЛОСЬ ПРОПИСАТЬ WEBHOOK: ' + JSON.stringify(r));
  }

  otchet('Настройка', log);
}

/**
 * Проверяет, что всё живое: бот, ключ модели, листы, будильник, webhook.
 * Ничего не ломает — можно запускать сколько угодно раз.
 */
function Проверка() {
  var log = [];
  var ok = function (t) { log.push('OK   ' + t); };
  var no = function (t) { log.push('НЕТ  ' + t); };

  // Подставлены ли значения
  isPlaceholder(BOT_TOKEN) ? no('токен бота не подставлен') : ok('токен бота подставлен');
  isPlaceholder(API_KEY) ? no('ключ модели не подставлен') : ok('ключ модели подставлен');
  MY_CHAT_ID ? ok('твой Telegram ID: ' + MY_CHAT_ID)
             : no('MY_CHAT_ID = 0 — напиши боту /id, он пришлёт число');

  // Бот
  if (!isPlaceholder(BOT_TOKEN)) {
    var me = tg('getMe', {});
    (me && me.ok) ? ok('бот отвечает: @' + me.result.username)
                  : no('бот не отвечает — токен неверный? ' + JSON.stringify(me));

    var wh = tg('getWebhookInfo', {});
    if (wh && wh.ok && wh.result.url) {
      ok('webhook: ' + wh.result.url);
      if (wh.result.last_error_message) {
        no('последняя ошибка доставки: ' + wh.result.last_error_message);
      }
    } else {
      no('webhook не прописан — запусти Настроить() после развёртывания');
    }
  }

  // Модель
  if (!isPlaceholder(API_KEY)) {
    try {
      var probe = model([{ role: 'user', content: 'Ответь одним словом: готов' }], 20);
      probe ? ok('модель ' + MODEL_TEXT + ' отвечает') : no('модель вернула пустой ответ');
    } catch (err) {
      no('модель не отвечает: ' + err.message);
    }
  }

  // Листы
  var ss = SpreadsheetApp.getActive();
  SHEETS.forEach(function (spec) {
    var sh = ss.getSheetByName(spec.name);
    if (!sh) { no('нет листа «' + spec.name + '»'); return; }
    var head = sh.getRange(1, 1, 1, spec.cols.length).getValues()[0].join('|');
    head === spec.cols.join('|') ? ok('лист «' + spec.name + '» в порядке')
                                 : no('в листе «' + spec.name + '» другие заголовки: ' + head);
  });

  // Будильник
  var est = ScriptApp.getProjectTriggers().some(function (t) {
    return t.getHandlerFunction() === 'УтренняяСводка';
  });
  est ? ok('утренний будильник стоит') : no('утреннего будильника нет — запусти Настроить()');

  // Часовой пояс
  var tzProekta = Session.getScriptTimeZone();
  tzProekta === TZ ? ok('часовой пояс проекта: ' + tzProekta)
                   : no('часовой пояс проекта ' + tzProekta + ', а в коде ' + TZ +
                        ' — поставь его в Настройках проекта, иначе сводка придёт не в 8:30');

  // Живое сообщение
  if (MY_CHAT_ID && !isPlaceholder(BOT_TOKEN)) {
    var s = tg('sendMessage', { chat_id: MY_CHAT_ID, text: 'Проверка связи. Я на месте.' });
    (s && s.ok) ? ok('тестовое сообщение отправлено в Telegram')
                : no('сообщение не дошло: ' + JSON.stringify(s));
  }

  otchet('Проверка', log);
}

/** Печатает отчёт в журнал и, если открыта таблица, показывает окном. */
function otchet(zagolovok, stroki) {
  var text = zagolovok + '\n' + stroki.join('\n');
  Logger.log(text);
  try {
    SpreadsheetApp.getUi().alert(zagolovok, stroki.join('\n'), SpreadsheetApp.getUi().ButtonSet.OK);
  } catch (err) {
    // Запущено из редактора без интерфейса — хватит журнала.
  }
}


/* ══════════════════════════════════════════════════════════════════
   5. ВХОД ИЗ TELEGRAM
   ══════════════════════════════════════════════════════════════════ */

function doGet() {
  return ContentService.createTextOutput('Узелок на месте.');
}

function doPost(e) {
  var ok = ContentService.createTextOutput('ok');
  var upd;
  try {
    upd = JSON.parse(e.postData.contents);
  } catch (err) {
    return ok;
  }

  var msg = upd.message;
  if (!msg || !msg.chat) return ok;

  var chat = msg.chat.id;

  // «/id» отвечаем всем: без него не узнать свой номер.
  if (komanda(msg.text) === '/id') {
    send(chat, 'Твой Telegram ID: ' + chat);
    return ok;
  }

  // Замок: всё остальное — только для хозяина.
  if (!MY_CHAT_ID || String(chat) !== String(MY_CHAT_ID)) return ok;

  // Telegram повторяет доставку, если ответ пришёл не сразу. Отсекаем дубли.
  var cache = CacheService.getScriptCache();
  var kluch = 'upd_' + upd.update_id;
  if (cache.get(kluch)) return ok;
  cache.put(kluch, '1', 600);

  var lock = LockService.getScriptLock();
  if (!lock.tryLock(30000)) {
    send(chat, 'Занят предыдущим сообщением, пришли ещё раз через минуту.');
    return ok;
  }

  try {
    obrabotat(msg, chat);
  } catch (err) {
    send(chat, 'Не смог обработать: ' + err.message);
    Logger.log(err.stack || err.message);
  } finally {
    lock.releaseLock();
  }
  return ok;
}

/** Разбирает одно сообщение: достаёт текст/голос/фото и отдаёт мозгу. */
function obrabotat(msg, chat) {
  var text = (msg.text || msg.caption || '').trim();
  var cmd = komanda(text);

  if (cmd === '/start') {
    send(chat, [
      'Я на месте. Кидай сюда что угодно: голосовые, фото, файлы, мысли.',
      'Разложу по шести разделам и верну, когда спросишь.',
      '',
      'Спросить можно словами: «что у меня на этой неделе».',
      'Команды: /неделя — сводка сейчас, /id — твой номер.'
    ].join('\n'));
    return;
  }

  if (cmd === '/неделя' || cmd === '/week') {
    send(chat, svodka(false));
    return;
  }

  var istochnik = 'текст';
  var kartinka = null;
  var ssylka = '';

  // Голосовое / кружок / аудио
  var golos = msg.voice || msg.video_note || msg.audio;
  if (golos) {
    istochnik = 'голосовое';
    var rasshifrovka = rasshifrovat(golos.file_id, golos.mime_type);
    if (!rasshifrovka) { send(chat, 'Голосовое не расшифровалось. Попробуй ещё раз или напиши текстом.'); return; }
    text = (text ? text + '. ' : '') + rasshifrovka;
  }

  // Фото
  if (msg.photo && msg.photo.length) {
    istochnik = 'фото';
    var big = msg.photo[msg.photo.length - 1];   // последнее — самое крупное
    var blob = skachat(big.file_id);
    if (blob) {
      kartinka = 'data:' + (blob.getContentType() || 'image/jpeg') + ';base64,' +
                 Utilities.base64Encode(blob.getBytes());
      ssylka = sohranit(blob, 'foto');
    }
  }

  // Файл
  if (msg.document) {
    istochnik = 'файл';
    var d = skachat(msg.document.file_id);
    if (d) {
      if (msg.document.file_name) d.setName(msg.document.file_name);
      ssylka = sohranit(d, 'file');
      var mime = String(msg.document.mime_type || '');
      if (mime.indexOf('image/') === 0) {
        kartinka = 'data:' + mime + ';base64,' + Utilities.base64Encode(d.getBytes());
      } else {
        text = (text ? text + '. ' : '') + 'Прислан файл: ' + (msg.document.file_name || 'без имени');
      }
    }
  }

  if (!text && !kartinka) { send(chat, 'Пустое сообщение — не понял, что записывать.'); return; }

  var otvet = mozg(text, kartinka, istochnik, ssylka);
  send(chat, otvet || 'Записал.');
}


/* ══════════════════════════════════════════════════════════════════
   6. МОЗГ
   ══════════════════════════════════════════════════════════════════ */

/** Отдаёт сообщение модели, раскладывает ответ по листам, возвращает текст для Telegram. */
function mozg(text, kartinka, istochnik, ssylka) {
  var segodnya = segodnyaISO();

  var system = PROMPT + '\n\nСЕГОДНЯ: ' + segodnya + ' (' + denNedeli(segodnya) + ').' +
               '\n\nЧТО УЖЕ ЛЕЖИТ В ЯЩИКАХ:\n' + snimok();

  var content;
  if (kartinka) {
    content = [
      { type: 'text', text: text || 'Разбери, что на этом изображении.' },
      { type: 'image_url', image_url: { url: kartinka } }
    ];
  } else {
    content = text;
  }

  var syroy = model([
    { role: 'system', content: system },
    { role: 'user', content: content }
  ], 1200);

  var razbor = razobrat(syroy);
  if (!razbor) return syroy || 'Модель ответила непонятно, ничего не записал.';

  var zapisano = [];
  (razbor.записи || []).forEach(function (z) {
    var polya = z.поля || {};
    if (ssylka && !polya['ссылка_на_файл']) polya['ссылка_на_файл'] = ssylka;
    if (!polya['источник']) polya['источник'] = istochnik;
    if (zapisat(z.раздел, polya)) zapisano.push(z.раздел);
  });

  var otvet = razbor.ответ || '';
  if (!otvet && zapisano.length) otvet = 'Записал в: ' + zapisano.join(', ') + '.';
  return otvet;
}

/** Вызов модели. Возвращает строку ответа. */
function model(messages, maxTokens) {
  var r = UrlFetchApp.fetch(API_BASE + '/chat/completions', {
    method: 'post',
    contentType: 'application/json',
    headers: { Authorization: 'Bearer ' + API_KEY },
    payload: JSON.stringify({
      model: MODEL_TEXT,
      messages: messages,
      temperature: 0.2,
      max_tokens: maxTokens || 800
    }),
    muteHttpExceptions: true
  });

  var kod = r.getResponseCode();
  var telo = r.getContentText();
  if (kod !== 200) {
    throw new Error('модель вернула ' + kod + ': ' + telo.slice(0, 300));
  }
  var j = JSON.parse(telo);
  return (j.choices && j.choices[0] && j.choices[0].message.content) || '';
}

/** Расшифровка голосового через Whisper. */
function rasshifrovat(fileId, mime) {
  var blob = skachat(fileId);
  if (!blob) return '';

  // Whisper смотрит на расширение имени файла, а Telegram его не отдаёт — проставляем руками.
  var ext = 'oga';
  if (mime) {
    if (mime.indexOf('mp4') >= 0) ext = 'mp4';
    else if (mime.indexOf('mpeg') >= 0) ext = 'mp3';
    else if (mime.indexOf('wav') >= 0) ext = 'wav';
    else if (mime.indexOf('webm') >= 0) ext = 'webm';
  }
  blob.setName('golos.' + ext);

  var r = UrlFetchApp.fetch(API_BASE + '/audio/transcriptions', {
    method: 'post',
    headers: { Authorization: 'Bearer ' + API_KEY },
    payload: { file: blob, model: MODEL_VOICE, language: 'ru' },
    muteHttpExceptions: true
  });

  if (r.getResponseCode() !== 200) {
    throw new Error('расшифровка вернула ' + r.getResponseCode() + ': ' + r.getContentText().slice(0, 300));
  }
  var j = JSON.parse(r.getContentText());
  return j.text || '';
}

/** Вытаскивает JSON из ответа модели, даже если она обернула его в ```json. */
function razobrat(s) {
  if (!s) return null;
  var t = String(s).trim().replace(/^```(?:json)?/i, '').replace(/```$/, '').trim();
  try { return JSON.parse(t); } catch (err) { /* пробуем найти скобки */ }
  var a = t.indexOf('{'), b = t.lastIndexOf('}');
  if (a < 0 || b <= a) return null;
  try { return JSON.parse(t.slice(a, b + 1)); } catch (err) { return null; }
}


/* ══════════════════════════════════════════════════════════════════
   7. ЯЩИКИ: ЗАПИСЬ И ЧТЕНИЕ
   ══════════════════════════════════════════════════════════════════ */

/** Кладёт строку в нужный лист, раскладывая поля по заголовкам. */
function zapisat(razdel, polya) {
  var sh = list(razdel);
  if (!sh) { Logger.log('неизвестный раздел: ' + razdel); return false; }

  var head = sh.getRange(1, 1, 1, sh.getLastColumn()).getValues()[0];
  var segodnya = segodnyaISO();

  var stroka = head.map(function (h) {
    var kluch = String(h).trim();
    var v = polya[kluch];
    if (v === undefined || v === null || v === '') {
      if (kluch === 'дата' || kluch === 'дата_создания' || kluch === 'дата_обновления') return segodnya;
      if (kluch === 'статус') return 'новое';
      if (kluch === 'куплено' || kluch === 'отправлено') return 'нет';
      return '';
    }
    return typeof v === 'object' ? JSON.stringify(v) : String(v);
  });

  sh.appendRow(stroka);
  return true;
}

/** Лист по имени, без оглядки на регистр и лишние пробелы. */
function list(imya) {
  if (!imya) return null;
  var cel = String(imya).trim().toLowerCase();
  var found = null;
  SpreadsheetApp.getActive().getSheets().forEach(function (sh) {
    if (sh.getName().trim().toLowerCase() === cel) found = sh;
  });
  return found;
}

/** Все строки листа как массив объектов {заголовок: значение}. */
function stroki(imya, limit) {
  var sh = list(imya);
  if (!sh) return [];
  var n = sh.getLastRow() - 1;
  if (n < 1) return [];
  var first = limit && n > limit ? n - limit + 2 : 2;
  var count = limit && n > limit ? limit : n;
  var head = sh.getRange(1, 1, 1, sh.getLastColumn()).getValues()[0];
  var data = sh.getRange(first, 1, count, sh.getLastColumn()).getValues();

  return data.map(function (r, i) {
    var o = { _строка: first + i };
    head.forEach(function (h, j) { o[String(h).trim()] = znachenie(r[j]); });
    return o;
  });
}

/** Даты из таблицы приводим к ГГГГ-ММ-ДД, остальное — к строке. */
function znachenie(v) {
  if (v instanceof Date) return Utilities.formatDate(v, TZ, 'yyyy-MM-dd');
  return v === null || v === undefined ? '' : String(v).trim();
}

/** Короткая выжимка из ящиков — её видит модель, чтобы отвечать на вопросы. */
function snimok() {
  var out = [];

  var pamyat = stroki('Личная память', 60);
  if (pamyat.length) {
    out.push('Личная память:');
    pamyat.forEach(function (r) { out.push('  • ' + r['факт'] + (r['категория'] ? ' (' + r['категория'] + ')' : '')); });
  }

  var dela = stroki('Дела', 120).filter(function (r) { return String(r['статус']).toLowerCase() !== 'сделано'; });
  if (dela.length) {
    out.push('Дела (не сделанные):');
    dela.slice(-40).forEach(function (r) { out.push('  • ' + r['текст'] + (r['срок'] ? ' — до ' + r['срок'] : ' — без срока')); });
  }

  var nap = stroki('Напоминания', 80).filter(function (r) { return String(r['отправлено']).toLowerCase() !== 'да'; });
  if (nap.length) {
    out.push('Напоминания:');
    nap.slice(-30).forEach(function (r) { out.push('  • ' + r['когда'] + ' — ' + r['текст']); });
  }

  var pok = stroki('Покупки', 60).filter(function (r) { return String(r['куплено']).toLowerCase() !== 'да'; });
  if (pok.length) {
    out.push('Покупки:');
    pok.slice(-25).forEach(function (r) { out.push('  • ' + r['что'] + (r['где'] ? ' — ' + r['где'] : '')); });
  }

  var idei = stroki('Идеи', 40);
  if (idei.length) {
    out.push('Идеи (последние):');
    idei.slice(-15).forEach(function (r) { out.push('  • ' + r['идея']); });
  }

  var doc = stroki('Документы', 40);
  if (doc.length) {
    out.push('Документы (последние):');
    doc.slice(-15).forEach(function (r) { out.push('  • ' + r['дата'] + ' ' + r['тип'] + ': ' + r['описание']); });
  }

  return out.length ? out.join('\n') : 'Пока пусто.';
}


/* ══════════════════════════════════════════════════════════════════
   8. УТРЕННЯЯ СВОДКА
   ══════════════════════════════════════════════════════════════════ */

/** Запускается будильником около 8:30. */
function УтренняяСводка() {
  if (!MY_CHAT_ID) return;
  var t = svodka(true);   // true — пометить сегодняшние напоминания отправленными
  if (t) send(MY_CHAT_ID, t);
}

/**
 * Собирается без модели — из самих таблиц. Так надёжнее и бесплатно:
 * даже если ключ кончился, утреннее сообщение всё равно придёт.
 *
 * pometit — помечать ли сегодняшние напоминания отправленными.
 * Утренняя рассылка помечает, ручное «/неделя» — нет, иначе спросил в семь утра
 * и в 8:30 напоминания уже не пришли бы.
 */
function svodka(pometit) {
  var segodnya = segodnyaISO();
  var granica = plusDney(segodnya, HORIZON_DAYS);
  var out = [];

  // Напоминания на сегодня
  var sh = list('Напоминания');
  var segodnyashnie = [];
  if (sh) {
    stroki('Напоминания', 300).forEach(function (r) {
      if (String(r['отправлено']).toLowerCase() === 'да') return;
      var d = data10(r['когда']);
      if (!d) return;
      if (d <= segodnya) {
        segodnyashnie.push(r);
        if (pometit && String(r['повтор']).toLowerCase() !== 'да') {
          var col = kolonka(sh, 'отправлено');
          if (col) sh.getRange(r._строка, col).setValue('да');
        }
      }
    });
  }

  if (segodnyashnie.length) {
    out.push('Сегодня:');
    segodnyashnie.forEach(function (r) { out.push('• ' + vremya(r['когда']) + r['текст']); });
  }

  // Дела: просроченные и ближайшие
  var prosr = [], blizhnie = [], bezSroka = 0;
  stroki('Дела', 500).forEach(function (r) {
    if (String(r['статус']).toLowerCase() === 'сделано') return;
    var d = data10(r['срок']);
    if (!d) { bezSroka++; return; }
    if (d < segodnya) prosr.push(r);
    else if (d <= granica) blizhnie.push(r);
  });

  var poSroku = function (a, b) { return data10(a['срок']) < data10(b['срок']) ? -1 : 1; };

  if (prosr.length) {
    out.push(out.length ? '' : null);
    out.push('Просрочено:');
    prosr.sort(poSroku).forEach(function (r) { out.push('• ' + r['текст'] + ' — было до ' + data10(r['срок'])); });
  }

  if (blizhnie.length) {
    out.push('');
    out.push('На этой неделе:');
    blizhnie.sort(poSroku).forEach(function (r) { out.push('• ' + r['текст'] + ' — до ' + data10(r['срок'])); });
  }

  if (bezSroka) {
    out.push('');
    out.push('Без срока висит: ' + bezSroka + '.');
  }

  var chistyy = out.filter(function (x) { return x !== null; });
  if (!segodnyashnie.length && !prosr.length && !blizhnie.length) {
    return segodnya + '. На сегодня и на неделю ничего срочного.' +
           (bezSroka ? ' Без срока висит: ' + bezSroka + '.' : '');
  }
  return segodnya + ', ' + denNedeli(segodnya) + '.\n\n' + chistyy.join('\n');
}

/** Номер колонки по заголовку. */
function kolonka(sh, imya) {
  var head = sh.getRange(1, 1, 1, sh.getLastColumn()).getValues()[0];
  for (var i = 0; i < head.length; i++) {
    if (String(head[i]).trim() === imya) return i + 1;
  }
  return 0;
}


/* ══════════════════════════════════════════════════════════════════
   9. TELEGRAM И ФАЙЛЫ
   ══════════════════════════════════════════════════════════════════ */

function tg(metod, params) {
  var r = UrlFetchApp.fetch('https://api.telegram.org/bot' + BOT_TOKEN + '/' + metod, {
    method: 'post',
    payload: params,
    muteHttpExceptions: true
  });
  try { return JSON.parse(r.getContentText()); }
  catch (err) { return { ok: false, description: r.getContentText().slice(0, 200) }; }
}

function send(chat, text) {
  if (!text) return;
  // Telegram не принимает сообщения длиннее 4096 символов.
  var kusok = String(text);
  while (kusok.length) {
    tg('sendMessage', { chat_id: chat, text: kusok.slice(0, 4000), disable_web_page_preview: true });
    kusok = kusok.slice(4000);
  }
}

/** Скачивает присланный файл из Telegram. */
function skachat(fileId) {
  var f = tg('getFile', { file_id: fileId });
  if (!f || !f.ok) return null;
  var r = UrlFetchApp.fetch(
    'https://api.telegram.org/file/bot' + BOT_TOKEN + '/' + f.result.file_path,
    { muteHttpExceptions: true }
  );
  return r.getResponseCode() === 200 ? r.getBlob() : null;
}

/**
 * Кладёт файл на Google Диск и возвращает ссылку.
 * Ссылку из Telegram в таблицу писать нельзя — в ней виден токен бота.
 */
function sohranit(blob, prefix) {
  try {
    var papki = DriveApp.getFoldersByName(DRIVE_FOLDER);
    var papka = papki.hasNext() ? papki.next() : DriveApp.createFolder(DRIVE_FOLDER);
    var imya = blob.getName() && blob.getName() !== 'blob'
      ? blob.getName()
      : prefix + '-' + Utilities.formatDate(new Date(), TZ, 'yyyy-MM-dd-HHmmss') + '.jpg';
    return papka.createFile(blob.setName(imya)).getUrl();
  } catch (err) {
    Logger.log('файл не сохранился: ' + err.message);
    return '';
  }
}


/* ══════════════════════════════════════════════════════════════════
   10. МЕЛОЧИ
   ══════════════════════════════════════════════════════════════════ */

function segodnyaISO() {
  return Utilities.formatDate(new Date(), TZ, 'yyyy-MM-dd');
}

function plusDney(iso, n) {
  var d = new Date(iso + 'T12:00:00Z');
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}

/** Из «2026-09-11 18:00» достаёт «2026-09-11». Не дата — пустая строка. */
function data10(v) {
  var s = String(v || '').trim();
  return /^\d{4}-\d{2}-\d{2}/.test(s) ? s.slice(0, 10) : '';
}

/** Из «2026-09-11 18:00» достаёт «18:00 » для строки сводки. */
function vremya(v) {
  var m = String(v || '').match(/(\d{2}:\d{2})/);
  return m ? m[1] + ' — ' : '';
}

function denNedeli(iso) {
  var dni = ['воскресенье', 'понедельник', 'вторник', 'среда', 'четверг', 'пятница', 'суббота'];
  return dni[new Date(iso + 'T12:00:00Z').getUTCDay()];
}

function pad2(n) { return n < 10 ? '0' + n : String(n); }

/** Команда из сообщения: «/неделя@moy_bot» → «/неделя». Не команда — пустая строка. */
function komanda(text) {
  var m = String(text || '').trim().toLowerCase().match(/^(\/[a-zа-яё_]+)(@\S+)?$/);
  return m ? m[1] : '';
}

function isPlaceholder(v) {
  return !v || String(v).indexOf('СЮДА_') === 0;
}

/** Меню в таблице — чтобы не искать функции в редакторе. */
function onOpen() {
  SpreadsheetApp.getUi()
    .createMenu('Узелок')
    .addItem('Настроить', 'Настроить')
    .addItem('Проверка', 'Проверка')
    .addItem('Прислать сводку сейчас', 'УтренняяСводка')
    .addToUi();
}
