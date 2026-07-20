import os
import re
import codecs

project = r'D:\aillm\opencode\just-for-test\kylin-SQL-Developer'

def decode_u_sequences(line):
    results = []
    for m in re.finditer(r'\\u([0-9A-Fa-f]{4})', line):
        code = int(m.group(1), 16)
        ch = chr(code)
        results.append((m.start(), m.end(), code, ch))
    return results

files = []
for r, dirs, fs in os.walk(project):
    dirs[:] = [d for d in dirs if d != 'target']
    for f in fs:
        if not f.endswith('.java'):
            continue
        fp = os.path.join(r, f)
        with open(fp, 'rb') as fh:
            content = fh.read()
        count = 0
        i = 0
        while i < len(content) - 5:
            if content[i] == 0x5c and content[i+1] == 0x75:
                nxt = content[i+2:i+6].decode('ascii', errors='ignore')
                if all(c in '0123456789abcdefABCDEF' for c in nxt):
                    count += 1
                    i += 6
                    continue
            i += 1
        if count > 0:
            files.append(fp)

files.sort()

print('=' * 80)
print('\u5171 %d \u4e2a\u6587\u4ef6\u9700\u8981\u4fee\u6539' % len(files))
print('=' * 80)

total_lines = 0
for fp in files:
    rel = os.path.relpath(fp, project)
    with open(fp, 'r', encoding='utf-8') as fh:
        lines = fh.readlines()
    
    file_lines = []
    for idx, line in enumerate(lines):
        stripped = line.rstrip()
        seqs = decode_u_sequences(stripped)
        if not seqs:
            continue
        if '\u4e2d\u6587\u539f\u6587' in stripped:
            continue
        if re.match(r'^\s*//|^\s*\*|^\s*/\*', stripped):
            continue
        chinese_chars = []
        has_cjk = False
        for _, _, code, ch in seqs:
            if 0x4E00 <= code <= 0x9FFF or 0x3000 <= code <= 0x303F or 0xFF00 <= code <= 0xFFEF:
                chinese_chars.append(ch)
                has_cjk = True
        if has_cjk:
            file_lines.append((idx + 1, ''.join(chinese_chars)))
    
    if file_lines:
        total_lines += len(file_lines)
        print('\n[%s] (%d lines)' % (rel, len(file_lines)))
        for lineno, text in file_lines:
            print('  L%d: /* %s */' % (lineno, text))

print('\n\u603b\u5171 %d \u884c\u9700\u8981\u6dfb\u52a0\u6ce8\u91ca' % total_lines)