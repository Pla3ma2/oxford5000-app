"""
Сборка app/src/main/assets/phrases.json из tools/phrases.tsv (en, ru, group, fr, de, id).
group: SET — устойчивые выражения, IDIOM — идиомы.
Внутри группы порядок перемешивается один раз с фиксированным зерном, поэтому состав
уровней (по 20 выражений) всегда одинаковый. id начинаются с 100001, чтобы не пересекаться со словами.
"""
import csv, json, random, os
here = os.path.dirname(os.path.abspath(__file__))
rows = list(csv.DictReader(open(os.path.join(here, 'phrases.tsv'), encoding='utf-8'), delimiter='\t'))
out, idn = [], 100001
for g in ['SET', 'IDIOM']:
    items = [r for r in rows if r['group'] == g]
    random.Random(5000 + len(g)).shuffle(items)
    for i, r in enumerate(items):
        out.append(dict(id=idn, en=r['en'].strip(), hint='', pos='', stage=g, level=i // 20 + 1,
                        pg='idiom' if g == 'IDIOM' else 'phrase', ru=r['ru'].strip(),
                        fr=r['fr'].strip(), de=r['de'].strip(), idn=r['id'].strip()))
        idn += 1
json.dump(out, open(os.path.join(here, '..', 'app/src/main/assets/phrases.json'), 'w', encoding='utf-8'),
          ensure_ascii=False, separators=(',', ':'))
from collections import Counter
print(len(out), Counter(o['stage'] for o in out), 'пустых:', sum(1 for o in out for k in ('ru','fr','de','idn') if not o[k]))
