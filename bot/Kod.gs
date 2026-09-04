/**
 * УЗЕЛОК-БОТ — второй мозг в Telegram.
 *
 * Живёт внутри Google Таблицы (Расширения → Apps Script).
 * Сервера не надо, платить за хостинг не надо, VPN не надо.
 *
 * Что делает:
 *   • ловит из Telegram текст, голосовые, фото и файлы —
 *     сам, опросом раз в минуту, либо мгновенно, если развернуть веб-приложение;
 *   • расшифровывает голос и читает фото словами — одной моделью Gemini;
 *   • раскладывает по шести листам этой же таблицы;
 *   • отвечает на вопросы вроде «что мне нужно сделать на этой неделе»;
 *   • около 8:30 сам присылает, что сегодня и что горит.
 *
 * Порядок настройки — в docs/nastroyka.md. Коротко:
 *   1. вставить этот файл в редактор и сохранить;
 *   2. обновить таблицу (F5) — появится меню «Узелок»;
 *   3. «Ввести ключи» — одна вставка, дальше всё само.
 * Развёртывание не обязательно: без него бот работает опросом.
 * Часовой пояс проекта тоже трогать не надо, время считается тут.
 */

/* ══════════════════════════════════════════════════════════════════
   1. ТРИ ЗНАЧЕНИЯ
   Правильный способ — НЕ трогать этот файл вообще:
   открой таблицу, меню «Узелок» → «Ввести ключи», вставь их в окошки.
   Так они лягут в хранилище проекта, а не в текст кода.

   Строки ниже — запасной путь, если так удобнее. Оставишь как есть —
   ничего не сломается, бот возьмёт значения из хранилища.
   ══════════════════════════════════════════════════════════════════ */

/** Токен от @BotFather, вида 7712345678:AAF... */
var BOT_TOKEN = 'СЮДА_ТОКЕН_ОТ_BOTFATHER';

/** Ключ Gemini из Google AI Studio, вида AIza... */
var GEMINI_KEY = 'СЮДА_КЛЮЧ_GEMINI';

/** Твой Telegram ID, только цифры. Не знаешь — напиши боту /id, он ответит. */
var MY_CHAT_ID = 0;


/* ══════════════════════════════════════════════════════════════════
   2. НАСТРОЙКИ, КОТОРЫЕ ОБЫЧНО НЕ ТРОГАЮТ
   ══════════════════════════════════════════════════════════════════ */

/** Адрес Google. Менять не надо. */
var API_BASE = 'https://generativelanguage.googleapis.com/v1beta';

/**
 * Модель. Она же расшифровывает голосовые и читает фото — отдельной не нужно.
 * Если Google скажет, что такой модели нет, Проверка() напечатает список доступных
 * именно твоему ключу — впиши оттуда.
 */
var MODEL = 'gemini-2.5-flash';

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
   3.5 ОТКУДА БЕРУТСЯ КЛЮЧИ
   Сначала смотрим в хранилище проекта (меню «Ввести ключи»),
   и только если там пусто — в строки выше.
   ══════════════════════════════════════════════════════════════════ */

function hranilishe() {
  return PropertiesService.getScriptProperties();
}

function nastroyka(imya, izKoda) {
  var v = '';
  try { v = hranilishe().getProperty(imya) || ''; } catch (err) { v = ''; }
  if (v) return String(v).trim();
  return isPlaceholder(izKoda) ? '' : String(izKoda).trim();
}

function tokenBota()   { return nastroyka('BOT_TOKEN', BOT_TOKEN); }
function kluchModeli() { return nastroyka('GEMINI_KEY', GEMINI_KEY); }
function moyChat()     { return nastroyka('MY_CHAT_ID', MY_CHAT_ID ? String(MY_CHAT_ID) : ''); }


/* ══════════════════════════════════════════════════════════════════
   4. НАСТРОЙКА — запускается один раз руками
   ══════════════════════════════════════════════════════════════════ */

/**
 * Спрашивает три значения окошками и кладёт в хранилище проекта.
 * Код при этом не меняется — значит и новую версию развёртывания
 * делать не надо, ключи подхватываются на лету.
 */
function ВвестиКлючи() {
  var ui = SpreadsheetApp.getUi();

  // Сначала пробуем принять всё одной вставкой: что есть — разберём по виду.
  var srazu = ui.prompt('Ключи Узелка',
    'Вставь всё сразу, каждое с новой строки — порядок неважен:\n' +
    '  • токен бота от @BotFather\n' +
    '  • ключ Gemini (начинается на AIza)\n' +
    '  • твой номер в Telegram (только цифры)\n\n' +
    'Есть не всё — вставь что есть, про остальное спрошу отдельно.',
    ui.ButtonSet.OK_CANCEL);

  var nashli = {};
  if (srazu.getSelectedButton() === ui.Button.OK) {
    nashli = razobratKluchi(srazu.getResponseText());
    Object.keys(nashli).forEach(function (k) { hranilishe().setProperty(k, nashli[k]); });
  }

  var spros = function (zagolovok, poyasnenie, tekushee) {
    var hvost = tekushee ? '\n\nСейчас записано: ' + zatemnit(tekushee) +
                           '\nОставить как есть — нажми «Отмена».' : '';
    var r = ui.prompt(zagolovok, poyasnenie + hvost, ui.ButtonSet.OK_CANCEL);
    if (r.getSelectedButton() !== ui.Button.OK) return null;
    var v = String(r.getResponseText() || '').trim();
    return v || null;
  };

  var itog = [];
  if (nashli['BOT_TOKEN'])  itog.push('токен бота узнал из вставки');
  if (nashli['GEMINI_KEY']) itog.push('ключ Gemini узнал из вставки');
  if (nashli['MY_CHAT_ID']) itog.push('номер узнал из вставки');

  // Чего не хватило — спросим поштучно.
  if (!tokenBota()) {
    var t = spros('Токен бота',
      'Вставь токен от @BotFather. Выглядит так: 7712345678:AAFdKk9-…', '');
    if (t) {
      if (t.indexOf(':') < 0) { ui.alert('Это не похоже на токен — в нём должно быть двоеточие. Не записал.'); }
      else { hranilishe().setProperty('BOT_TOKEN', t); itog.push('токен бота записан'); }
    }
  }

  if (!kluchModeli()) {
    var k = spros('Ключ Gemini',
      'Вставь ключ из Google AI Studio. Начинается на AIza…', '');
    if (k) {
      if (k.indexOf('AIza') !== 0) { ui.alert('Ключ Gemini должен начинаться на AIza. Не записал.'); }
      else { hranilishe().setProperty('GEMINI_KEY', k); itog.push('ключ Gemini записан'); }
    }
  }

  if (!moyChat()) {
    var c = spros('Твой номер в Telegram',
      'Только цифры. Не знаешь — напиши боту /id, он ответит числом.', '');
    if (c) {
      if (!/^\d+$/.test(c)) { ui.alert('Номер — это только цифры, без пробелов и букв. Не записал.'); }
      else { hranilishe().setProperty('MY_CHAT_ID', c); itog.push('номер записан'); }
    }
  }

  if (!itog.length) { ui.alert('Ничего не менял.'); return; }

  if (!(tokenBota() && kluchModeli() && moyChat())) {
    ui.alert('Ключи', itog.join('\n') +
      '\n\nЧего-то ещё не хватает. Запусти «Ввести ключи» снова и добавь недостающее.',
      ui.ButtonSet.OK);
    return;
  }

  // Всё на месте — доводим до конца сами, чтобы человеку не жать лишнего.
  var log = itog.slice();
  log.push('');
  log.push('— Настройка —');
  try { log = log.concat(Настроить(true)); }
  catch (err) { log.push('НЕ УДАЛОСЬ НАСТРОИТЬ: ' + err.message); }

  log.push('');
  log.push('— Проверка —');
  try { log = log.concat(Проверка(true)); }
  catch (err) { log.push('НЕ УДАЛОСЬ ПРОВЕРИТЬ: ' + err.message); }

  var plohih = log.filter(function (r) { return String(r).indexOf('НЕТ') === 0 ||
                                                String(r).indexOf('НЕ УДАЛОСЬ') === 0; });
  log.push('');
  log.push(plohih.length ? 'Есть чем заняться: строк с «НЕТ» — ' + plohih.length + '.'
                         : 'Всё готово. Пиши боту в Telegram.');

  otchet('Узелок настроен', log);
}

/**
 * Разбирает вставленный кусок текста и раскладывает по виду строки:
 * с двоеточием — токен, на AIza — ключ, только цифры — номер.
 * Мусор и лишние строки просто игнорируются.
 */
function razobratKluchi(text) {
  var out = {};
  String(text || '').split(/[\r\n]+/).forEach(function (stroka) {
    var v = String(stroka).trim().replace(/^["'<]+|["'>]+$/g, '');
    if (!v) return;
    if (/^\d{5,}:[A-Za-z0-9_-]{20,}$/.test(v))      { if (!out['BOT_TOKEN'])  out['BOT_TOKEN'] = v; }
    else if (/^AIza[A-Za-z0-9_-]{10,}$/.test(v))     { if (!out['GEMINI_KEY']) out['GEMINI_KEY'] = v; }
    else if (/^\d{5,15}$/.test(v))                   { if (!out['MY_CHAT_ID']) out['MY_CHAT_ID'] = v; }
  });
  return out;
}

/** Показывает ключ так, чтобы узнать можно, а списать нельзя. */
function zatemnit(v) {
  var s = String(v || '');
  if (s.length <= 8) return '••••';
  return s.slice(0, 4) + '…' + s.slice(-4);
}

/**
 * Создаёт шесть листов, вешает утренний будильник и говорит Telegram,
 * куда присылать сообщения. Запускать ПОСЛЕ развёртывания веб-приложения.
 */
function Настроить(tiho) {
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

  // 4.2 Утренний будильник. Ходит каждый час, а отправляет один раз —
  //     когда по твоему часовому поясу наступает нужное время.
  //     Поэтому часовой пояс проекта настраивать не надо.
  ScriptApp.getProjectTriggers().forEach(function (t) {
    var f = t.getHandlerFunction();
    if (f === 'utrom' || f === 'УтренняяСводка') ScriptApp.deleteTrigger(t);
  });
  ScriptApp.newTrigger('utrom').timeBased().everyHours(1).create();
  log.push('сводка будет приходить около ' + MORNING_HOUR + ':' + pad2(MORNING_MINUTE) +
           ' по времени ' + TZ);

  // 4.3 Связь с Telegram. Способ выбирается сам:
  //     развёрнуто веб-приложение — Telegram пишет нам (мгновенно);
  //     не развёрнуто — спрашиваем сами раз в минуту.
  var url = '';
  try { url = ScriptApp.getService().getUrl() || ''; } catch (err) { url = ''; }

  if (!tokenBota()) {
    log.push('Токена бота ещё нет — связь не настраивал.');
    log.push('Меню «Узелок» → «Ввести ключи», потом «Настроить» ещё раз.');
  } else if (url) {
    ubratOpros();
    var r = tg('setWebhook', {
      url: url,
      drop_pending_updates: true,
      allowed_updates: JSON.stringify(['message'])
    });
    log.push(r && r.ok
      ? 'Связь: Telegram пишет напрямую, ответ мгновенный.'
      : 'НЕ УДАЛОСЬ ПРОПИСАТЬ WEBHOOK: ' + JSON.stringify(r));
  } else {
    // drop_pending_updates: пока бота настраивали, в Telegram копились сообщения.
    // Без сброса бот ответит на каждое разом — стеной. Начинаем с чистого листа.
    tg('deleteWebhook', { drop_pending_updates: true });
    hranilishe().deleteProperty('TG_OFFSET');
    ubratOpros();
    ScriptApp.newTrigger('opros').timeBased().everyMinutes(1).create();
    log.push('Связь: бот сам спрашивает Telegram раз в минуту.');
    log.push('Накопившиеся до этой минуты сообщения отброшены.');
    log.push('Работает без развёртывания. Ответ приходит в течение минуты.');
    log.push('Хочешь мгновенно — разверни веб-приложение и запусти «Настроить» ещё раз.');
  }

  if (!tiho) otchet('Настройка', log);
  return log;
}

/**
 * Проверяет, что всё живое: бот, ключ модели, листы, будильник, webhook.
 * Ничего не ломает — можно запускать сколько угодно раз.
 */
function Проверка(tiho) {
  var log = [];
  var ok = function (t) { log.push('OK   ' + t); };
  var no = function (t) { log.push('НЕТ  ' + t); };

  // Заданы ли значения
  tokenBota()   ? ok('токен бота на месте (' + zatemnit(tokenBota()) + ')')
                : no('токена бота нет — меню «Узелок» → «Ввести ключи»');
  kluchModeli() ? ok('ключ Gemini на месте (' + zatemnit(kluchModeli()) + ')')
                : no('ключа Gemini нет — меню «Узелок» → «Ввести ключи»');
  moyChat()     ? ok('твой Telegram ID: ' + moyChat())
                : no('номера нет — напиши боту /id, он пришлёт число');

  // Бот
  if (tokenBota()) {
    var me = tg('getMe', {});
    (me && me.ok) ? ok('бот отвечает: @' + me.result.username)
                  : no('бот не отвечает — токен неверный? ' + JSON.stringify(me));

    var estOpros = ScriptApp.getProjectTriggers().some(function (t) {
      return t.getHandlerFunction() === 'opros';
    });
    var wh = tg('getWebhookInfo', {});
    if (wh && wh.ok && wh.result.url) {
      ok('связь: Telegram пишет напрямую, ответ мгновенный');
      if (wh.result.last_error_message) {
        no('последняя ошибка доставки: ' + wh.result.last_error_message);
      }
    } else if (estOpros) {
      ok('связь: опрос раз в минуту (без развёртывания)');
    } else {
      no('связи с Telegram нет — запусти «Настроить»');
    }
  }

  // Модель
  if (kluchModeli()) {
    try {
      var probe = gemini('', [{ text: 'Ответь одним словом: готов' }], 30, false);
      probe ? ok('модель ' + MODEL + ' отвечает: ' + probe)
            : no('модель вернула пустой ответ');
    } catch (err) {
      no('модель ' + MODEL + ' не отвечает: ' + err.message);
      log.push('     впиши в MODEL одну из этих — они доступны твоему ключу:');
      spisokModeley().forEach(function (m) { log.push('       • ' + m); });
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
    return t.getHandlerFunction() === 'utrom';
  });
  est ? ok('утренний будильник стоит')
      : no('утреннего будильника нет — запусти «Настроить»');

  // Время
  ok('сейчас у тебя ' + Utilities.formatDate(new Date(), TZ, 'HH:mm') +
     ' (' + TZ + '), сводка приходит около ' + MORNING_HOUR + ':' + pad2(MORNING_MINUTE));

  // Живое сообщение
  if (moyChat() && tokenBota()) {
    var s = tg('sendMessage', { chat_id: moyChat(), text: 'Проверка связи. Я на месте.' });
    (s && s.ok) ? ok('тестовое сообщение отправлено в Telegram')
                : no('сообщение не дошло: ' + JSON.stringify(s));
  }

  if (!tiho) otchet('Проверка', log);
  return log;
}

/**
 * Полная остановка: снимает все будильники и отключает webhook.
 * Бот замолкает и перестаёт отвечать вообще. Обратно — «Настроить».
 */
function ОстановитьБота() {
  var snyato = 0;
  ScriptApp.getProjectTriggers().forEach(function (t) {
    var f = t.getHandlerFunction();
    if (f === 'opros' || f === 'utrom' || f === 'УтренняяСводка') {
      ScriptApp.deleteTrigger(t);
      snyato++;
    }
  });

  var webhook = 'не трогал';
  if (tokenBota()) {
    var r = tg('deleteWebhook', { drop_pending_updates: true });
    webhook = (r && r.ok) ? 'отключён, очередь очищена' : 'отключить не вышло';
  }

  otchet('Бот остановлен', [
    'снято будильников: ' + snyato,
    'webhook: ' + webhook,
    '',
    'Теперь он молчит: не отвечает и не присылает сводку.',
    'Записи в таблице целы, ничего не удалено.',
    'Включить обратно — «Узелок» → «Настроить».'
  ]);
}

/** Снимает будильник опроса, если он стоял. */
function ubratOpros() {
  ScriptApp.getProjectTriggers().forEach(function (t) {
    if (t.getHandlerFunction() === 'opros') ScriptApp.deleteTrigger(t);
  });
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

/** Приходит, когда Telegram стучится сам (после развёртывания). */
function doPost(e) {
  var ok = ContentService.createTextOutput('ok');
  var upd;
  try {
    upd = JSON.parse(e.postData.contents);
  } catch (err) {
    return ok;
  }

  var lock = LockService.getScriptLock();
  if (!lock.tryLock(30000)) return ok;
  try { obrabotatUpdate(upd); } finally { lock.releaseLock(); }
  return ok;
}

/**
 * Опрос: то же самое, но бот сам спрашивает Telegram, нет ли новых сообщений.
 * Запускается будильником раз в минуту, когда веб-приложение не развёрнуто.
 * Ответ приходит не мгновенно, а в течение минуты — это единственная разница.
 */
function opros() {
  if (!tokenBota()) return;

  var lock = LockService.getScriptLock();
  if (!lock.tryLock(5000)) return;   // предыдущий опрос ещё идёт — пропускаем круг

  try {
    var pamyat = hranilishe();
    var off = Number(pamyat.getProperty('TG_OFFSET') || 0);

    for (var krug = 0; krug < 5; krug++) {
      var r = tg('getUpdates', {
        offset: off,
        timeout: 0,
        limit: 20,
        allowed_updates: JSON.stringify(['message'])
      });
      if (!r || !r.ok || !r.result || !r.result.length) break;

      for (var i = 0; i < r.result.length; i++) {
        var upd = r.result[i];
        if (upd.update_id >= off) off = upd.update_id + 1;
        // Смещение сохраняем ДО обработки: упавшее сообщение не должно
        // возвращаться круг за кругом и блокировать всё, что за ним.
        pamyat.setProperty('TG_OFFSET', String(off));
        try { obrabotatUpdate(upd); }
        catch (err) { Logger.log('опрос: ' + (err.stack || err.message)); }
      }
      if (r.result.length < 20) break;
    }
  } finally {
    lock.releaseLock();
  }

  // Раз уж просыпаемся каждую минуту — заодно смотрим, не пора ли сводку.
  try { utrom(); } catch (err) { Logger.log('утро: ' + (err.stack || err.message)); }
}

/** Одно сообщение — общее для обоих способов. Замок берёт вызывающий. */
function obrabotatUpdate(upd) {
  var msg = upd && upd.message;
  if (!msg || !msg.chat) return;

  var chat = msg.chat.id;

  // «/id» отвечаем всем: без него не узнать свой номер.
  if (komanda(msg.text) === '/id') {
    send(chat, 'Твой Telegram ID: ' + chat);
    return;
  }

  // Замок: всё остальное — только для хозяина.
  var moy = moyChat();
  if (!moy || String(chat) !== moy) return;

  // Одно и то же сообщение может прийти дважды — отсекаем по номеру.
  var cache = CacheService.getScriptCache();
  var kluch = 'upd_' + upd.update_id;
  if (cache.get(kluch)) return;
  cache.put(kluch, '1', 600);

  try {
    obrabotat(msg, chat);
  } catch (err) {
    send(chat, 'Не смог обработать: ' + err.message);
    Logger.log(err.stack || err.message);
  }
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
      kartinka = { mime: blob.getContentType() || 'image/jpeg',
                   data: Utilities.base64Encode(blob.getBytes()) };
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
        kartinka = { mime: mime, data: Utilities.base64Encode(d.getBytes()) };
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

  var parts = [{ text: text || 'Разбери, что на этом изображении.' }];
  if (kartinka) {
    parts.push({ inline_data: { mime_type: kartinka.mime, data: kartinka.data } });
  }

  // json:true — Google обязуется вернуть чистый JSON, без ```-обёрток.
  var syroy = gemini(system, parts, 1200, true);

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

/**
 * Один вызов Google.
 *   system — постоянная инструкция, может быть пустой;
 *   parts  — куски сообщения: {text: "..."} и/или {inline_data: {mime_type, data}};
 *   json   — потребовать чистый JSON в ответе.
 */
function gemini(system, parts, maxTokens, json) {
  var body = {
    contents: [{ role: 'user', parts: parts }],
    generationConfig: {
      temperature: 0.2,
      maxOutputTokens: maxTokens || 800
    }
  };
  if (system) body.system_instruction = { parts: [{ text: system }] };
  if (json) body.generationConfig.responseMimeType = 'application/json';

  var r = UrlFetchApp.fetch(API_BASE + '/models/' + MODEL + ':generateContent', {
    method: 'post',
    contentType: 'application/json',
    headers: { 'x-goog-api-key': kluchModeli() },
    payload: JSON.stringify(body),
    muteHttpExceptions: true
  });

  var kod = r.getResponseCode();
  var telo = r.getContentText();
  if (kod !== 200) {
    throw new Error('Google вернул ' + kod + ': ' + telo.slice(0, 300));
  }

  var j = JSON.parse(telo);
  var kand = j.candidates && j.candidates[0];
  if (!kand) {
    var blok = j.promptFeedback && j.promptFeedback.blockReason;
    throw new Error(blok ? 'Google отказался отвечать: ' + blok
                         : 'Google не вернул ответ: ' + telo.slice(0, 200));
  }

  var kuski = (kand.content && kand.content.parts) || [];
  var out = kuski.map(function (p) { return p.text || ''; }).join('').trim();

  if (!out && kand.finishReason && kand.finishReason !== 'STOP') {
    throw new Error('Google оборвал ответ: ' + kand.finishReason);
  }
  return out;
}

/** Расшифровка голосового: та же модель, звук уходит вложением. */
function rasshifrovat(fileId, mime) {
  var blob = skachat(fileId);
  if (!blob) return '';
  var tip = mime || blob.getContentType() || 'audio/ogg';

  return gemini(
    'Ты расшифровываешь голосовые сообщения на русском языке. Пиши только то, что услышал.',
    [
      { text: 'Расшифруй это голосовое дословно. Верни только текст, без пояснений и без кавычек.' },
      { inline_data: { mime_type: tip, data: Utilities.base64Encode(blob.getBytes()) } }
    ],
    1000, false
  );
}

/** Список моделей, доступных твоему ключу. Печатается, когда указанная не подошла. */
function spisokModeley() {
  try {
    var r = UrlFetchApp.fetch(API_BASE + '/models', {
      headers: { 'x-goog-api-key': kluchModeli() },
      muteHttpExceptions: true
    });
    if (r.getResponseCode() !== 200) {
      return ['список не отдался (' + r.getResponseCode() + ')'];
    }
    var j = JSON.parse(r.getContentText());
    return (j.models || []).filter(function (m) {
      return (m.supportedGenerationMethods || []).indexOf('generateContent') >= 0;
    }).map(function (m) {
      return String(m.name).replace('models/', '');
    }).slice(0, 25);
  } catch (err) {
    return ['ошибка: ' + err.message];
  }
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

/**
 * Будильник дёргает это каждый час. Отправляем, когда у ТЕБЯ наступило утро,
 * и только один раз в день. Час считается по TZ из настроек, а не по
 * часовому поясу проекта — поэтому его можно вообще не трогать.
 */
function utrom() {
  var moy = moyChat();
  if (!moy) return;

  var mestnoe = Utilities.formatDate(new Date(), TZ, 'yyyy-MM-dd HH:mm');
  var den = mestnoe.slice(0, 10);
  var minut = Number(mestnoe.slice(11, 13)) * 60 + Number(mestnoe.slice(14, 16));
  var nado = MORNING_HOUR * 60 + MORNING_MINUTE;

  // Окно в три часа: будильник срабатывает раз в час и должен успеть попасть.
  if (minut < nado || minut > nado + 180) return;

  var pamyat = hranilishe();
  if (pamyat.getProperty('SVODKA_DEN') === den) return;   // сегодня уже слали
  pamyat.setProperty('SVODKA_DEN', den);

  var t = svodka(true);
  if (t) send(moy, t);
}

/** Прислать сводку прямо сейчас — из меню, без оглядки на время. */
function УтренняяСводка() {
  var moy = moyChat();
  if (!moy) return;
  var t = svodka(true);   // true — пометить сегодняшние напоминания отправленными
  if (t) send(moy, t);
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
    segodnyashnie.forEach(function (r) {
      // Вчерашнее не должно выглядеть как сегодняшнее — показываем, с какого числа висит.
      var d = data10(r['когда']);
      if (d && d < segodnya) out.push('• ' + r['текст'] + ' — с ' + poRusski(d));
      else out.push('• ' + vremya(r['когда']) + r['текст']);
    });
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
    prosr.sort(poSroku).forEach(function (r) { out.push('• ' + r['текст'] + ' — было до ' + poRusski(r['срок'])); });
  }

  if (blizhnie.length) {
    out.push('');
    out.push('На этой неделе:');
    blizhnie.sort(poSroku).forEach(function (r) { out.push('• ' + r['текст'] + ' — до ' + poRusski(r['срок'])); });
  }

  if (bezSroka) {
    out.push('');
    out.push('Без срока висит: ' + bezSroka + '.');
  }

  var chistyy = out.filter(function (x) { return x !== null; });
  if (!segodnyashnie.length && !prosr.length && !blizhnie.length) {
    return poRusski(segodnya) + '. На сегодня и на неделю ничего срочного.' +
           (bezSroka ? ' Без срока висит: ' + bezSroka + '.' : '');
  }
  return poRusski(segodnya) + ', ' + denNedeli(segodnya) + '.\n\n' + chistyy.join('\n');
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
  var r = UrlFetchApp.fetch('https://api.telegram.org/bot' + tokenBota() + '/' + metod, {
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
    'https://api.telegram.org/file/bot' + tokenBota() + '/' + f.result.file_path,
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

/**
 * «2026-09-15» → «15 сентября». Другой год — «15 сентября 2027».
 * Только для сообщений: в таблице даты остаются машинными, чтобы сортировались.
 */
function poRusski(iso) {
  var d = data10(iso);
  if (!d) return String(iso || '');
  var mesyacy = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня',
                 'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря'];
  var m = parseInt(d.slice(5, 7), 10) - 1;
  if (m < 0 || m > 11) return d;
  var text = parseInt(d.slice(8, 10), 10) + ' ' + mesyacy[m];
  if (d.slice(0, 4) !== segodnyaISO().slice(0, 4)) text += ' ' + d.slice(0, 4);
  return text;
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
    .addItem('Ввести ключи', 'ВвестиКлючи')
    .addItem('Настроить', 'Настроить')
    .addItem('Проверка', 'Проверка')
    .addItem('Прислать сводку сейчас', 'УтренняяСводка')
    .addSeparator()
    .addItem('Остановить бота', 'ОстановитьБота')
    .addToUi();
}
