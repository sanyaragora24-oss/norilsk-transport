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

const s = svodka();
console.log('\n--- СВОДКА ---\n' + s + '\n--------------\n');

const must = [
  ['дата и день', '2026-09-08, вторник'],
  ['сегодняшнее напоминание со временем', '18:00 — родительское собрание'],
  ['просроченное напоминание подтянулось', 'забрать посылку'],
  ['просроченное дело', 'поменять резину — было до 2026-09-05'],
  ['ближайшее дело', 'оплатить страховку — до 2026-09-15'],
  ['без срока', 'Без срока висит: 1.']
];
must.forEach(([n, frag]) => eq('сводка: ' + n, s.includes(frag), true));
eq('сводка: сделанное не попало', s.includes('сдать отчёт'), false);
eq('сводка: дальнее не попало', s.includes('купить билеты'), false);

// отправленные напоминания помечены
const nap = sheets.find(x => x.name === 'Напоминания');
eq('напоминания помечены отправленными', nap.rows.map(r => r[3]), ['да','нет','да']);
eq('повторный запуск не дублирует', svodka().includes('родительское собрание'), false);

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

// --- Разбор команд ---
eq('команда /id', komanda('/id'), '/id');
eq('команда с именем бота', komanda('/id@moy_bot'), '/id');
eq('команда в верхнем регистре', komanda('/Start'), '/start');
eq('неизвестная команда не путается с /id', komanda('/idea') === '/id', false);
eq('обычный текст не команда', komanda('надо купить хлеб'), '');
eq('команда с текстом не команда', komanda('/idea купить книгу'), '');

// --- Пустая таблица ---
sheets.forEach(x => { x.rows = []; });
const pusto = svodka();
eq('пустая таблица не падает', pusto.includes('ничего срочного'), true);

console.log(fails ? `\nПРОВАЛОВ: ${fails}` : '\nВСЕ ПРОВЕРКИ ПРОШЛИ');
process.exit(fails ? 1 : 0);
