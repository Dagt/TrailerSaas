"""Rebuild English index/captions from actual capture manifests (no app mutations)."""
import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
captions={
 'admin-overview':'Keep Customer, facility and equipment records organized in one administrative workspace.',
 'admin-customers':'Consult a consistent Customer directory and its active/inactive record states.',
 'admin-equipment':'Use separate location, load, serviceability and designation filters; equipment rows continue below the fold.',
 'admin-settings':'Review the configured stale-observation threshold and timezone; no change was saved.',
 'admin-audit':'Identify the recorded actor, timestamp and entity for a read-only administrative change.',
 'admin-audit-changes':'Compare permitted before/after values without replacing historical facts or offering Undo.',
 'operations-dispatch':'Review pending work, active Trips and attention within explicitly bounded previews.',
 'operations-orders':'Select eligible Orders for planning while keeping Customer, references and current state visible.',
 'operations-trip':'Review expected assignments separately from physical coupling and Stop execution.',
 'operations-planning':'Start the implemented four-step Trip planner with eligible Orders; nothing has been created.',
 'operations-planning-review':'Review Orders, Stop sequence and resource plan after navigating all four stages; no creation request was submitted.',
 'operations-trailer':'Investigate an unknown current location using a clearly historical Last Known observation.',
 'operations-trailer-search':'Find an exact operational Trailer number; the result retains independent physical-state dimensions.',
 'operations-trip-timeline':'Inspect an additional page of recorded assignments and instructions in chronological order.',
 'operations-movement-history':'Review physical observations and their recorded times separately from administrative Audit.',
 'operations-alerts':'Review a synthetic warning requiring authorized follow-up; acknowledgement would not prove physical recovery.',
 'customer-dashboard':'Give an authorized Customer a bounded preview of its work and designated equipment.',
 'customer-orders':'Review Customer request states and references without exposing internal dispatch records.',
 'customer-deliveries':'Follow mapped Delivery Stop milestones; missing timestamps are not inferred completion.',
 'customer-documents':'Find synthetic accepted-evidence associations; publication alone does not establish accepted POD.',
 'customer-document-unavailable':'Inspect document metadata before any download authorization request; this initial frame does not show the later storage error.',
 'customer-download-unavailable':'Show the real unavailable-storage UI after a rejected synthetic download request; no link was issued.',
 'customer-unknown-location':'Keep unknown current location distinct from Last Known while withholding internal equipment/staff identities.',
 'operations-empty':'Explain that no Orders match the current preview instead of inventing work.',
 'customer-error':'Keep record loading and action availability errors understandable and separate.',
 'customer-access-denied':'Show denied actions and reads without displaying the requested private records.',
 'customer-loading':'Show an actual pending read without presenting old private content as current.',
 'customer-mobile':'Use the same Customer workspace on a narrow browser viewport with discoverable navigation.',
 'operations-tablet':'Keep search, filters and bounded previews usable on a tablet-sized browser viewport.',
 'road-trip':'Give a Driver assigned-Trip context and an explicit reviewed Hook entry; no QR or physical action was confirmed.',
 'road-home':'Show the actual Road workspace for the synthetic dual-role user on Android.',
 'road-sync':'Keep updates and synchronization visible; this synthetic empty queue does not prove live offline replay.',
 'yard-home':'Identify the selected Yard/Site and the Trailer review entry before a physical move.',
 'yard-review':'Review the recorded origin and selected destination before explicit physical confirmation; Not Yet dismisses and no move was confirmed.',
 'native-qr-unavailable':'Show the actual camera-unavailable response on the Android QR screen; no Trailer was identified and no operation was authorized.',
}
rows=[]
for manifest in sorted((root/'ux08').glob('web*/manifest.json')):
 data=json.loads(manifest.read_text())
 for item in data['screenshots']:
  rows.append((manifest.parent,item,'D1'))
for manifest in sorted((root/'ux08/mobile').glob('*.json')):
 item=json.loads(manifest.read_text())
 if 'file' in item: rows.append((manifest.parent,item,'D2'))
index=['# Commercial asset index','',
 'Application source: `fa0898e12cbdc358ea7cd7119c76a1e3a11e248c`. Capture dates use UTC in machine manifests; presenter review date: October 22, 2026 (America/Mexico_City).','',
 '**D1 — ISOLATED DEMO:** actual React routes, synthetic role/read fixtures, no real Firebase/Backend authorization, writes rejected. **D2 — ISOLATED DEMO:** actual Flutter widgets on AOSP Android 15/API35 x86_64 emulator, synthetic fixture context, rejected HTTP writes. No physical-device/iOS claim.','',
 'Every PNG is unmodified running-app output. These are viewport captures, not full-page images: content below the fold remains scrollable. Detail frames may be scrolled, leaving the original disclosure bar above the viewport; they retain DEMO fixture values and are labeled here and on the slides. Table columns may continue in a labeled local scroll region. No viewport edge is represented as a complete page.','',
 'Web locale en-US, timezone America/Chicago, reduced motion enabled; host-native date-picker placeholders can follow the OS locale. Android 720×1600 / 320dpi equals 360×800 logical, font scale 1.0. Exact browser version, routes, actions, trees, timestamps and SHA-256 are in each Web manifest and each Mobile JSON.','',
 '| Asset | Actual route / widget | Synthetic role | Viewport / pixels | State / authenticity |','|---|---|---|---|---|']
notes=['# Screenshot captions','',
 'Use these captions with the linked assets. All records, names, counts, accepted-document metadata and memberships are synthetic. Benefits describe supported workflow intent, not measured savings. D1/D2 are defined in the asset index.','']
for folder,item,kind in rows:
 name=Path(item['file']).stem;assert name in captions,name
 rel=(folder/item['file']).relative_to(root).as_posix()
 role=item.get('role', 'Platform scanner review (no operational authorization)' if name=='native-qr-unavailable' else ('YARD_SPOTTER' if name.startswith('yard') else 'ROAD_DRIVER')+' (synthetic dual-role fixture)')
 geometry=(f"{item['viewport']['width']}×{item['viewport']['height']} CSS px" if kind=='D1' else f"{item['pixelWidth']}×{item['pixelHeight']} px / 360×800 logical")
 target=item.get('route',item.get('widgetOrState',''))
 state=item.get('state',item.get('widgetOrState',''))
 index.append(f'| [{item["file"]}]({rel}) | `{target}` | {role} | {geometry} | {state} · {kind} |')
 notes.extend([f'## {name}', '',f'[{item["file"]}](../{rel}) — {captions[name]}', '',f'**{kind} / synthetic data.** No real authorization or server-confirmed operation is claimed. SHA-256: `{item["sha256"]}`.',''])
index.extend(['','## Reusable presentation','',
 '[10-slide editable commercial deck](ux08/presentation/trailersaas-commercial-demo.pptx). Text and speaker notes remain editable; embedded screenshots preserve their original aspect ratio. This is a presentation of evidence, not a new UI design. See the [13-minute walkthrough](notes/suggested-client-walkthrough.md).','',
 'No video is supplied. Optional recordings are omitted to avoid implying a continuous operational scenario across independent fixtures. No old screenshots are relabeled as new captures.'])
(root/'asset-index.md').write_text('\n'.join(index)+'\n',encoding='utf-8')
(root/'notes/captions.md').write_text('\n'.join(notes)+'\n',encoding='utf-8')
print('Indexed',len(rows),'screenshots')
