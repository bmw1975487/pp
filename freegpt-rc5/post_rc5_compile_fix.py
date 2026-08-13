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

capture_marker = '            final int primaryCode = results[0].httpCode;\n            main.post(() -> {'
if capture_marker not in s:
    raise SystemExit('RC5 provider probe lambda capture marker not found')
s = s.replace(capture_marker,
              '            final int primaryCode = results[0].httpCode;\n            final int supportOkFinal = supportOk;\n            main.post(() -> {', 1)

lambda_old = '"service=" + service + " exit=" + exit + " loc=" + loc + " primaryHttp=" + primaryCode + " supportOk=" + supportOk);'
if lambda_old not in s:
    raise SystemExit('RC5 supportOk lambda reference not found')
s = s.replace(lambda_old,
              '"service=" + service + " exit=" + exit + " loc=" + loc + " primaryHttp=" + primaryCode + " supportOk=" + supportOkFinal);', 1)

activity.write_text(s, encoding="utf-8")
print(f'RC5 compile fixes applied: configure={count}, lambdaCapture=1')
