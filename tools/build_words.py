"""
Сборка app/src/main/assets/words.json из официальных PDF Oxford 3000/5000 by CEFR level.
Использование:
  pip install pdfplumber
  python build_words.py <папка репозитория ittuann/The-Oxford-5000-Word-Lists> <папка с переводами tr/> <выходной words.json>
Шаг 1 без папки tr/ (передать "-") создаёт words_en.json + list.txt для перевода.
Формат файлов перевода tr/*.txt: строки "id|перевод".
"""
import pdfplumber, re, json, csv, sys, glob, os

SRC, TR, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
LV = {'A1', 'A2', 'B1', 'B2', 'C1'}
POS = r"(n\.|v\.|adj\.|adv\.|prep\.|conj\.|pron\.|det\.|exclam\.|number|modal v\.|auxiliary v\.|indefinite article|definite article|infinitive marker|ordinal number|linking v\.)"
PAT = re.compile(r"^(.+?)\s+((?:" + POS + r")(?:[ ,/]+(?:" + POS + r"))*)\s*$")
COLS = [0, 160, 292, 422, 10_000]

# 1. Читаем PDF по колонкам, отслеживая заголовки уровней
raw = []
for name in ['The_Oxford_3000_by_CEFR_level.pdf', 'The_Oxford_5000_by_CEFR_level.pdf']:
    level = None
    for pi, pg in enumerate(pdfplumber.open(os.path.join(SRC, name)).pages):
        ws = pg.extract_words()
        for ci in range(4):
            cw = [w for w in ws if COLS[ci] <= w['x0'] < COLS[ci + 1]
                  and w['top'] > (110 if pi == 0 else 30) and w['top'] < pg.height - 40]
            lines = {}
            for w in cw:
                k = round(w['top'])
                key = next((kk for kk in lines if abs(kk - k) <= 2), k)
                lines.setdefault(key, []).append(w)
            for k in sorted(lines):
                t = ' '.join(w['text'] for w in sorted(lines[k], key=lambda w: w['x0']))
                if t.strip() in LV:
                    level = t.strip()
                elif level:
                    raw.append((level, t))

# 2. Строки -> записи (слово, часть речи, уровень); переносы строк склеиваем
ents, buf = [], ''
for lvl, t in raw:
    t = (buf + ' ' + t).strip() if buf else t
    m = PAT.match(t)
    if m:
        ents.append((m.group(1).strip(), m.group(2).strip(), lvl)); buf = ''
    else:
        buf = t if len(t) < 60 else ''

# 3. Чистка, частотность, деление C1 -> C1/C2, нумерация уровней по 20 слов
freq = {r['word']: int(r['count']) for r in csv.DictReader(open(os.path.join(SRC, 'scripts/english-word-frequency.csv')))}
out, seen = [], set()
for w, pos, lvl in ents:
    m = re.match(r"^(.+?)(\d)?(?:\s*\((.+)\))?$", w)
    word, hint = m.group(1).strip().replace('’', "'"), (m.group(3) or '').strip()
    if (word, hint, pos) in seen: continue
    seen.add((word, hint, pos))
    f = min([freq.get(t.lower(), 0) for t in re.split(r"[ ,\-']+", word) if t] or [0])
    out.append(dict(en=word, hint=hint, pos=pos, cefr=lvl, f=f))
c1 = sorted([o for o in out if o['cefr'] == 'C1'], key=lambda o: -o['f'])
for o in c1[len(c1) // 2:]: o['cefr'] = 'C2'          # редкая половина C1 -> C2

POSMAP = {'n.': 'n', 'v.': 'v', 'adj.': 'adj', 'adv.': 'adv', 'prep.': 'prep', 'conj.': 'conj', 'pron.': 'pron',
          'det.': 'det', 'exclam.': 'exclam', 'number': 'num', 'modal v.': 'v', 'auxiliary v.': 'v', 'linking v.': 'v'}
final, idn = [], 1
for s in ['A1', 'A2', 'B1', 'B2', 'C1', 'C2']:
    for i, o in enumerate(sorted([o for o in out if o['cefr'] == s], key=lambda o: (-o['f'], o['en']))):
        final.append(dict(id=idn, en=o['en'], hint=o['hint'], pos=o['pos'], stage=s, level=i // 20 + 1,
                          pg=POSMAP.get(re.split(r'[,/]', o['pos'])[0].strip(), 'other')))
        idn += 1

if TR == '-':
    json.dump(final, open('words_en.json', 'w'), ensure_ascii=False, indent=1)
    open('list.txt', 'w').write('\n'.join(f"{o['id']} {o['en']}{' (' + o['hint'] + ')' if o['hint'] else ''} {o['pos']}" for o in final))
    print('words_en.json + list.txt готовы:', len(final)); sys.exit()

# 4. Подмешиваем переводы
tr = {}
for f in sorted(glob.glob(os.path.join(TR, '*.txt'))):
    for l in open(f, encoding='utf-8'):
        if '|' in l:
            i, t = l.strip().split('|', 1); tr[int(i)] = t.strip()
missing = [o['id'] for o in final if o['id'] not in tr]
assert not missing, f'нет перевода для id: {missing[:20]}'
for o in final: o['ru'] = tr[o['id']]
json.dump(final, open(OUT, 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
print('OK', len(final), 'слов ->', OUT)
