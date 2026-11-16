/**
 * Узелок — независимая служба точных Telegram-напоминаний.
 * Устанавливается как НОВЫЙ файл .gs в СУЩЕСТВУЮЩИЙ Apps Script
 * таблицы «Узелок»; основной код и модель (MiniMax/Gemini) не меняются.
 *
 * 1) Выполнить UzelokInstallReminders (один раз).
 * 2) Выполнить UzelokSendReminderTest (проверка Telegram).
 * 3) Выполнить UzelokScheduleReminderTest (проверка минутного триггера).
 *
 * Использовать только в редакторе Apps Script, открытом из нужной таблицы.
 */
var UZELOK_REMINDER_LOOKBACK_MINUTES = 240;
var UZELOK_REMINDER_MAX_PER_TICK = 10;

function UzelokReminderConfig_() {
  var p = PropertiesService.getScriptProperties();
  var token = String(p.getProperty('BOT_TOKEN') || p.getProperty('TELEGRAM_BOT_TOKEN') || '').trim();
  var chat = String(p.getProperty('MY_CHAT_ID') || p.getProperty('TELEGRAM_CHAT_ID') || '').trim();
  if (!token && typeof tokenBota === 'function') token = String(tokenBota() || '').trim();
  if (!chat && typeof moyChat === 'function') chat = String(moyChat() || '').trim();
  if (!token || token.indexOf('СЮДА_') === 0 || !chat || !/^-?\d+$/.test(chat)) {
    throw new Error('Не найдены BOT_TOKEN и MY_CHAT_ID. Проверьте Script Properties текущего проекта; токены в лог не выводятся.');
  }
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  if (!ss) throw new Error('Скрипт должен быть привязан к таблице Узелок. Откройте его из Расширения → Apps Script.');
  var sh = ss.getSheetByName('Напоминания');
  if (!sh) throw new Error('Нет листа Напоминания');
  var h = sh.getRange(1, 1, 1, 4).getDisplayValues()[0].map(function (x) { return String(x).trim().toLowerCase(); });
  if (h.join('|') !== 'когда|текст|повтор|отправлено') throw new Error('Неожиданные колонки Напоминаний: ' + h.join('|'));
  return { token: token, chat: chat, sheet: sh, timezone: ss.getSpreadsheetTimeZone() || 'Asia/Yekaterinburg', props: p };
}

function UzelokInstallReminders() {
  UzelokReminderConfig_(); // Не ставим новый триггер без корректной конфигурации.
  var triggers = ScriptApp.getProjectTriggers().filter(function (t) {
    return t.getHandlerFunction() === 'UzelokReminderTick';
  });
  if (triggers.length === 0) {
    ScriptApp.newTrigger('UzelokReminderTick').timeBased().everyMinutes(1).create();
  }
  // Убираем только дубликаты СВОЕГО обработчика, чужие триггеры не трогаем.
  for (var i = 1; i < triggers.length; i++) ScriptApp.deleteTrigger(triggers[i]);
  Logger.log('Узелок: обработчик напоминаний установлен; проверка каждую минуту. Дубликатов триггера нет.');
  return 'OK: UzelokReminderTick / каждую минуту';
}

function UzelokReminderParse_(value) {
  var s = String(value || '').trim();
  var m = s.match(/^(\d{4})-(\d{1,2})-(\d{1,2})[ T](\d{1,2}):(\d{2})(?::\d{2})?$/);
  if (!m) return null;  // Дата без времени остаётся для утренней сводки.
  var y = +m[1], mo = +m[2], d = +m[3], h = +m[4], mi = +m[5];
  var millis = Date.UTC(y, mo - 1, d, h, mi);
  if (!isFinite(millis)) return null;
  var iso = new Date(millis).toISOString().slice(0, 16).replace('T', ' ');
  var expected = m[1] + '-' + ('0' + mo).slice(-2) + '-' + ('0' + d).slice(-2)
    + ' ' + ('0' + h).slice(-2) + ':' + ('0' + mi).slice(-2);
  return iso === expected ? millis : null;
}

function UzelokReminderFormat_(ms) {
  return new Date(ms).toISOString().slice(0, 16).replace('T', ' ');
}

function UzelokReminderRepeat_(raw) {
  var s = String(raw || '').trim().toLowerCase();
  if (!s || s === 'нет' || s === 'false' || s === 'не повторять') return 0;
  var m = s.match(/^каждые\s+(\d+)\s+минут[а-я]*$/);
  if (m && +m[1] >= 1 && +m[1] <= 10080) return +m[1];
  m = s.match(/^каждые\s+(\d+)\s+час[а-я]*$/);
  if (m && +m[1] >= 1 && +m[1] <= 168) return +m[1] * 60;
  if (s === 'ежедневно' || s === 'каждый день') return 1440;
  if (s === 'еженедельно' || s === 'каждую неделю') return 10080;
  return -1; // Неизвестный повтор не списываем и не рассылаем циклически.
}

function UzelokReminderSend_(conf, message) {
  var r = UrlFetchApp.fetch('https://api.telegram.org/bot' + conf.token + '/sendMessage', {
    method: 'post',
    payload: { chat_id: conf.chat, text: message, disable_web_page_preview: true },
    muteHttpExceptions: true
  });
  var j;
  try { j = JSON.parse(r.getContentText()); } catch (e) { j = null; }
  if (r.getResponseCode() !== 200 || !j || !j.ok) {
    var reason = j && j.description ? j.description : 'HTTP ' + r.getResponseCode();
    throw new Error('Telegram не подтвердил доставку: ' + reason);
  }
  return true;
}

function UzelokReminderTick() {
  var lock = LockService.getScriptLock();
  if (!lock.tryLock(1000)) return; // Не запускаем параллельную отправку.
  try {
    var conf = UzelokReminderConfig_();
    var nowText = Utilities.formatDate(new Date(), conf.timezone, 'yyyy-MM-dd HH:mm');
    var now = UzelokReminderParse_(nowText);
    var last = conf.sheet.getLastRow();
    if (last < 2) return;
    // getDisplayValues сохраняет ВРЕМЯ 15:30; getValues+старый znachenie его терял.
    var data = conf.sheet.getRange(2, 1, last - 1, 4).getDisplayValues();
    var delivered = 0, old = 0, unknown = 0;
    for (var i = 0; i < data.length && delivered < UZELOK_REMINDER_MAX_PER_TICK; i++) {
      var rowNo = i + 2, row = data[i];
      var planned = UzelokReminderParse_(row[0]);
      var txt = String(row[1] || '').trim();
      var status = String(row[3] || '').trim().toLowerCase();
      if (planned === null || !txt || status === 'да' || status === 'true' || planned > now) continue;
      var cadence = UzelokReminderRepeat_(row[2]);
      if (cadence < 0) {
        unknown++;
        Logger.log('Узелок: неизвестный формат повтора в строке ' + rowNo);
        continue;
      }
      var ageMinutes = (now - planned) / 60000;
      // Старые задачи не рассылаем пачкой после восстановления сервиса.
      if (ageMinutes > UZELOK_REMINDER_LOOKBACK_MINUTES) { old++; continue; }
      var signature = String(row[0]) + '|' + txt + '|' + String(row[2] || '');
      var key = 'UZELOK_SENT_ROW_' + rowNo;
      var already = conf.props.getProperty(key) === signature;
      try {
        if (!already) {
          var prefix = ageMinutes >= 2 ? '🔔 Напоминание (с опозданием): ' : '🔔 Напоминание: ';
          UzelokReminderSend_(conf, prefix + txt + '\nНа: ' + String(row[0]).trim());
          // Подтверждение Telegram получено: теперь сохраняем идемпотентность.
          conf.props.setProperty(key, signature);
          delivered++;
        }
        if (cadence === 0) {
          conf.sheet.getRange(rowNo, 4).setValue('да');
        } else {
          // Одна отправка вместо десятков накопившихся за время простоя.
          var steps = Math.floor((now - planned) / (cadence * 60000)) + 1;
          conf.sheet.getRange(rowNo, 1).setValue(UzelokReminderFormat_(planned + steps * cadence * 60000));
          conf.sheet.getRange(rowNo, 4).setValue('нет');
        }
      } catch (e) {
        Logger.log('Узелок: строка ' + rowNo + ', отправка/запись не завершена: ' + e.message);
      }
    }
    if (old || unknown) Logger.log('Узелок: устаревших записей >4ч: ' + old + ', непонятных повторов: ' + unknown);
  } finally {
    lock.releaseLock();
  }
}

/** Проверка доступа к Telegram; не меняет напоминания. */
function UzelokSendReminderTest() {
  var conf = UzelokReminderConfig_();
  UzelokReminderSend_(conf, '✅ Узелок: проверка Telegram успешна. Планировщик проверяется отдельно.');
  Logger.log('Узелок: Telegram подтвердил приём тестового сообщения.');
}

/** Проверка полного цикла: добавит ОДНО тестовое напоминание через 2 минуты. */
function UzelokScheduleReminderTest() {
  var conf = UzelokReminderConfig_();
  var n = UzelokReminderParse_(Utilities.formatDate(new Date(), conf.timezone, 'yyyy-MM-dd HH:mm')) + 120000;
  conf.sheet.appendRow([UzelokReminderFormat_(n), 'ТЕСТ УЗЕЛКА: минутный триггер доставил напоминание', 'нет', 'нет']);
  Logger.log('Узелок: тест создан на ' + UzelokReminderFormat_(n) + ' (' + conf.timezone + ').');
}
