// Офлайн-проверки узла точных напоминаний. Запуск: node bot/test/napominaniya-fix.test.js
const fs = require('fs');
const path = require('path');
const source = fs.readFileSync(path.join(__dirname, '..', 'UzelokReminders.gs'), 'utf8');
const rows = [
  ['2026-11-16 15:30','одноразовое','нет','нет'],
  ['2026-11-16 15:30','повтор','каждые 10 минут','нет'],
  ['2026-11-16 09:00','старое','нет','нет'],
  ['2026-11-16 16:00','будущее','нет','нет'],
  ['2026-11-16 15:31','непонятный','каждую пятницу','нет'],
  ['2026-11-16 15:31','сбой Telegram','нет','нет']
];
const logs = [], props = {BOT_TOKEN:'mock_token',MY_CHAT_ID:'123'};
const triggers = [];
let fail = true, sent = 0;
const sheet = {
  getLastRow: () => rows.length + 1,
  getRange: (r,c,count) => ({
    getDisplayValues: () => r === 1
      ? [['когда','текст','повтор','отправлено']]
      : rows.slice(r-2,r-2+count).map(row=>row.slice(c-1,c+3)),
    setValue: value => {rows[r-2][c-1] = value;}
  }),
  appendRow: row => rows.push(row)
};
const ps = {getScriptProperties:()=>({
  getProperty: k=>props[k],
  setProperty: (k,v)=>{props[k]=v;}
})};
const ss = {getActiveSpreadsheet:()=>({
  getSheetByName:n=>n==='Напоминания'?sheet:null,
  getSpreadsheetTimeZone:()=> 'Asia/Yekaterinburg'
})};
const scr = {
  getProjectTriggers:()=>triggers,
  newTrigger:fn=>({
    timeBased(){return this}, everyMinutes(){return this},
    create(){triggers.push({getHandlerFunction:()=>fn})}
  }),
  deleteTrigger:t=>{let i=triggers.indexOf(t);if(i>=0)triggers.splice(i,1)}
};
const util = {formatDate:()=> '2026-11-16 15:32'};
const lock = {getScriptLock:()=>({tryLock:()=>true,releaseLock:()=>{}})};
const http = {fetch:(url,p)=> {
  if (fail && p.payload.text.indexOf('сбой Telegram')>=0) return {
    getResponseCode:()=>500,
    getContentText:()=>JSON.stringify({ok:false,description:'mock failure'})
  };
  sent++;
  return {getResponseCode:()=>200,getContentText:()=>JSON.stringify({ok:true})};
}};
const logger = {log:s=>logs.push(s)};
const load = new Function('PropertiesService','SpreadsheetApp','ScriptApp',
  'Utilities','LockService','UrlFetchApp','Logger', source + '\nreturn {' +
  'UzelokReminderTick,UzelokInstallReminders,UzelokReminderParse_,UzelokReminderRepeat_' +
  '};');
const api = load(ps,ss,scr,util,lock,http,logger);
let checks=0, failed=0;
function test(name, value) {
  checks++;
  if (!value) {failed++;console.error('FAIL',name);}
  else console.log('PASS',name);
}
test('ISO datetime',api.UzelokReminderParse_('2026-11-16 15:30')!==null);
test('date only is not timed',api.UzelokReminderParse_('2026-11-16')===null);
test('bad date rejected',api.UzelokReminderParse_('2026-02-30 15:30')===null);
test('10 min cadence',api.UzelokReminderRepeat_('каждые 10 минут')===10);
test('19 min cadence',api.UzelokReminderRepeat_('каждые 19 минут')===19);
test('unknown recurrence safe',api.UzelokReminderRepeat_('каждую пятницу')===-1);
api.UzelokInstallReminders();api.UzelokInstallReminders();
test('one trigger only',triggers.length===1);
api.UzelokReminderTick();
test('one-time sent and acknowledged',rows[0][3]==='да');
test('recurrence advances to 15:40',rows[1][0]==='2026-11-16 15:40');
test('stale not resent',rows[2][3]==='нет');
test('future not sent',rows[3][3]==='нет');
test('unknown recurrence not sent',rows[4][3]==='нет');
test('Telegram failure not marked',rows[5][3]==='нет');
const count=sent;
api.UzelokReminderTick();
test('second tick does not duplicate',sent===count);
fail=false;
api.UzelokReminderTick();
test('retry after Telegram recovery',rows[5][3]==='да');
test('no old mass-send',!props.UZELOK_SENT_ROW_4);
console.log(checks-failed+'/'+checks+' PASS');
process.exit(failed?1:0);
