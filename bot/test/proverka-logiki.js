// Моки окружения Apps Script
const FIXED_NOW = new Date('2026-09-08T05:00:00Z'); // 12:00 в Красноярске, вторник

global.Utilities = {
  formatDate(d, tz, fmt) {
    const p = new Intl.DateTimeFormat('en-CA', {
      timeZone: tz, year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
    }).formatToParts(d).reduce((a, x) => (a[x.type] = x.value, a), {});
    if (fmt === 'yyyy-MM-dd') return `${p.year}-${p.month}-${p.day}`;
    return `${p.year}-${p.month}-${p.day}-${p.hour}${p.minute}${p.second}`;
  },
  base64Encode: b => Buffer.from(b).toString('base64')
};
global.Logger = { log: () => {} };
const svoystvaProekta = {};
const keshZapisi = {};
global.CacheService = {
  getScriptCache: () => ({
    get: k => (k in keshZapisi ? keshZapisi[k] : null),
    put: (k, v) => { keshZapisi[k] = v; }
  })
};
global.LockService = {
  getScriptLock: () => ({ tryLock: () => true, releaseLock: () => {} })
};
global.PropertiesService = {
  getScriptProperties: () => ({
    getProperty: k => (k in svoystvaProekta ? svoystvaProekta[k] : null),
    setProperty: (k, v) => { svoystvaProekta[k] = v; }
  })
};
global.Session = { getScriptTimeZone: () => 'Asia/Krasnoyarsk' };

class Sheet {
  constructor(name, head, rows) { this.name = name; this.head = head; this.rows = rows.map(r => r.slice()); }
  getName() { return this.name; }
  getLastRow() { return this.rows.length + 1; }
  getLastColumn() { return this.head.length; }
  getRange(r, c, nr, nc) {
    const self = this;
    return {
      getValues() {
        const out = [];
        for (let i = 0; i < nr; i++) {
          const src = (r + i === 1) ? self.head : self.rows[r + i - 2];
          out.push((src || []).slice(c - 1, c - 1 + nc));
        }
        return out;
      },
      setValue(v) { self.rows[r - 2][c - 1] = v; return this; },
      setValues(v) { self.head = v[0]; return this; },
      setFontWeight() { return this; }
    };
  }
  appendRow(row) { this.rows.push(row); }
  autoResizeColumns() {}
  setFrozenRows() {}
}

const sheets = [];
global.SpreadsheetApp = {
  getActive: () => ({
    getSheets: () => sheets,
    getSheetByName: n => sheets.find(s => s.name === n) || null
  }),
  getUi: () => { throw new Error('no ui'); }
};

// Загружаем код бота, вырезая вызовы верхнего уровня нет — их нет
const fs = require('fs');
const src = fs.readFileSync('../Kod.gs', 'utf8');
eval(src);

// Подменяем "сегодня"
const RealDate = Date;
global.Date = class extends RealDate {
  constructor(...a) { return a.length ? new RealDate(...a) : new RealDate(FIXED_NOW); }
};
global.Date.now = () => FIXED_NOW.getTime();

let fails = 0;
function eq(name, got, want) {
  const g = JSON.stringify(got), w = JSON.stringify(want);
  if (g !== w) { fails++; console.log(`ПРОВАЛ ${name}\n  получено: ${g}\n  ожидалось: ${w}`); }
  else console.log(`ok   ${name}`);
}

eq('segodnyaISO', segodnyaISO(), '2026-09-08');
eq('denNedeli', denNedeli('2026-09-08'), 'вторник');
eq('plusDney +7', plusDney('2026-09-08', 7), '2026-09-15');
eq('plusDney через месяц', plusDney('2026-08-28', 7), '2026-09-04');
eq('data10 с временем', data10('2026-09-11 18:00'), '2026-09-11');
eq('data10 мусор', data10('в пятницу'), '');
eq('vremya', vremya('2026-09-11 18:00'), '18:00 — ');
eq('vremya без времени', vremya('2026-09-11'), '');
eq('razobrat чистый', razobrat('{"ответ":"да","записи":[]}'), { ответ: 'да', записи: [] });
eq('razobrat в ```json', razobrat('```json\n{"ответ":"да","записи":[]}\n```'), { ответ: 'да', записи: [] });
eq('razobrat с болтовнёй', razobrat('Вот результат: {"ответ":"да","записи":[]} готово'), { ответ: 'да', записи: [] });
eq('razobrat мусор', razobrat('совсем не json'), null);
eq('isPlaceholder', [isPlaceholder('СЮДА_ТОКЕН_ОТ_BOTFATHER'), isPlaceholder('7712:AAF'), isPlaceholder('')], [true, false, true]);

// --- Таблицы ---
sheets.length = 0;
sheets.push(new Sheet('Дела', ['дата_создания','текст','срок','статус','источник'], [
  ['2026-09-01','оплатить страховку','2026-09-15','новое','голосовое'],
  ['2026-09-01','записаться к стоматологу','','новое','голосовое'],
  ['2026-08-20','поменять резину','2026-09-05','новое','текст'],
  ['2026-08-01','сдать отчёт','2026-09-02','сделано','текст'],
  ['2026-09-01','купить билеты','2026-10-20','новое','текст']
]));
sheets.push(new Sheet('Документы', ['дата','тип','описание','сумма','ссылка_на_файл'], []));
sheets.push(new Sheet('Покупки', ['что','где','примерная_цена','куплено'], [['молоко','Лента','120','нет']]));
sheets.push(new Sheet('Идеи', ['дата','идея','тема'], []));
sheets.push(new Sheet('Напоминания', ['когда','текст','повтор','отправлено'], [
  ['2026-09-08 18:00','родительское собрание','нет','нет'],
  ['2026-09-20','день рождения у Иры','нет','нет'],
  ['2026-09-07','забрать посылку','нет','нет']
]));
sheets.push(new Sheet('Личная память', ['факт','категория','дата_обновления'], [['машина — Камри 2019','техника','2026-08-01']]));

const s = svodka(true);
console.log('\n--- СВОДКА ---\n' + s + '\n--------------\n');

const must = [
  ['дата и день по-русски', '8 сентября, вторник'],
  ['сегодняшнее напоминание со временем', '18:00 — родительское собрание'],
  ['просроченное напоминание подтянулось', 'забрать посылку'],
  ['просроченное дело', 'поменять резину — было до 5 сентября'],
  ['ближайшее дело', 'оплатить страховку — до 15 сентября'],
  ['без срока', 'Без срока висит: 1.']
];
must.forEach(([n, frag]) => eq('сводка: ' + n, s.includes(frag), true));
eq('сводка: перенесённое напоминание помечено датой', s.includes('забрать посылку — с 7 сентября'), true);
eq('сводка: машинных дат в тексте не осталось', /\d{4}-\d{2}-\d{2}/.test(s), false);
eq('сводка: сделанное не попало', s.includes('сдать отчёт'), false);
eq('сводка: дальнее не попало', s.includes('купить билеты'), false);

// отправленные напоминания помечены
const nap = sheets.find(x => x.name === 'Напоминания');
eq('напоминания помечены отправленными', nap.rows.map(r => r[3]), ['да','нет','да']);
eq('повторный запуск не дублирует', svodka(true).includes('родительское собрание'), false);

// «/неделя» не должна съедать напоминания у утренней сводки
sheets.find(x => x.name === 'Напоминания').rows = [['2026-09-08 18:00','родительское собрание','нет','нет']];
eq('ручная сводка показывает напоминание', svodka(false).includes('родительское собрание'), true);
eq('ручная сводка не помечает отправленным',
   sheets.find(x => x.name === 'Напоминания').rows[0][3], 'нет');
eq('утренняя после ручной всё ещё покажет', svodka(true).includes('родительское собрание'), true);
eq('и вот теперь пометила', sheets.find(x => x.name === 'Напоминания').rows[0][3], 'да');

// --- Запись строки ---
eq('zapisat в Дела', zapisat('дела', { текст: 'позвонить в банк', срок: '2026-09-30' }), true);
const dela = sheets.find(x => x.name === 'Дела');
eq('строка разложилась по заголовкам', dela.rows[dela.rows.length - 1],
   ['2026-09-08','позвонить в банк','2026-09-30','новое','']);
eq('zapisat в неизвестный раздел', zapisat('Погода', { текст: 'дождь' }), false);
zapisat('Покупки', { что: 'хлеб' });
const pok = sheets.find(x => x.name === 'Покупки');
eq('умолчание для «куплено»', pok.rows[pok.rows.length - 1], ['хлеб','','','нет']);

// --- Снимок для модели ---
const sn = snimok();
eq('снимок: личная память', sn.includes('машина — Камри 2019'), true);
eq('снимок: сделанное скрыто', sn.includes('сдать отчёт'), false);
eq('снимок: купленное не показано дважды', (sn.match(/молоко/g) || []).length, 1);

// --- Даты по-русски ---
eq('poRusski обычная дата', poRusski('2026-09-15'), '15 сентября');
eq('poRusski без ведущего нуля', poRusski('2026-09-05'), '5 сентября');
eq('poRusski январь', poRusski('2026-01-01'), '1 января');
eq('poRusski декабрь', poRusski('2026-12-31'), '31 декабря');
eq('poRusski другой год — с годом', poRusski('2027-03-10'), '10 марта 2027');
eq('poRusski с временем', poRusski('2026-09-11 18:00'), '11 сентября');
eq('poRusski не дата — как есть', poRusski('в пятницу'), 'в пятницу');
eq('poRusski пусто', poRusski(''), '');

// --- Разбор команд ---
eq('команда /id', komanda('/id'), '/id');
eq('команда с именем бота', komanda('/id@moy_bot'), '/id');
eq('команда в верхнем регистре', komanda('/Start'), '/start');
eq('неизвестная команда не путается с /id', komanda('/idea') === '/id', false);
eq('обычный текст не команда', komanda('надо купить хлеб'), '');
eq('команда с текстом не команда', komanda('/idea купить книгу'), '');

// --- Пустая таблица ---
sheets.forEach(x => { x.rows = []; });
const pusto = svodka(true);
eq('пустая таблица не падает', pusto.includes('ничего срочного'), true);

// --- Откуда берутся ключи ---
eq('пока ничего не задано — пусто', [tokenBota(), kluchModeli(), moyChat()], ['', '', '']);
eq('заглушки из кода за значения не считаются', nastroyka('НЕТУ', 'СЮДА_КЛЮЧ_GEMINI'), '');
eq('настоящее значение из кода подхватывается', nastroyka('НЕТУ', 'AIzaИзКода'), 'AIzaИзКода');

svoystvaProekta['BOT_TOKEN'] = '  7712:AAF  ';
svoystvaProekta['GEMINI_KEY'] = 'AIzaИзХранилища';
svoystvaProekta['MY_CHAT_ID'] = '284736591';
eq('хранилище читается и подрезается по краям', tokenBota(), '7712:AAF');
eq('ключ из хранилища', kluchModeli(), 'AIzaИзХранилища');
eq('номер из хранилища', moyChat(), '284736591');
eq('хранилище главнее кода', nastroyka('GEMINI_KEY', 'AIzaИзКода'), 'AIzaИзХранилища');

eq('затемнение оставляет края', zatemnit('AIzaSyABCDEFGH1234'), 'AIza…1234');
eq('короткое затемняется целиком', zatemnit('abc'), '••••');
eq('затемнение пустого не падает', zatemnit(''), '••••');

// --- Обмен с Google ---
// Подменяем сеть: запоминаем, что ушло, и отдаём заготовленный ответ.
let poslednii = null, otvetSeti = null;
global.UrlFetchApp = {
  fetch(url, opts) {
    poslednii = { url, opts, body: opts && opts.payload && typeof opts.payload === 'string'
      ? JSON.parse(opts.payload) : opts && opts.payload };
    return {
      getResponseCode: () => otvetSeti.kod,
      getContentText: () => otvetSeti.telo,
      getBlob: () => null
    };
  }
};

otvetSeti = { kod: 200, telo: JSON.stringify({
  candidates: [{ content: { parts: [{ text: 'готов' }] }, finishReason: 'STOP' }] }) };
eq('gemini: читает ответ', gemini('инструкция', [{ text: 'привет' }], 30, false), 'готов');
eq('gemini: адрес с моделью', poslednii.url.endsWith('/models/' + MODEL + ':generateContent'), true);
eq('gemini: ключ уходит заголовком, не в адресе',
   [poslednii.opts.headers['x-goog-api-key'] === kluchModeli(), poslednii.url.indexOf('key=') >= 0],
   [true, false]);
eq('gemini: системная инструкция на месте', poslednii.body.system_instruction.parts[0].text, 'инструкция');
eq('gemini: без json-режима формат не требуем',
   poslednii.body.generationConfig.responseMimeType, undefined);

gemini('', [{ text: 'x' }], 50, true);
eq('gemini: json-режим требует чистый JSON',
   poslednii.body.generationConfig.responseMimeType, 'application/json');
eq('gemini: пустую инструкцию не шлём', poslednii.body.system_instruction, undefined);

otvetSeti = { kod: 200, telo: JSON.stringify({
  candidates: [{ content: { parts: [{ text: '{"ответ":' }, { text: '"да","записи":[]}' }] } }] }) };
eq('gemini: склеивает ответ из нескольких кусков',
   gemini('', [{ text: 'x' }], 50, true), '{"ответ":"да","записи":[]}');

otvetSeti = { kod: 429, telo: '{"error":{"message":"quota"}}' };
let lovil = '';
try { gemini('', [{ text: 'x' }], 10, false); } catch (e) { lovil = e.message; }
eq('gemini: код ошибки виден в сообщении', /429/.test(lovil) && /quota/.test(lovil), true);

otvetSeti = { kod: 200, telo: JSON.stringify({ promptFeedback: { blockReason: 'SAFETY' } }) };
lovil = '';
try { gemini('', [{ text: 'x' }], 10, false); } catch (e) { lovil = e.message; }
eq('gemini: отказ модели объяснён', /SAFETY/.test(lovil), true);

otvetSeti = { kod: 200, telo: JSON.stringify({
  candidates: [{ content: { parts: [] }, finishReason: 'MAX_TOKENS' }] }) };
lovil = '';
try { gemini('', [{ text: 'x' }], 10, false); } catch (e) { lovil = e.message; }
eq('gemini: обрыв ответа объяснён', /MAX_TOKENS/.test(lovil), true);

// Голосовое уходит вложением с правильным типом
skachat = () => ({
  getContentType: () => 'audio/ogg',
  getBytes: () => Buffer.from('звук')
});
otvetSeti = { kod: 200, telo: JSON.stringify({
  candidates: [{ content: { parts: [{ text: 'оплатить страховку' }] }, finishReason: 'STOP' }] }) };
eq('расшифровка: возвращает текст', rasshifrovat('file1', 'audio/ogg'), 'оплатить страховку');
const zvuk = poslednii.body.contents[0].parts.filter(p => p.inline_data)[0];
eq('расшифровка: звук ушёл вложением', !!zvuk, true);
eq('расшифровка: тип звука проставлен', zvuk.inline_data.mime_type, 'audio/ogg');
eq('расшифровка: звук закодирован', zvuk.inline_data.data, Buffer.from('звук').toString('base64'));
eq('расшифровка: тип берём из блоба, если Telegram не сказал',
   (rasshifrovat('file1', ''), poslednii.body.contents[0].parts.filter(p => p.inline_data)[0].inline_data.mime_type),
   'audio/ogg');

// Список моделей — только те, что умеют отвечать
otvetSeti = { kod: 200, telo: JSON.stringify({ models: [
  { name: 'models/gemini-2.5-flash', supportedGenerationMethods: ['generateContent'] },
  { name: 'models/text-embedding-004', supportedGenerationMethods: ['embedContent'] },
  { name: 'models/gemini-2.5-pro', supportedGenerationMethods: ['generateContent', 'countTokens'] }
] }) };
eq('список моделей: только отвечающие', spisokModeley(), ['gemini-2.5-flash', 'gemini-2.5-pro']);
otvetSeti = { kod: 403, telo: 'forbidden' };
eq('список моделей: ошибку не роняем, а показываем', spisokModeley().length, 1);

// --- Опрос Telegram ---
let ochered = [];        // что Telegram отдаст на следующий getUpdates
let razobrano = [];      // какие сообщения дошли до обработки
let poslano = [];        // что бот отправил в чат
let padatNa = null;      // на каком тексте обработка должна упасть

svoystvaProekta['MY_CHAT_ID'] = '284736591';
svoystvaProekta['BOT_TOKEN'] = '7712:AAF';
delete svoystvaProekta['TG_OFFSET'];

tg = (metod, params) => {
  if (metod === 'getUpdates') {
    const off = Number(params.offset || 0);
    const partiya = ochered.filter(u => u.update_id >= off).slice(0, params.limit || 20);
    return { ok: true, result: partiya };
  }
  if (metod === 'sendMessage') { poslano.push(params); return { ok: true }; }
  return { ok: true, result: {} };
};
obrabotat = (msg) => {
  if (padatNa && msg.text === padatNa) throw new Error('нарочно упало');
  razobrano.push(msg.text);
};

const soobshenie = (id, text, chat = 284736591) =>
  ({ update_id: id, message: { chat: { id: chat }, text: text } });

ochered = [soobshenie(10, 'первое'), soobshenie(11, 'второе')];
opros();
eq('опрос: оба сообщения разобраны', razobrano, ['первое', 'второе']);
eq('опрос: смещение сдвинулось за последнее', svoystvaProekta['TG_OFFSET'], '12');

razobrano = [];
opros();
eq('опрос: разобранное второй раз не приходит', razobrano, []);

ochered.push(soobshenie(12, 'третье'));
opros();
eq('опрос: новое сообщение подхватывается', razobrano, ['третье']);

// Упавшее сообщение не должно застревать и держать очередь
razobrano = []; poslano = []; padatNa = 'битое';
ochered.push(soobshenie(13, 'битое'));
ochered.push(soobshenie(14, 'после битого'));
opros();
eq('опрос: сообщение после упавшего разобрано', razobrano, ['после битого']);
eq('опрос: про поломку сказано в чат', poslano.length && /Не смог обработать/.test(poslano[0].text), true);
eq('опрос: смещение ушло за оба', svoystvaProekta['TG_OFFSET'], '15');
razobrano = []; padatNa = null;
opros();
eq('опрос: битое не возвращается кругами', razobrano, []);

// Чужой чат
razobrano = []; poslano = [];
ochered.push({ update_id: 15, message: { chat: { id: 999 }, text: 'чужое' } });
opros();
eq('опрос: чужое сообщение молча выброшено', [razobrano, poslano], [[], []]);

// /id отвечаем кому угодно — иначе свой номер не узнать
poslano = [];
ochered.push({ update_id: 16, message: { chat: { id: 999 }, text: '/id' } });
opros();
eq('опрос: /id отвечает и чужому', poslano.length, 1);
eq('опрос: /id называет номер чата', /999/.test(poslano[0].text), true);

// Без токена опрос не лезет в сеть
razobrano = [];
const bylToken = svoystvaProekta['BOT_TOKEN'];
delete svoystvaProekta['BOT_TOKEN'];
ochered.push(soobshenie(17, 'без токена'));
opros();
eq('опрос: без токена ничего не делает', razobrano, []);
svoystvaProekta['BOT_TOKEN'] = bylToken;

console.log(fails ? `\nПРОВАЛОВ: ${fails}` : '\nВСЕ ПРОВЕРКИ ПРОШЛИ');
process.exit(fails ? 1 : 0);
