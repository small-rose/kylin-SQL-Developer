import os
import re
import sys

project = r'D:\aillm\opencode\just-for-test\kylin-SQL-Developer'

# Use sys.stdout with utf-8 encoding
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

def decode_u_sequences(line):
    results = []
    for m in re.finditer(r'\\u([0-9A-Fa-f]{4})', line):
        code = int(m.group(1), 16)
        ch = chr(code)
        results.append((m.start(), m.end(), code, ch))
    return results

def collect_files():
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
    return files

def process_file(fp):
    with open(fp, 'r', encoding='utf-8') as fh:
        content = fh.read()
    lines = content.split('\n')
    modified = []
    changes = []
    for idx, line in enumerate(lines):
        stripped = line.rstrip()
        if not stripped:
            modified.append(line)
            continue
        seqs = decode_u_sequences(stripped)
        if not seqs:
            modified.append(line)
            continue
        # Skip if already annotated
        if u'/* \u4e2d\u6587\u539f\u6587 */' in stripped:
            modified.append(line)
            continue
        # Skip comment-only lines
        if re.match(r'^\s*//|^\s*\*|^\s*/\*', stripped):
            modified.append(line)
            continue
        chinese_chars = []
        has_cjk = False
        for _, _, code, ch in seqs:
            if 0x4E00 <= code <= 0x9FFF or 0x3000 <= code <= 0x303F or 0xFF00 <= code <= 0xFFEF:
                chinese_chars.append(ch)
                has_cjk = True
        if has_cjk:
            chinese_text = ''.join(chinese_chars)
            new_line = stripped + u' /* ' + chinese_text + u' */'
            # Preserve original indentation
            modified.append(new_line)
            changes.append((idx + 1, chinese_text))
        else:
            modified.append(line)
    if changes:
        new_content = '\n'.join(modified)
        with open(fp, 'w', encoding='utf-8') as fh:
            fh.write(new_content)
        return True, changes
    return False, []

files = collect_files()
print(f'Total files to process: {len(files)}')
print()

total_modified_lines = 0
for fp in files:
    rel = os.path.relpath(fp, project)
    ok, changes = process_file(fp)
    if ok:
        total_modified_lines += len(changes)
        print(f'[OK] {rel} ({len(changes)} lines modified)')
    else:
        print(f'[-] {rel} (no changes)')

print()
print(f'Done! Total {total_modified_lines} lines annotated across {len(files)} files.')