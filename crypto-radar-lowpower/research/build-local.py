"""Reproducible local research APK build when Gradle dependency resolution is unavailable.
Arguments: runtime directory, output APK. Runtime contains official Android platform/build-tools,
JDK 17 compiler and Maven OkHttp 4.12.0, Okio 3.6.0, Kotlin stdlib 1.9.10, annotations 13.0.
Signing is a local research/debug key, never a replacement for the production signing key.
"""
import pathlib,subprocess,sys,zipfile,xml.etree.ElementTree as E,os
runtime=pathlib.Path(sys.argv[1]).resolve();output=pathlib.Path(sys.argv[2]).resolve()
project=pathlib.Path(__file__).resolve().parents[1];build=runtime/'manual';sdk=runtime/'tools/android-15';android=runtime/'platform/android-35/android.jar';libs=runtime/'libs'
def run(*a):subprocess.run([str(x) for x in a],check=True,cwd=project)
for d in ['generated','classes','dex']:(build/d).mkdir(parents=True,exist_ok=True)
ns='http://schemas.android.com/apk/res/android';E.register_namespace('android',ns)
r=E.parse(project/'app/src/main/AndroidManifest.xml');r.getroot().set('package','com.wahshi.cryptoexplosionradar.research')
for el in r.iter():
 key='{'+ns+'}name'
 if el.get(key,'').startswith('.'):el.set(key,'com.wahshi.cryptoexplosionradar'+el.get(key))
r.find('application').set('{'+ns+'}label','Explosion Radar Research')
r.write(build/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
run(sdk/'aapt','package','-f','-m','--custom-package','com.wahshi.cryptoexplosionradar','-J',build/'generated','-M',build/'AndroidManifest.xml','-S',project/'app/src/main/res','-A',project/'app/src/main/assets','-I',android,'-F',build/'resources.apk','--min-sdk-version','26','--target-sdk-version','34','--version-code','77','--version-name','6.10.0-research')
(build/'generated/com/wahshi/cryptoexplosionradar/BuildConfig.java').write_text('package com.wahshi.cryptoexplosionradar; public final class BuildConfig {public static final String VERSION_NAME="6.10.0-research";}\n')
app_libs=[libs/n for n in ['okhttp-4.12.0.jar','okio-jvm-3.6.0.jar','kotlin-stdlib-1.9.10.jar','annotations-13.0.jar']]
sources=list((project/'app/src/main/java').rglob('*.java'))+list((build/'generated').rglob('*.java'))
run(runtime/'jdk/bin/javac','-source','17','-target','17','-encoding','UTF-8','-cp',os.pathsep.join(map(str,[android]+app_libs)),'-d',build/'classes',*sources)
with zipfile.ZipFile(build/'classes.jar','w',zipfile.ZIP_DEFLATED) as z:
 for f in (build/'classes').rglob('*.class'):z.write(f,f.relative_to(build/'classes'))
run('java','-cp',sdk/'lib/d8.jar','com.android.tools.r8.D8','--min-api','26','--lib',android,'--output',build/'dex',build/'classes.jar',*app_libs)
with zipfile.ZipFile(build/'resources.apk') as src,zipfile.ZipFile(build/'unsigned.apk','w',zipfile.ZIP_DEFLATED) as dst:
 for f in src.infolist():dst.writestr(f,src.read(f.filename))
 for f in (build/'dex').glob('*.dex'):dst.write(f,f.name)
run(sdk/'zipalign','-f','4',build/'unsigned.apk',build/'aligned.apk')
key=runtime/'research-debug.keystore'
if not key.exists():run('keytool','-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','androiddebugkey','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Android Debug,O=Android,C=US')
output.parent.mkdir(parents=True,exist_ok=True)
run('java','-jar',sdk/'lib/apksigner.jar','sign','--ks',key,'--ks-pass','pass:android','--key-pass','pass:android','--out',output,build/'aligned.apk')
run('java','-jar',sdk/'lib/apksigner.jar','verify','--verbose',output)
run(sys.executable,project/'tests/verify-apk-assets.py',output)
print(output)
