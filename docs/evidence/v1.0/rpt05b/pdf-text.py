"""从导出的 PDF 抽文本：pypdf 逐页抽取，用于 metadata 文案逐字比对。"""
import sys
from pypdf import PdfReader

reader = PdfReader(sys.argv[1])
texts = [(p.extract_text() or "") for p in reader.pages]
print("pages=%d" % len(texts))
for i, t in enumerate(texts, 1):
    print("---- page %d ----" % i)
    print(t)
