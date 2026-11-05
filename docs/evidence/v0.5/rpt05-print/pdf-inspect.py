#!/usr/bin/env python3
"""RPT-05 证据：对下载到的 PDF 做结构核验（不依赖任何外部 PDF 工具）。

检查项：
1. 文件头 / 字节数 / sha256（与 export_record、下载响应三处一致）
2. 页面对象数 /Type /Page
3. 内容流解压后是否含内部版红色 #c00000 -> .7529 0 0 rg
4. 文本绘制算子数量（证明是真实渲染内容而非空白页）
"""
import hashlib
import os
import re
import zlib

PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'artifacts', '07-download.pdf')
data = open(PATH, 'rb').read()
print('file        =', PATH)
print('bytes       =', len(data))
print('sha256      =', hashlib.sha256(data).hexdigest())
print('header      =', data[:8].decode('latin-1'))
print('pages       =', data.count(b'/Type /Page'))

streams = re.findall(rb'stream\r?\n(.*?)endstream', data, re.S)
parts = []
for s in streams:
    try:
        parts.append(zlib.decompress(s))
    except Exception:
        parts.append(s)
blob = b'\n'.join(parts)
print('streams     =', len(streams))
print('text ops    =', len(re.findall(rb'(?:Tj|TJ)\b', blob)))
red = re.findall(rb'\.7529\s+0\s+0\s+r[gG]', blob)
print('red_ops #c00000 =', len(red), '(内部版红字/红框)')
print('image_ops   =', len(re.findall(rb'/Im\d+ Do', blob)))
