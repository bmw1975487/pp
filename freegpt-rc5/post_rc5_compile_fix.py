from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
activity = root / "app/src/main/java/org/torproject/android/AiAccessActivity.java"
s = activity.read_text(encoding="utf-8")
old = 'AiAccessPrefs.configure(this, currentExit());'
new = 'AiAccessPrefs.configure(this, targetPackage, currentExit());'
count = s.count(old)
if count < 1:
    raise SystemExit('RC5 2-arg AiAccessPrefs.configure call not found')
s = s.replace(old, new)
activity.write_text(s, encoding="utf-8")
print(f'RC5 configure signature fixed: {count} call(s)')
