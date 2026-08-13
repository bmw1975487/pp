from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
smart = root / "app/src/main/java/org/torproject/android/service/circumvention/SmartConnect.kt"
t = smart.read_text(encoding="utf-8")

# patch_twoscreen leaves explanatory comments between the remembered variable and
# the assignment; RC5 intentionally patches that pair as one stable marker.
needle = '            val remembered = loadLastGoodTransport(context)\n'
pos = t.find(needle)
if pos < 0:
    raise SystemExit('remembered transport variable not found')
assign = '            Prefs.transport = remembered ?: autoSuggested ?: Transport.SNOWFLAKE'
apos = t.find(assign, pos)
if apos < 0:
    # tolerate the pre-RC3 form too
    assign = '            Prefs.transport = remembered ?: Transport.SNOWFLAKE'
    apos = t.find(assign, pos)
if apos < 0:
    raise SystemExit('remembered transport assignment not found')
# Remove only comments/blank text between the two statements.
prefix_end = pos + len(needle)
between = t[prefix_end:apos]
if between.strip() and not all(line.strip().startswith('//') or not line.strip() for line in between.splitlines()):
    raise SystemExit('unexpected SmartConnect code between remembered and assignment')
t = t[:prefix_end] + t[apos:]
smart.write_text(t, encoding='utf-8')
print('RC5 SmartConnect compatibility marker normalized')
