"""从导出的 PDF 抽文本：pypdf 逐页抽取，用于补录值逐字比对。"""
import sys
from pypdf import PdfReader

texts = [(p.extract_text() or "") for p in PdfReader(sys.argv[1]).pages]
print("pages=%d" % len(texts))
for i, t in enumerate(texts, 1):
    print("---- page %d ----" % i)
    print(t)
