"""Capture actual emulator pixels and provenance; does not create a UI or edit images."""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import xml.etree.ElementTree as ET
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--adb', default='adb')
parser.add_argument('--serial', required=True)
parser.add_argument('--apk', type=Path, required=True, help='APK actually built from the recorded source')
parser.add_argument('--output', type=Path, required=True, help='Existing/new asset folder; individual files never overwritten')
parser.add_argument('--name', required=True)
parser.add_argument('--expect', required=True, help='Actual visible semantic label required before capture')
args = parser.parse_args()
repo = Path(__file__).resolve().parents[2]
def git(*items):
    return subprocess.check_output(['git', *items], cwd=repo, text=True).strip()
def adb(*items):
    return subprocess.check_output([args.adb, '-s', args.serial, *items], timeout=60)
def text(*items):
    return adb(*items).decode().strip()
assert not git('status', '--porcelain', '--', 'mobile'), 'Mobile source must be clean'
args.output.mkdir(parents=True, exist_ok=True)
image_path = args.output / (args.name + '.png')
assert not image_path.exists(), 'Existing screenshots must be preserved'
adb('shell', 'uiautomator', 'dump', '/sdcard/trailersaas-demo-ui.xml')
xml = adb('shell', 'cat', '/sdcard/trailersaas-demo-ui.xml')
nodes = ET.fromstring(xml)
labels = ' '.join(n.get('text','')+' '+n.get('content-desc','') for n in nodes.iter('node'))
assert args.expect in labels, 'Expected screen is not visible'
# The outer Flutter builder's visible banner is not included in Android's
# UIAutomator tree. Verify the isolated artifact, then visually inspect its PNG.
with zipfile.ZipFile(args.apk) as apk:
    kernel=apk.read('assets/flutter_assets/kernel_blob.bin')
    assert b'ISOLATED ANDROID REVIEW' in kernel and b'ISOLATED_REVIEW_WRITES_DISABLED' in kernel
package = 'com.trailersaas.trailersaas_mobile'
focus = [line.strip() for line in text('shell','dumpsys','window').splitlines() if 'mCurrentFocus=' in line]
assert any(package in line for line in focus), focus
installed = text('shell','pm','path',package).splitlines()[0].removeprefix('package:')
expected_hash = hashlib.sha256(args.apk.read_bytes()).hexdigest()
assert text('shell','sha256sum',installed).split()[0] == expected_hash, 'Installed APK differs from supplied build'
png = adb('exec-out','screencap','-p')
assert png.startswith(b'\x89PNG\r\n\x1a\n')
width,height = struct.unpack('>II',png[16:24])
record = {'file':image_path.name,'widgetOrState':args.expect,
          'sourceRevision':git('rev-parse','HEAD'),'mobileGitTree':git('rev-parse','HEAD:mobile'),
          'entry':'mobile/test/native_review/main.dart','apkSha256':expected_hash,
          'sha256':hashlib.sha256(png).hexdigest(), 'pixelWidth':width,'pixelHeight':height,
          'capturedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),
          'environment':'ISOLATED DEMO; actual Flutter widgets on Android emulator; synthetic reads; rejected HTTP writes; no Firebase/Backend',
          'captureMethod':'adb exec-out screencap -p; unmodified emulator PNG',
          'disclosureVerification':'Review markers in APK verified; visible banner requires PNG inspection (outer builder absent from UIAutomator tree)',
          'dataSource':'mobile/test/native_review/main.dart, test/session/fixtures.dart, test/yard/fixtures.dart',
          'serial':args.serial,'wmSize':text('shell','wm','size'),'wmDensity':text('shell','wm','density'),
          'fontScale':text('shell','settings','get','system','font_scale'),
          'androidApi':text('shell','getprop','ro.build.version.sdk'),
          'fingerprint':text('shell','getprop','ro.build.fingerprint'),'foreground':focus}
image_path.write_bytes(png)
(args.output / (args.name+'.json')).write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
(args.output / (args.name+'.xml')).write_bytes(xml)
print(json.dumps(record,indent=2))
