"""Build-time check only. The Android runtime uses native Java."""
import gzip,hashlib,json,sys,zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    manifest=json.loads(z.read('assets/replay/manifest.json'))
    for row in manifest['files']:
        path='assets/replay/'+row['symbol']+'.csv.bin'
        data=gzip.decompress(z.read(path))
        assert hashlib.sha256(data).hexdigest()==row['sha256'],path
        assert len(data.splitlines())==row['rows'],path
    dex=b''.join(z.read(p) for p in z.namelist() if p.endswith('.dex'))
    for name in [b'PreExplosionEngine',b'HistoricalReplay',b'ReplayActivity',b'EARLY_WATCH']:
        assert name in dex,name
    print('Verified all 10 packaged datasets and native V6 classes')
