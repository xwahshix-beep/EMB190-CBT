from pathlib import Path
import shutil,re
base=Path('app/src/main/java/com/wahshi/cryptoexplosionradar')
for source in Path('v54').glob('*.java'):shutil.copyfile(source,base/source.name)
p=Path('app/build.gradle');s=p.read_text()
s=re.sub(r'versionCode\s+\d+','versionCode 58',s)
s=re.sub(r'versionName\s+"[^"]+"','versionName "5.4.2-signal-stability"',s)
p.write_text(s)
print('V5.4: closed-minute confirmation, shared anti-chase gate, independent discovery scheduling')
