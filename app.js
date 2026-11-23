// Oxford 5000 — веб-версия. Те же режимы и правила, что в Android-приложении.
import { STRINGS } from './strings.js';
import {
  STAGES, SET_STAGE, IDIOM_STAGE, PHRASE_STAGES, LANGS, EN_RU, RU_EN, MAX_ERRORS,
  Repo, OptionPicker, LearningSession, Marathon, tr, levelKey, posLocal, shuffle,
} from './logic.js';

// ---------- хранение прогресса (localStorage) ----------
const STORE_KEY = 'oxford5000';
const store = (() => {
  let d;
  try { d = JSON.parse(localStorage.getItem(STORE_KEY)) || {}; } catch { d = {}; }
  return Object.assign({ lang: 'ru', dark: false, darkIntroShown: false, passed: {}, marathon: {}, errors: {} }, d);
})();
function save() { try { localStorage.setItem(STORE_KEY, JSON.stringify(store)); } catch { /* приватный режим */ } }
// Прогресс отдельно для каждого языка и темы.
const slot = () => (store.dark ? 'dark' : 'light') + (store.lang === 'ru' ? '' : '_' + store.lang);
const passedSet = () => new Set(store.passed[slot()] || []);
const errorsList = () => store.errors[slot()] || [];

// ---------- тексты ----------
const langInfo = () => LANGS.find((l) => l.code === store.lang) || LANGS[0];
const S = () => STRINGS[langInfo().key];
// Тексты про озвучку для браузера (в Android-версии они про синтезатор телефона).
const TTS_WEB = {
  ru: 'В браузере нет английского голоса. На iPhone: Настройки → Универсальный доступ → Устный контент → Голоса → English. На Android: установите «Синтезатор речи Google».',
  fr: "Le navigateur n'a pas de voix anglaise. Sur iPhone : Réglages → Accessibilité → Contenu énoncé → Voix → English. Sur Android : installez « Speech Services by Google ».",
  de: 'Der Browser hat keine englische Stimme. Auf dem iPhone: Einstellungen → Bedienungshilfen → Gesprochene Inhalte → Stimmen → English. Auf Android: „Speech Services by Google“ installieren.',
  id: 'Peramban tidak punya suara bahasa Inggris. Di iPhone: Pengaturan → Aksesibilitas → Konten Lisan → Suara → English. Di Android: pasang "Speech Services by Google".',
};

// ---------- данные ----------
let repo = null, picker = null, phraseRepo = null, phrasePicker = null;
const isPhraseStage = (st) => PHRASE_STAGES.includes(st);
const repoFor = (st) => (isPhraseStage(st) ? phraseRepo : repo);
const pickerFor = (st) => (isPhraseStage(st) ? phrasePicker : picker);
const wordById = (id) => repo?.byId.get(id) || phraseRepo?.byId.get(id);
const stageLabel = (st) => (st === SET_STAGE ? S().setPhrases : st === IDIOM_STAGE ? S().idioms : st);
const isPassed = (st, lv) => passedSet().has(levelKey(st, lv));
function passedCount(st) {
  const r = repoFor(st); if (!r) return 0;
  let n = 0; for (let i = 1; i <= r.levelCount(st); i++) if (isPassed(st, i)) n++;
  return n;
}

// ---------- навигация ----------
let stack = [{ name: 'home' }];
const top = () => stack[stack.length - 1];
function push(s) { stack.push(s); history.pushState({ depth: stack.length }, ''); render(true); }
function replace(s) { stack[stack.length - 1] = s; render(true); }
// Держим историю браузера в соответствии со стеком экранов (кнопка «Назад» в браузере/жест).
let ignorePops = 0;
function syncHistory(oldLen) {
  const diff = oldLen - stack.length;
  if (diff > 0) { ignorePops++; history.go(-diff); }
  for (let k = 0; k < -diff; k++) history.pushState({}, '');
}
function popTo(pred, fallback) {
  clearAuto(); quiz = null;
  const oldLen = stack.length;
  const i = stack.map(pred).lastIndexOf(true);
  if (i >= 0) stack = stack.slice(0, i + 1);
  else stack = [stack[0], fallback];
  syncHistory(oldLen);
  render(true);
}
function back() {
  clearAuto();
  if (stack.length > 1) stack.pop();
  if (!['learnQuiz', 'marathonQuiz', 'errorsQuiz'].includes(top().name)) quiz = null;
  render(true);
}
let skipConfirm = false;
window.addEventListener('popstate', () => {
  if (ignorePops > 0) { ignorePops--; return; }
  if (top().name === 'learnQuiz' && !skipConfirm) {
    history.pushState({ depth: stack.length }, '');
    dialog = { kind: 'exit' }; render();
    return;
  }
  skipConfirm = false;
  dialog = null;
  if (stack.length > 1) back();
});

// ---------- состояние вопросов ----------
let quiz = null;      // { session | marathon | errors }
let dialog = null;    // открытое окно
let autoTimer = null;
function clearAuto() { if (autoTimer) { clearTimeout(autoTimer); autoTimer = null; } }
function scheduleNext() { clearAuto(); autoTimer = setTimeout(() => { autoTimer = null; next(); }, 700); }

// Обучение и «Выражения»
function startLevel(stage, level) {
  const r = repoFor(stage), p = pickerFor(stage);
  quiz = { kind: 'learn', stage, level, session: new LearningSession(r.levelWords(stage, level), r.reviewPool(stage, level), store.dark, p, store.lang) };
  const t = { name: 'learnQuiz', stage, level };
  if (['preview', 'learnResult'].includes(top().name)) replace(t); else push(t);
}
function recordError(id) {
  const k = slot();
  const list = store.errors[k] || [];
  if (!list.includes(id)) list.push(id);
  store.errors[k] = list; save();
}
function removeError(id) {
  const k = slot();
  store.errors[k] = (store.errors[k] || []).filter((x) => x !== id); save();
}

// Марафон
function startMarathon(stages) {
  const st = Marathon.start(stages, repo.words);
  store.marathon[slot()] = st; save();
  openMarathon(st);
}
function openMarathon(st) {
  quiz = { kind: 'marathon', state: st, after: null, q: null, answered: false, selected: -1 };
  buildMarathonQ();
  if (top().name === 'marathonResult') replace({ name: 'marathonQuiz' }); else push({ name: 'marathonQuiz' });
}
function buildMarathonQ() {
  const w = repo.byId.get(Marathon.currentId(quiz.state));
  quiz.q = picker.build(w, store.dark && Math.random() < 0.5 ? RU_EN : EN_RU, store.lang);
  quiz.answered = false; quiz.selected = -1;
}

// Ошибки
function startErrors() {
  const order = shuffle(errorsList().filter((id) => wordById(id)));
  if (!order.length) return;
  quiz = { kind: 'errors', order, pos: 0, removed: 0, q: null, answered: false, selected: -1 };
  buildErrQ();
  if (top().name === 'errorsResult') replace({ name: 'errorsQuiz' }); else push({ name: 'errorsQuiz' });
}
function buildErrQ() {
  const w = wordById(quiz.order[quiz.pos]);
  quiz.q = pickerFor(w.stage).build(w, store.dark && Math.random() < 0.5 ? RU_EN : EN_RU, store.lang);
  quiz.answered = false; quiz.selected = -1;
}

function currentQuestion() {
  if (!quiz) return null;
  if (quiz.kind === 'learn') return quiz.session.current;
  return quiz.q;
}
function answer(i) {
  if (!quiz) return;
  if (quiz.kind === 'learn') {
    const s = quiz.session;
    if (s.answered || s.status !== 'IN_PROGRESS') return;
    const ok = s.answer(i);
    if (!ok) recordError(s.current.word.id);
    render(); if (ok) scheduleNext();
  } else if (quiz.kind === 'marathon') {
    if (quiz.answered) return;
    quiz.answered = true; quiz.selected = i;
    const ok = i === quiz.q.correctIndex;
    if (!ok) recordError(quiz.q.word.id);
    quiz.after = Marathon.answer(quiz.state, ok);
    if (Marathon.finished(quiz.after)) delete store.marathon[slot()]; else store.marathon[slot()] = quiz.after;
    save(); render(); if (ok) scheduleNext();
  } else if (quiz.kind === 'errors') {
    if (quiz.answered) return;
    quiz.answered = true; quiz.selected = i;
    const ok = i === quiz.q.correctIndex;
    if (ok) { quiz.removed++; removeError(quiz.q.word.id); }
    render(); if (ok) scheduleNext();
  }
}
function next() {
  clearAuto();
  if (!quiz) return;
  if (quiz.kind === 'learn') {
    const s = quiz.session, { stage, level } = quiz;
    s.next();
    if (s.status === 'IN_PROGRESS') return render();
    if (s.status === 'PASSED') {
      const k = slot(); const set = new Set(store.passed[k] || []); set.add(levelKey(stage, level));
      store.passed[k] = [...set]; save();
    }
    quiz = null;
    replace({ name: 'learnResult', stage, level, passed: s.status === 'PASSED', errors: s.errors, wrong: s.wrongWords });
  } else if (quiz.kind === 'marathon') {
    if (!quiz.after) return;
    quiz.state = quiz.after; quiz.after = null;
    if (Marathon.finished(quiz.state)) {
      const st = quiz.state; quiz = null;
      return replace({ name: 'marathonResult', total: st.total, firstRoundErrors: st.firstRoundErrors, rounds: st.round });
    }
    buildMarathonQ(); render();
  } else if (quiz.kind === 'errors') {
    if (!quiz.answered) return;
    if (quiz.pos + 1 >= quiz.order.length) {
      const { order, removed } = quiz; quiz = null;
      return replace({ name: 'errorsResult', total: order.length, removed, left: order.length - removed });
    }
    quiz.pos++; buildErrQ(); render();
  }
}

// ---------- тема и язык ----------
function toggleDark() {
  if (!store.darkIntroShown) { dialog = { kind: 'darkIntro' }; return render(); }
  store.dark = !store.dark; save(); render();
}

// ---------- озвучка ----------
let enVoice = null;
function pickVoice() {
  const vs = window.speechSynthesis ? speechSynthesis.getVoices() : [];
  enVoice = vs.find((v) => v.lang === 'en-US') || vs.find((v) => v.lang?.startsWith('en')) || null;
}
if (window.speechSynthesis) { pickVoice(); speechSynthesis.onvoiceschanged = pickVoice; }
function speak(text) {
  if (!window.speechSynthesis) { dialog = { kind: 'tts' }; return render(); }
  pickVoice();
  const u = new SpeechSynthesisUtterance(text.replace(/\//g, ' or '));
  u.lang = 'en-US'; u.rate = 0.9;
  if (enVoice) u.voice = enVoice;
  else if (speechSynthesis.getVoices().length) { dialog = { kind: 'tts' }; return render(); }
  speechSynthesis.cancel(); speechSynthesis.speak(u);
}

// ---------- значки (SVG) ----------
const I = {
  back: '<svg viewBox="0 0 24 24"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>',
  sun: '<svg viewBox="0 0 24 24"><path d="M12 7a5 5 0 100 10 5 5 0 000-10zM2 13h2a1 1 0 000-2H2a1 1 0 000 2zm18 0h2a1 1 0 000-2h-2a1 1 0 000 2zM11 2v2a1 1 0 002 0V2a1 1 0 00-2 0zm0 18v2a1 1 0 002 0v-2a1 1 0 00-2 0zM5.99 4.58a1 1 0 00-1.41 1.41l1.06 1.06a1 1 0 001.41-1.41L5.99 4.58zm12.37 12.37a1 1 0 00-1.41 1.41l1.06 1.06a1 1 0 001.41-1.41l-1.06-1.06zm1.06-10.96a1 1 0 00-1.41-1.41l-1.06 1.06a1 1 0 001.41 1.41l1.06-1.06zM7.05 18.36a1 1 0 00-1.41-1.41l-1.06 1.06a1 1 0 001.41 1.41l1.06-1.06z"/></svg>',
  moon: '<svg viewBox="0 0 24 24"><path d="M12 3a9 9 0 109 9c0-.46-.04-.92-.1-1.36a5.39 5.39 0 01-4.4 2.26 5.4 5.4 0 01-3.14-9.8A9.4 9.4 0 0012 3z"/></svg>',
  help: '<svg viewBox="0 0 24 24"><path d="M11 18h2v-2h-2v2zm1-16a10 10 0 100 20 10 10 0 000-20zm0 18a8 8 0 110-16 8 8 0 010 16zm0-14a4 4 0 00-4 4h2a2 2 0 114 0c0 2-3 1.75-3 5h2c0-2.25 3-2.5 3-5a4 4 0 00-4-4z"/></svg>',
  heart: '<svg viewBox="0 0 24 24"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09A5.99 5.99 0 0116.5 3C19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>',
  heartO: '<svg viewBox="0 0 24 24"><path d="M16.5 3c-1.74 0-3.41.81-4.5 2.09A5.99 5.99 0 007.5 3C4.42 3 2 5.42 2 8.5c0 3.78 3.4 6.86 8.55 11.54L12 21.35l1.45-1.32C18.6 15.36 22 12.28 22 8.5 22 5.42 19.58 3 16.5 3zm-4.4 15.55l-.1.1-.1-.1C7.14 14.24 4 11.39 4 8.5 4 6.5 5.5 5 7.5 5c1.54 0 3.04.99 3.57 2.36h1.87C13.46 5.99 14.96 5 16.5 5c2 0 3.5 1.5 3.5 3.5 0 2.89-3.14 5.74-7.9 10.05z"/></svg>',
  speak: '<svg viewBox="0 0 24 24"><path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3A4.5 4.5 0 0014 7.97v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06a7 7 0 010 13.42v2.06A9 9 0 0014 3.23z"/></svg>',
  book: '<svg viewBox="0 0 24 24"><path d="M21 5c-1.11-.35-2.33-.5-3.5-.5-1.95 0-4.05.4-5.5 1.5-1.45-1.1-3.55-1.5-5.5-1.5S2.45 4.9 1 6v14.65c0 .25.25.5.5.5.1 0 .15-.05.25-.05C3.1 20.45 5.05 20 6.5 20c1.95 0 4.05.4 5.5 1.5 1.35-.85 3.8-1.5 5.5-1.5 1.65 0 3.35.3 4.75 1.05.1.05.15.05.25.05.25 0 .5-.25.5-.5V6c-.6-.45-1.25-.75-2-1zm0 13.5c-1.1-.35-2.3-.5-3.5-.5-1.7 0-4.15.65-5.5 1.5V8c1.35-.85 3.8-1.5 5.5-1.5 1.2 0 2.4.15 3.5.5v11.5z"/></svg>',
  run: '<svg viewBox="0 0 24 24"><path d="M13.49 5.48a2 2 0 100-4 2 2 0 000 4zm-3.6 13.9l1-4.4 2.1 2v6h2v-7.5l-2.1-2 .6-3A7.3 7.3 0 0019 13v-2c-1.9 0-3.5-1-4.3-2.4l-1-1.6a2.1 2.1 0 00-1.7-1c-.3 0-.5.1-.8.1L6 8.3V13h2V9.6l1.8-.7-1.6 8.1-4.9-1-.4 2 7 1.4z"/></svg>',
  quote: '<svg viewBox="0 0 24 24"><path d="M6 17h3l2-4V7H5v6h3l-2 4zm8 0h3l2-4V7h-6v6h3l-2 4z"/></svg>',
  err: '<svg viewBox="0 0 24 24"><path d="M11 15h2v2h-2v-2zm0-8h2v6h-2V7zm1-5a10 10 0 100 20 10 10 0 000-20zm0 18a8 8 0 110-16 8 8 0 010 16z"/></svg>',
  check: '<svg viewBox="0 0 24 24"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/></svg>',
};
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const nl2br = (s) => esc(s).replace(/\n/g, '<br>');

// ---------- отрисовка ----------
const app = document.getElementById('app');

function header({ title = '', left = 'back', right = '', theme = true }) {
  const l = left === 'flag'
    ? `<button class="icon flag" data-a="lang" aria-label="${esc(S().chooseLanguage)}">${langInfo().flag}</button>`
    : left === 'back' ? `<button class="icon" data-a="back" aria-label="${esc(S().back)}">${I.back}</button>` : '<span class="icon"></span>';
  const t = theme ? `<button class="icon theme" data-a="theme" aria-label="${esc(S().darkModeLabel)}">${store.dark ? I.moon : I.sun}</button>` : '';
  return `<header class="bar">${l}<h1>${esc(title)}</h1><div class="right">${right}${t}</div></header>`;
}
const btn = (label, a, cls = 'primary', extra = '') => `<button class="btn ${cls}" data-a="${a}" ${extra}>${esc(label)}</button>`;
const speakBtn = (text) => `<button class="icon speak" data-a="speak" data-t="${esc(text)}" aria-label="${esc(S().speak)}">${I.speak}</button>`;
const progressBar = (f, cls = '') => `<div class="pbar ${cls}"><div style="width:${Math.round(f * 100)}%"></div></div>`;

function screenHome() {
  const s = S();
  const passed = STAGES.reduce((n, st) => n + passedCount(st), 0);
  const m = store.marathon[slot()];
  const errN = errorsList().filter((id) => wordById(id)).length;
  const phrDone = PHRASE_STAGES.reduce((n, st) => n + passedCount(st), 0);
  const phrTotal = PHRASE_STAGES.reduce((n, st) => n + (phraseRepo?.levelCount(st) || 0), 0);
  const card = (icon, title, sub, a, help) => `
    <div class="big" data-a="${a}"><div class="ic">${icon}</div>
      <div class="txt"><div class="t">${esc(title)}</div><div class="s">${esc(sub)}</div></div>
      <button class="icon helpb" data-a="help" data-h="${help}" aria-label="${esc(s.help)}">${I.help}</button></div>`;
  return header({ left: 'flag' }) + `<main class="home">
    <div class="logo">Oxford 5000</div>
    <p class="muted">${esc(store.dark ? s.subtitleDark : s.subtitleLight)}</p>
    ${card(I.book, s.learn, s.learnSub(passed, repo.totalLevels), 'go-stages', 'learn')}
    ${card(I.run, s.marathon, m ? s.marathonContinue(marathonPos(m)) : s.marathonNew, 'go-marathon', 'marathon')}
    ${card(I.quote, s.phrases, s.phrasesSub(phrDone, phrTotal), 'go-phrases', 'phrases')}
    ${card(I.err, s.errors, errN ? s.errorsSub(errN) : s.errorsNone, 'go-errors', 'errors')}
  </main><footer class="made">${esc(s.madeBy)} Maksim Kulinich</footer>`;
}
const marathonPos = (m) => (m.round === 1 ? S().posRound1(m.pos + 1, m.order.length) : S().posReview(m.round, m.pos + 1, m.order.length));

function stageCard(st, info, r) {
  const total = r.levelCount(st), done = passedCount(st), s = S();
  const badge = STAGES.includes(st) ? `<div class="badge ${done === total && total ? 'ok' : ''}">${st}</div>` : '';
  return `<div class="card click" data-a="levels" data-st="${st}">${badge}
    <div class="grow">${badge ? '' : `<div class="t2">${esc(stageLabel(st))}</div>`}
      <div class="b">${esc(info)}</div><div class="muted sm">${esc(s.passedOf(done, total))}</div>${progressBar(total ? done / total : 0, 'green')}</div></div>`;
}
function screenStages() {
  const s = S();
  return header({ title: s.learn }) + `<main class="list">${STAGES.map((st) => stageCard(st, s.stageInfo(repo.stageWords(st).length, repo.levelCount(st)), repo)).join('')}</main>`;
}
function screenPhraseGroups() {
  const s = S();
  return header({ title: s.phrases }) + `<main class="list"><p class="muted">${esc(s.phrasesChoose)}</p>
    ${PHRASE_STAGES.map((st) => stageCard(st, s.groupInfo(phraseRepo.stageWords(st).length, phraseRepo.levelCount(st)), phraseRepo)).join('')}</main>`;
}
function screenLevels({ stage }) {
  const r = repoFor(stage), n = r.levelCount(stage);
  let tiles = '';
  for (let l = 1; l <= n; l++) tiles += `<button class="tile ${isPassed(stage, l) ? 'ok' : ''}" data-a="preview" data-st="${stage}" data-l="${l}">${l}</button>`;
  return header({ title: S().stageTitle(stageLabel(stage), passedCount(stage), n) }) + `<main><div class="grid">${tiles}</div></main>`;
}
function screenPreview({ stage, level }) {
  const s = S(), r = repoFor(stage), passed = isPassed(stage, level);
  const rows = r.levelWords(stage, level).map((w) => `<div class="wrow"><div class="grow">
      <div><b class="en">${esc(w.en)}</b> ${w.pos ? `<span class="muted sm">${esc(w.pos)}</span>` : ''}</div>
      ${w.hint ? `<div class="muted sm it">(${esc(w.hint)})</div>` : ''}
      <div class="tr">${esc(tr(w, store.lang))}</div></div>${speakBtn(w.en)}</div>`).join('');
  return header({ title: s.levelTitle(stageLabel(stage), level) + (passed ? '  ✓' : '') }) +
    `<main><p class="muted">${esc(passed ? s.previewPassed(store.dark) : s.previewWords)}</p>${rows}</main>
     <div class="bottom two">${btn(s.back, 'back', 'outline')}${btn(s.start, 'start', 'primary', `data-st="${stage}" data-l="${level}"`)}</div>`;
}

function screenQuiz(title, closeAction) {
  const s = S(), q = currentQuestion(); if (!q) return '';
  let answered, selected, headerText, prog, lives = null, note = '';
  if (quiz.kind === 'learn') {
    const se = quiz.session;
    answered = se.answered; selected = se.selected;
    headerText = s.questionOf(se.index + 1, se.total);
    prog = (se.index + (answered ? 1 : 0)) / se.total;
    lives = Math.max(0, MAX_ERRORS - se.errors);
    if (se.doomed) note = s.levelFailedNote;
  } else if (quiz.kind === 'marathon') {
    const st = quiz.state; answered = quiz.answered; selected = quiz.selected;
    headerText = st.round === 1 ? s.questionOf(st.pos + 1, st.order.length) : s.reviewHeader(st.round, st.pos + 1, st.order.length);
    prog = (st.pos + (answered ? 1 : 0)) / st.order.length;
  } else {
    answered = quiz.answered; selected = quiz.selected;
    headerText = s.errorsHeader(quiz.pos + 1, quiz.order.length, quiz.removed);
    prog = (quiz.pos + (answered ? 1 : 0)) / quiz.order.length;
  }
  const en = q.direction === EN_RU;
  const wrongAnswered = answered && selected !== q.correctIndex;
  const hearts = lives === null ? '' : `<div class="hearts">${[0, 1, 2].map((i) => `<span class="${i < lives ? 'on' : ''}">${i < lives ? I.heart : I.heartO}</span>`).join('')}</div>`;
  const opts = q.options.map((_, i) => {
    let cls = '';
    if (answered) { if (i === q.correctIndex) cls = 'correct'; else if (i === selected) cls = 'wrong'; }
    return `<button class="opt ${cls}" data-a="answer" data-i="${i}" ${answered ? 'disabled' : ''}>${esc(q.optionText(i))}</button>`;
  }).join('');
  const pos = q.word.pos ? (en ? q.word.pos : posLocal(q.word.pos, store.lang)) : '';
  return header({ title, theme: false, right: hearts }).replace('data-a="back"', `data-a="${closeAction}"`) + `<main class="quiz">
    <div class="muted">${esc(headerText)}</div>${progressBar(prog)}
    ${note ? `<div class="note">${esc(note)}</div>` : ''}
    <div class="qcard"><div class="hint">${esc(en ? s.chooseTranslation : s.chooseEnglish)}</div>
      <div class="prompt ${q.prompt.length > 18 ? 'long' : ''}">${esc(q.prompt)}</div>
      ${pos ? `<div class="pos">${esc(pos)}</div>` : ''}
      ${en && q.word.hint ? `<div class="pos it">(${esc(q.word.hint)})</div>` : ''}
      ${en ? speakBtn(q.word.en) : ''}</div>
    <div class="opts">${opts}</div>
    ${wrongAnswered && !en ? `<div class="fix">${esc(s.correctIs)}: <b>${esc(q.word.en)}</b>${q.word.pos ? ' — ' + esc(q.word.pos) : ''} ${speakBtn(q.word.en)}</div>` : ''}
  </main>${wrongAnswered ? `<div class="bottom">${btn(s.next, 'next')}</div>` : ''}`;
}

function screenLearnResult(r) {
  const s = S(), nxt = repoFor(r.stage).nextLevel(r.stage, r.level);
  return header({ title: s.levelTitle(stageLabel(r.stage), r.level) }).replace('data-a="back"', 'data-a="to-levels"') + `<main class="center">
    <div class="emoji">${r.passed ? '🎉' : '😕'}</div><h2>${esc(r.passed ? s.levelPassed : s.levelFailed)}</h2>
    <p class="muted">${esc(s.errorsCount(r.errors))}</p>
    ${r.wrong.length ? `<div class="card col"><b>${esc(s.wrongWordsTitle)}</b>${r.wrong.map((w) => `<div>${esc(w.en)} — ${esc(tr(w, store.lang))}</div>`).join('')}</div>` : ''}
    ${r.passed && nxt ? btn(s.nextLevel, 'next-level') : ''}
    ${r.passed ? btn(s.playAgain, 'again', 'outline') : btn(s.tryAgain, 'again')}
    ${btn(s.toLevels, 'to-levels', 'outline')}</main>`;
}

let marathonSel = new Set();
function screenMarathon() {
  const s = S(), saved = store.marathon[slot()];
  let body = `<p class="muted">${esc(s.marathonDesc + (store.dark ? s.marathonDarkNote : ''))}</p>`;
  if (saved) {
    body += `<div class="card col accent"><div class="t2">${esc(s.savedMarathon)}</div><div>${esc(s.stagesLabel(saved.stages.join(', ')))}</div>
      <div>${esc(marathonPos(saved).replace(/^./, (c) => c.toUpperCase()))}</div>${btn(s.continueBtn, 'm-continue')}${btn(s.resetProgress, 'm-reset', 'outline')}</div>`;
  } else {
    const count = [...marathonSel].reduce((n, st) => n + repo.stageWords(st).length, 0);
    body += `<b>${esc(s.chooseStages)}</b><div class="chips">${STAGES.map((st) => `<button class="chip ${marathonSel.has(st) ? 'on' : ''}" data-a="m-chip" data-st="${st}">${marathonSel.has(st) ? '✓ ' : ''}${st} · ${repo.stageWords(st).length}</button>`).join('')}</div>
      <p class="muted">${esc(s.wordsInMarathon(count))}</p>${btn(s.start, 'm-start', 'primary', marathonSel.size ? '' : 'disabled')}`;
  }
  return header({ title: s.marathon }) + `<main class="col gap">${body}</main>`;
}
function screenMarathonResult(r) {
  const s = S();
  return header({ title: s.marathon }).replace('data-a="back"', 'data-a="home"') + `<main class="center"><div class="emoji">🏁</div>
    <h2>${esc(s.marathonDone)}</h2><p>${esc(s.wordsDone(r.total))}</p><p>${esc(s.firstRoundErrors(r.firstRoundErrors))}</p>
    <p class="muted">${esc(s.reviewRounds(r.rounds - 1))}</p>${btn(s.restart, 'm-setup')}${btn(s.home, 'home', 'outline')}</main>`;
}
function screenErrors() {
  const s = S();
  const words = errorsList().map(wordById).filter(Boolean).sort((a, b) => a.id - b.id);
  const list = words.length
    ? words.map((w) => `<div class="erow"><b>${esc(w.en)}</b> ${w.pos ? `<span class="muted sm">${esc(w.pos)}</span>` : ''} <span class="tr">— ${esc(tr(w, store.lang))}</span></div>`).join('')
    : `<div class="center"><div class="emoji">🎉</div><h2>${esc(s.noErrors)}</h2></div>`;
  return header({ title: `${s.errors} · ${words.length}` }) + `<main><p class="muted">${esc(s.errorsDesc + (store.dark ? s.errorsDarkNote : ''))}</p>${list}</main>
    ${words.length ? `<div class="bottom">${btn(s.start, 'e-start')}</div>` : ''}`;
}
function screenErrorsResult(r) {
  const s = S();
  return header({ title: s.errors }).replace('data-a="back"', 'data-a="e-home"') + `<main class="center"><div class="emoji">${r.left ? '💪' : '🎉'}</div>
    <h2>${esc(r.left ? s.roundDone : s.allFixed)}</h2><p>${esc(s.removedOf(r.removed, r.total))}</p><p class="muted">${esc(s.left(r.left))}</p>
    ${r.left ? btn(s.anotherRound, 'e-start') : ''}${btn(s.home, 'home', 'outline')}</main>`;
}

function dialogHtml() {
  if (!dialog) return '';
  const s = S();
  let title = '', body = '', buttons = '';
  switch (dialog.kind) {
    case 'lang':
      title = s.chooseLanguage;
      body = LANGS.map((l) => `<button class="langrow ${l.code === store.lang ? 'on' : ''}" data-a="set-lang" data-l="${l.code}"><span class="fl">${l.flag}</span><span class="grow">${l.title}</span>${l.code === store.lang ? I.check : ''}</button>`).join('');
      buttons = btn(s.cancel, 'close', 'text');
      break;
    case 'help': {
      const map = { learn: [s.helpLearnTitle, s.helpLearn], marathon: [s.marathon, s.helpMarathon], errors: [s.errors, s.helpErrors], phrases: [s.phrases, s.helpPhrases] };
      [title, body] = map[dialog.topic]; body = `<p>${nl2br(body)}</p>`; buttons = btn(s.gotIt, 'close', 'text');
      break;
    }
    case 'darkIntro':
      title = s.darkIntroTitle; body = `<p>${nl2br(s.darkIntro)}</p>`; buttons = btn(s.next, 'dark-confirm', 'text');
      break;
    case 'exit':
      title = s.exitTitle; body = `<p>${esc(s.exitText)}</p>`; buttons = btn(s.stay, 'close', 'text') + btn(s.exitConfirm, 'exit-confirm', 'text');
      break;
    case 'reset':
      title = s.resetTitle; body = `<p>${esc(s.resetText)}</p>`; buttons = btn(s.cancel, 'close', 'text') + btn(s.resetConfirm, 'm-reset-confirm', 'text');
      break;
    case 'tts':
      title = s.ttsTitle; body = `<p>${esc(TTS_WEB[store.lang])}</p>`; buttons = btn(s.gotIt, 'close', 'text');
      break;
  }
  return `<div class="scrim" data-a="scrim"><div class="dlg" role="dialog"><h3>${esc(title)}</h3><div class="dbody">${body}</div><div class="dbtns">${buttons}</div></div></div>`;
}

function render(scrollTop = false) {
  document.documentElement.classList.toggle('dark', store.dark);
  document.documentElement.lang = store.lang;
  document.querySelector('meta[name=theme-color]')?.setAttribute('content', store.dark ? '#0F1220' : '#F7F8FC');
  if (!repo || !phraseRepo) { app.innerHTML = '<div class="loading"><div class="spinner"></div></div>'; return; }
  const sc = top(), s = S();
  let html = '';
  switch (sc.name) {
    case 'home': html = screenHome(); break;
    case 'stages': html = screenStages(); break;
    case 'phrases': html = screenPhraseGroups(); break;
    case 'levels': html = screenLevels(sc); break;
    case 'preview': html = screenPreview(sc); break;
    case 'learnQuiz': html = screenQuiz(s.levelTitle(stageLabel(sc.stage), sc.level), 'quiz-close'); break;
    case 'learnResult': html = screenLearnResult(sc); break;
    case 'marathon': html = screenMarathon(); break;
    case 'marathonQuiz': html = screenQuiz(s.marathon, 'back'); break;
    case 'marathonResult': html = screenMarathonResult(sc); break;
    case 'errors': html = screenErrors(); break;
    case 'errorsQuiz': html = screenQuiz(s.errors, 'back'); break;
    case 'errorsResult': html = screenErrorsResult(sc); break;
  }
  app.innerHTML = `<div class="screen">${html}</div>${dialogHtml()}`;
  if (scrollTop) window.scrollTo(0, 0);
}

// ---------- обработка нажатий ----------
app.addEventListener('click', (e) => {
  const el = e.target.closest('[data-a]');
  if (!el || el.disabled) return;
  const a = el.dataset.a;
  if (a === 'scrim') { if (e.target === el && dialog?.kind !== 'darkIntro') { dialog = null; render(); } return; }
  if (a === 'help' || a === 'speak') e.stopPropagation();
  const st = el.dataset.st, lv = +el.dataset.l;
  switch (a) {
    case 'back': history.back(); break;
    case 'theme': toggleDark(); break;
    case 'lang': dialog = { kind: 'lang' }; render(); break;
    case 'set-lang': store.lang = el.dataset.l; save(); dialog = null; render(); break;
    case 'close': dialog = null; render(); break;
    case 'help': dialog = { kind: 'help', topic: el.dataset.h }; render(); break;
    case 'dark-confirm': store.darkIntroShown = true; store.dark = !store.dark; save(); dialog = null; render(); break;
    case 'speak': speak(el.dataset.t); break;
    case 'go-stages': push({ name: 'stages' }); break;
    case 'go-phrases': push({ name: 'phrases' }); break;
    case 'go-marathon': marathonSel = new Set(); push({ name: 'marathon' }); break;
    case 'go-errors': push({ name: 'errors' }); break;
    case 'levels': push({ name: 'levels', stage: st }); break;
    case 'preview': push({ name: 'preview', stage: st, level: lv }); break;
    case 'start': startLevel(st, lv); break;
    case 'answer': answer(+el.dataset.i); break;
    case 'next': next(); break;
    case 'quiz-close': dialog = { kind: 'exit' }; render(); break;
    case 'exit-confirm': dialog = null; skipConfirm = true; history.back(); break;
    case 'again': { const r = top(); startLevel(r.stage, r.level); break; }
    case 'next-level': {
      const r = top(), n = repoFor(r.stage).nextLevel(r.stage, r.level); if (!n) break;
      popTo((x) => x.name === (isPhraseStage(r.stage) ? 'phrases' : 'stages'), { name: isPhraseStage(r.stage) ? 'phrases' : 'stages' });
      stack.push({ name: 'levels', stage: n[0] }, { name: 'preview', stage: n[0], level: n[1] });
      history.pushState({}, ''); history.pushState({}, ''); render(true);
      break;
    }
    case 'to-levels': { const r = top(); popTo((x) => x.name === 'levels' && x.stage === r.stage, { name: 'levels', stage: r.stage }); break; }
    case 'home': popTo((x) => x.name === 'home', { name: 'home' }); break;
    case 'm-chip': marathonSel.has(st) ? marathonSel.delete(st) : marathonSel.add(st); render(); break;
    case 'm-start': startMarathon(STAGES.filter((x) => marathonSel.has(x))); break;
    case 'm-continue': openMarathon(store.marathon[slot()]); break;
    case 'm-reset': dialog = { kind: 'reset' }; render(); break;
    case 'm-reset-confirm': delete store.marathon[slot()]; save(); dialog = null; render(); break;
    case 'm-setup': marathonSel = new Set(); popTo((x) => x.name === 'marathon', { name: 'marathon' }); break;
    case 'e-start': startErrors(); break;
    case 'e-home': popTo((x) => x.name === 'errors', { name: 'errors' }); break;
  }
});

// ---------- запуск ----------
history.replaceState({ depth: 1 }, '');
render();
Promise.all([fetch('words.json').then((r) => r.json()), fetch('phrases.json').then((r) => r.json())]).then(([w, p]) => {
  repo = new Repo(w); picker = new OptionPicker(repo.words);
  phraseRepo = new Repo(p); phrasePicker = new OptionPicker(phraseRepo.words);
  render();
});
if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => {});
