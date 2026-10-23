"""Verify committed commercial evidence against frozen application trees and hashes."""
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import zipfile

repo = Path(__file__).resolve().parents[2]
root = repo / 'demo-assets'
base = 'fa0898e12cbdc358ea7cd7119c76a1e3a11e248c'
def git(*args):
    return subprocess.check_output(['git',*args],cwd=repo,text=True).strip()
for area in ['web','mobile','backend','.github','docs/api']:
    assert not git('diff',base,'--',area), 'Out-of-scope source changes: '+area
    assert not git('status','--porcelain','--',area), 'Uncommitted source change: '+area
frames=[]
for manifest in sorted((root/'ux08').glob('web*/manifest.json')):
    data=json.loads(manifest.read_text())
    assert data['sourceRevision']==base
    assert data['webGitTree']==git('rev-parse','HEAD:web')
    assert not data['pageErrors']
    for item in data['screenshots']:
        frames.append((manifest.parent,item))
for manifest in sorted((root/'ux08/mobile').glob('*.json')):
    item=json.loads(manifest.read_text())
    if 'file' not in item: continue
    assert item['mobileGitTree']==git('rev-parse','HEAD:mobile')
    assert item['sourceRevision']==base
    frames.append((manifest.parent,item))
assert len(frames)>=30, 'Required running-app coverage is missing'
for folder,item in frames:
    data=(folder/item['file']).read_bytes()
    assert hashlib.sha256(data).hexdigest()==item['sha256'],item['file']
    width,height=struct.unpack('>II',data[16:24])
    expected=(item['viewport']['width'],item['viewport']['height']) if 'viewport' in item else (item['pixelWidth'],item['pixelHeight'])
    assert (width,height)==expected,(item['file'],width,height,expected)
    assert item['captureMethod']
    assert 'synthetic' in (item.get('authenticity','')+item.get('environment','')).lower()
assert set((root/'ux08').rglob('*.png'))=={folder/item['file'] for folder,item in frames},'Unindexed or extra PNGs'
for line in (root/'SHA256SUMS').read_text().splitlines():
    expected,relative=line.split('  ',1)
    assert hashlib.sha256((root/relative).read_bytes()).hexdigest()==expected,relative
for folder,item in frames:
    assert item['file'] in (root/'asset-index.md').read_text(encoding='utf-8')
    assert item['file'] in (root/'notes/captions.md').read_text(encoding='utf-8')
for doc in root.rglob('*.md'):
    for target in re.findall(r'\]\(([^)#]+)(?:#[^)]*)?\)',doc.read_text(encoding='utf-8')):
        if '://' not in target and not target.startswith('#'):
            assert (doc.parent/target).exists(),f'Broken link: {doc.name}: {target}'
deck=root/'ux08/presentation/trailersaas-commercial-demo.pptx'
with zipfile.ZipFile(deck) as z:
    assert z.testzip() is None
    slides=[n for n in z.namelist() if re.fullmatch(r'ppt/slides/slide\d+\.xml',n)]
    assert len(slides)==10
    assert len([n for n in z.namelist() if re.fullmatch(r'ppt/notesSlides/notesSlide\d+\.xml',n)])==10
print(json.dumps({'result':'PASS','applicationSource':base,'currentHead':git('rev-parse','HEAD'),
                  'screenshots':len(frames),'slides':len(slides),'verified':'hashes, dimensions, source-tree identity, scope, index links, PPTX CRC/notes'},indent=2))
