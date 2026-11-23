// Логика режимов — тот же алгоритм, что в Android-версии (LearningSession, OptionPicker, MarathonEngine).

export const STAGES = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'];
export const SET_STAGE = 'SET';
export const IDIOM_STAGE = 'IDIOM';
export const PHRASE_STAGES = [SET_STAGE, IDIOM_STAGE];
export const LANGS = [
  { code: 'ru', key: 'RU', flag: '🇷🇺', title: 'Русский' },
  { code: 'fr', key: 'FR', flag: '🇫🇷', title: 'Français' },
  { code: 'de', key: 'DE', flag: '🇩🇪', title: 'Deutsch' },
  { code: 'id', key: 'ID', flag: '🇮🇩', title: 'Bahasa Indonesia' },
];

export const EN_RU = 'EN_RU';
export const RU_EN = 'RU_EN';
export const MAX_ERRORS = 3;
const BASE_REVIEW = 5;
const EXTRA_PER_ERROR = 5;

// ---------- случайность ----------
export function shuffle(arr) {
  const a = arr.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}
const randItem = (a) => a[Math.floor(Math.random() * a.length)];
const coin = () => Math.random() < 0.5;

// ---------- слова ----------
export function tr(w, langCode) {
  const v = { ru: w.ru, fr: w.fr, de: w.de, id: w.idn }[langCode];
  return v && v.trim() ? v : w.ru;
}
export const isArticle = (w) => (w.pos || '').includes('article');
export const levelKey = (stage, level) => `${stage}:${level}`;

export class Repo {
  constructor(words) {
    this.words = words.slice().sort((a, b) => a.id - b.id);
    this.byId = new Map(this.words.map((w) => [w.id, w]));
    this.levels = {};
    for (const w of this.words) {
      (this.levels[w.stage] ??= {});
      (this.levels[w.stage][w.level] ??= []).push(w);
    }
  }
  levelCount(stage) { return Object.keys(this.levels[stage] || {}).length; }
  levelWords(stage, level) { return (this.levels[stage] || {})[level] || []; }
  stageWords(stage) { return this.words.filter((w) => w.stage === stage); }
  reviewPool(stage, level) {
    const lw = this.levelWords(stage, level);
    if (!lw.length) return [];
    const firstId = Math.min(...lw.map((w) => w.id));
    return STAGES.includes(stage)
      ? this.words.filter((w) => w.id < firstId)
      : this.words.filter((w) => w.stage === stage && w.id < firstId);
  }
  nextLevel(stage, level) {
    if (level < this.levelCount(stage)) return [stage, level + 1];
    const i = STAGES.indexOf(stage);
    return i >= 0 && i < STAGES.length - 1 ? [STAGES[i + 1], 1] : null;
  }
  get totalLevels() { return STAGES.reduce((s, st) => s + this.levelCount(st), 0); }
}

// ---------- вопрос и варианты ----------
export class OptionPicker {
  constructor(all) {
    this.all = all;
    this.byStagePg = new Map();
    this.byPg = new Map();
    for (const w of all) {
      const k = w.stage + '|' + w.pg;
      if (!this.byStagePg.has(k)) this.byStagePg.set(k, []);
      this.byStagePg.get(k).push(w);
      if (!this.byPg.has(w.pg)) this.byPg.set(w.pg, []);
      this.byPg.get(w.pg).push(w);
    }
  }
  build(word, direction, lang) {
    const dir = isArticle(word) ? EN_RU : direction; // артикли всегда с английского
    const text = (w) => (dir === EN_RU ? tr(w, lang) : w.en).trim().toLowerCase();
    const used = new Set([text(word)]);
    const picked = [];
    const tiers = [this.byStagePg.get(word.stage + '|' + word.pg) || [], this.byPg.get(word.pg) || [], this.all];
    for (const tier of tiers) {
      if (picked.length >= 3) break;
      for (const c of shuffle(tier)) {
        if (picked.length >= 3) break;
        if (c.id === word.id) continue;
        const t = text(c);
        if (used.has(t)) continue;
        used.add(t);
        picked.push(c);
      }
    }
    const options = shuffle([...picked, word]);
    return {
      word, direction: dir, options, lang,
      correctIndex: options.findIndex((o) => o.id === word.id),
      optionText: (i) => (dir === EN_RU ? tr(options[i], lang) : options[i].en),
      prompt: dir === EN_RU ? word.en : tr(word, lang),
    };
  }
}

// ---------- прохождение уровня ----------
export class LearningSession {
  constructor(levelWords, reviewPool, mixed, picker, lang) {
    Object.assign(this, { levelWords, reviewPool, mixed, picker, lang });
    this.queue = [];
    this.index = 0;
    this.errors = 0;
    this.status = 'IN_PROGRESS';
    this.answered = false;
    this.selected = -1;
    this.wrongWords = [];
    this.usedIds = new Set();

    const base = shuffle([...levelWords, ...this.pickReview(BASE_REVIEW)]);
    const mixable = base.filter((w) => !isArticle(w)).length;
    let dirs;
    if (mixed) {
      const half = Math.floor(mixable / 2);
      const a = coin() ? mixable - half : half;
      dirs = shuffle([...Array(a).fill(EN_RU), ...Array(mixable - a).fill(RU_EN)]);
    } else dirs = Array(mixable).fill(EN_RU);
    for (const w of base) {
      const d = isArticle(w) ? EN_RU : dirs.shift();
      this.queue.push(picker.build(w, d, lang));
      this.usedIds.add(w.id);
    }
  }
  get current() { return this.queue[this.index]; }
  get total() { return this.queue.length; }
  get doomed() { return this.errors >= MAX_ERRORS; }

  answer(i) {
    if (this.status !== 'IN_PROGRESS' || this.answered) return false;
    this.answered = true;
    this.selected = i;
    const q = this.current;
    const correct = i === q.correctIndex;
    if (!correct) {
      this.errors++;
      if (!this.wrongWords.some((w) => w.id === q.word.id)) this.wrongWords.push(q.word);
      // 1-я и 2-я ошибка: +5 вопросов. С 3-й — уровень не пройден, но доигрывается до конца.
      if (this.errors < MAX_ERRORS) this.addExtra(EXTRA_PER_ERROR);
    }
    return correct;
  }
  next() {
    if (this.status !== 'IN_PROGRESS' || !this.answered) return;
    this.answered = false;
    this.selected = -1;
    if (this.index + 1 >= this.queue.length) this.status = this.errors >= MAX_ERRORS ? 'FAILED' : 'PASSED';
    else this.index++;
  }
  addExtra(n) {
    for (const w of this.pickReview(n)) {
      const d = this.mixed && coin() ? RU_EN : EN_RU;
      this.queue.push(this.picker.build(w, d, this.lang));
      this.usedIds.add(w.id);
    }
  }
  pickReview(n) {
    const res = shuffle(this.reviewPool.filter((w) => !this.usedIds.has(w.id))).slice(0, n);
    if (res.length < n && this.reviewPool.length) {
      const chosen = new Set(res.map((w) => w.id));
      res.push(...shuffle(this.reviewPool.filter((w) => !chosen.has(w.id))).slice(0, n - res.length));
      while (res.length < n) res.push(randItem(this.reviewPool));
    }
    while (res.length < n) res.push(...shuffle(this.levelWords).slice(0, n - res.length));
    return res;
  }
}

// ---------- марафон ----------
export const Marathon = {
  start(stages, words) {
    const ids = shuffle(words.filter((w) => stages.includes(w.stage)).map((w) => w.id));
    return { stages, order: ids, pos: 0, wrong: [], round: 1, total: ids.length, firstRoundErrors: 0 };
  },
  finished: (st) => st.pos >= st.order.length && st.wrong.length === 0,
  currentId: (st) => st.order[st.pos],
  answer(st, correct) {
    const id = st.order[st.pos];
    if (id === undefined) return st;
    const wrong = correct ? st.wrong : [...st.wrong, id];
    const firstRoundErrors = st.firstRoundErrors + (!correct && st.round === 1 ? 1 : 0);
    const pos = st.pos + 1;
    if (pos < st.order.length) return { ...st, pos, wrong, firstRoundErrors };
    if (!wrong.length) return { ...st, pos, wrong: [], firstRoundErrors };
    return { ...st, order: shuffle(wrong), pos: 0, wrong: [], round: st.round + 1, firstRoundErrors };
  },
};

// ---------- части речи на языке интерфейса ----------
const POS_KEYS = ['modal v.', 'auxiliary v.', 'linking v.', 'indefinite article', 'definite article',
  'infinitive marker', 'ordinal number', 'n.', 'v.', 'adj.', 'adv.', 'prep.', 'conj.', 'pron.', 'det.', 'exclam.', 'number'];
const POS_NAMES = {
  'n.': ['сущ.', 'nom', 'Subst.', 'kb. benda'],
  'v.': ['гл.', 'verbe', 'Verb', 'kk.'],
  'adj.': ['прил.', 'adj.', 'Adj.', 'ks.'],
  'adv.': ['нар.', 'adv.', 'Adv.', 'kk. ket.'],
  'prep.': ['предл.', 'prép.', 'Präp.', 'kd.'],
  'conj.': ['союз', 'conj.', 'Konj.', 'kata sambung'],
  'pron.': ['мест.', 'pron.', 'Pron.', 'kg.'],
  'det.': ['опред.', 'dét.', 'Det.', 'penentu'],
  'exclam.': ['межд.', 'interj.', 'Interj.', 'kata seru'],
  number: ['числ.', 'nombre', 'Zahlw.', 'bilangan'],
  'modal v.': ['модальный гл.', 'verbe modal', 'Modalverb', 'kk. modal'],
  'auxiliary v.': ['вспом. гл.', 'auxiliaire', 'Hilfsverb', 'kk. bantu'],
  'linking v.': ['гл.-связка', "verbe d'état", 'Kopula', 'kk. penghubung'],
  'indefinite article': ['артикль', 'article', 'Artikel', 'kata sandang'],
  'definite article': ['артикль', 'article', 'Artikel', 'kata sandang'],
  'infinitive marker': ['частица', 'particule', 'Partikel', 'partikel'],
  'ordinal number': ['порядк. числ.', 'ordinal', 'Ordinalzahl', 'bil. tingkat'],
};
const POS_RE = new RegExp(POS_KEYS.map((k) => k.replace(/\./g, '\\.')).join('|'), 'g');
export function posLocal(pos, langCode) {
  const i = ['ru', 'fr', 'de', 'id'].indexOf(langCode);
  const parts = [...new Set((pos.match(POS_RE) || []).map((k) => POS_NAMES[k]?.[i] ?? k))];
  return parts.length ? parts.join(', ') : pos;
}
