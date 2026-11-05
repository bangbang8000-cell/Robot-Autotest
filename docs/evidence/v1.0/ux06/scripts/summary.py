import json, os
p = os.path.expanduser('~/robot-autotest/docs/evidence/v1.0/ux06/report.json')
d = json.load(open(p))
print('generatedAt', d['generatedAt'], 'login', d.get('login'))
print('--- lang / h1 / title ---')
for x in d['pages']:
    print(f"{x['width']} {x['name']:<14} lang={x['lang']:<6} h1={x['h1']!r:<45} title={x['title']!r}")
print('--- overflow ---')
bad = [x for x in d['pages'] if x['overflowPx'] != 0]
print('non-zero overflow:', bad if bad else 'NONE')
print('--- print token ---')
print(json.dumps(d.get('print'), ensure_ascii=False)[:400])
print('--- density ---')
for k in ('comfortable','compact'):
    v = d['density'][k]
    print(k, 'rowH=', v['firstRowH'], 'var=', v['rowVar'], 'density=', v['density'])
