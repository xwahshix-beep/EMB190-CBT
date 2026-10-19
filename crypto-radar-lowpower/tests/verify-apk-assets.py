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
    research=json.loads(z.read('assets/research/momentum-summary.json'))
    assert len(research['periods'])==4
    assert research['profitability_proven'] is False
    for name in [b'MinuteStreams',b'MinuteHistory',b'DailyMomentumEngine',b'ResearchLabActivity',b'PreExplosionEngine',b'HistoricalReplay',b'ReplayActivity',b'EARLY_WATCH']:
        assert name in dex,name
    print('Verified packaged datasets, research results and native V6.3 classes')
