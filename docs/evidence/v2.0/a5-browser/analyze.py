#!/usr/bin/env python3
"""A5 分析：TC-59 深色主题白块扫描 + TC-64 截图逐像素 diff。
只读 shots/ 与 baseline/，输出 raw/tc-59-whiteblocks.json / raw/tc-64-diff.json。
"""
import json
import os
from PIL import Image, ImageChops

HERE = os.path.dirname(os.path.abspath(__file__))
PAGES = ['overview', 'assets', 'cases', 'runs', 'reports', 'groups', 'imports', 'test-reports']
BS = 32          # 白块判定窗口 32×32
WHITE = 245      # 近白阈值
TOL = 8          # 单通道容差，低于此视为同像素
DIFF_RATIO = 0.005


def scan_white_blocks(path):
    im = Image.open(path).convert('RGB')
    w, h = im.size
    px = im.load()
    total = 0
    blocks = []
    for y in range(0, h - BS + 1, BS):
        for x in range(0, w - BS + 1, BS):
            total += 1
            ok = True
            for yy in range(y, y + BS, 4):
                for xx in range(x, x + BS, 4):
                    r, g, b = px[xx, yy]
                    if r < WHITE or g < WHITE or b < WHITE:
                        ok = False
                        break
                if not ok:
                    break
            if ok:
                blocks.append([x, y])
    return {'size': [w, h], 'blocks': total, 'whiteBlocks': len(blocks),
            'ratioPct': round(len(blocks) / total * 100, 3) if total else 0,
            'samples': blocks[:5]}


def pixel_diff(a, b):
    im1 = Image.open(a).convert('RGB')
    im2 = Image.open(b).convert('RGB')
    if im1.size != im2.size:
        return {'size': [im1.size, im2.size], 'changedPixels': None, 'ratioPct': None,
                'bbox': None, 'note': '尺寸不同，无法逐像素比对'}
    d = ImageChops.difference(im1, im2)
    lut = [0] * 256
    for i in range(256):
        lut[i] = 0 if i < TOL else 1
    mask = d.convert('L').point(lut, '1')
    # mode '1' 的 histogram 只有 2 桶：[0]=相同像素数, [1]=超过容差的像素数
    changed = mask.histogram()[1]
    w, h = im1.size
    return {'size': [w, h], 'changedPixels': changed, 'ratioPct': round(changed / (w * h) * 100, 3),
            'bbox': list(mask.getbbox()) if mask.getbbox() else None}


def main():
    out = {}
    # TC-59：深色主题下不应出现大面积纯白块
    dark = {}
    for slug in PAGES:
        p = os.path.join(HERE, 'shots', 'pages', f'{slug}-dark-1440.png')
        if os.path.exists(p):
            dark[slug] = scan_white_blocks(p)
    out['tc59'] = {'pages': dark,
                   'gapPages': [k for k, v in dark.items() if v['whiteBlocks'] > 0]}

    # TC-64：当前截图 vs v1.0 基线
    base_dir = os.path.join(HERE, 'baseline')
    cur_dir = os.path.join(HERE, 'shots', 'pages')
    pairs = {}
    for theme in ['light', 'dark']:
        for slug in PAGES:
            name = f'{slug}-{theme}-1440.png'
            a = os.path.join(base_dir, name)
            b = os.path.join(cur_dir, f'{slug}-{theme}-1440.png')
            if not os.path.exists(a):
                pairs[name] = {'baseline': False, 'current': os.path.exists(b)}
                continue
            if not os.path.exists(b):
                pairs[name] = {'baseline': True, 'current': False}
                continue
            pairs[name] = pixel_diff(a, b)
            pairs[name]['baseline'] = True
            pairs[name]['current'] = True
            pairs[name]['verdict'] = ('无实质差异' if (pairs[name]['ratioPct'] or 0) <= DIFF_RATIO * 100
                                     else '有差异（bbox 可定位）')
    out['tc64'] = {'pairs': pairs,
                   'baselineCount': sum(1 for v in pairs.values() if v.get('baseline')),
                   'currentCount': sum(1 for v in pairs.values() if v.get('current')),
                   'changedPairs': [k for k, v in pairs.items()
                                    if (v.get('ratioPct') or 0) > DIFF_RATIO * 100]}

    os.makedirs(os.path.join(HERE, 'raw'), exist_ok=True)
    with open(os.path.join(HERE, 'raw', 'tc-64-diff.json'), 'w') as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print(json.dumps({'tc59_gapPages': out['tc59']['gapPages'],
                      'tc64_baseline': out['tc64']['baselineCount'],
                      'tc64_current': out['tc64']['currentCount'],
                      'tc64_changed': len(out['tc64']['changedPairs'])}, ensure_ascii=False))


if __name__ == '__main__':
    main()
