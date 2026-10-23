// Optional artifact authoring tool. No product imports or dependencies are added.
// Requires @oai/artifact-tool (or an explicit ARTIFACT_TOOL_IMPORT file URL).
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
const { Presentation, PresentationFile } = await import(process.env.ARTIFACT_TOOL_IMPORT || '@oai/artifact-tool');
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const workspaceDir = path.resolve(process.env.DECK_WORKSPACE || path.join(root,'review-deck'));
const output = path.join(workspaceDir, 'output');
await fs.mkdir(output,{recursive:true});
const finalPath = path.join(output,'trailersaas-commercial-demo.pptx');
try { await fs.access(finalPath); throw Error('Use a new DECK_WORKSPACE; never overwrite a final deck'); } catch(e) { if(e.code !== 'ENOENT') throw e; }
const slides = JSON.parse(await fs.readFile(path.join(root,'notes/presentation-content.json'),'utf8'));
const p = Presentation.create({slideSize:{width:1600,height:900}});
const font = 'Arial';
function text(slide,value,x,y,w,h,size=28,color='#475569',bold=false) {
  const s=slide.shapes.add({geometry:'textbox',position:{left:x,top:y,width:w,height:h},fill:'none',line:{fill:'none',width:0}});
  s.text=value;s.text.style={typeface:font,fontSize:size,color,bold};return s;
}
async function picture(slide,file,x,y,w,h){
  slide.images.add({blob:new Uint8Array(await fs.readFile(path.join(root,'ux08',file))),contentType:'image/png',fit:'contain',alt:'Unmodified running-app screenshot: '+file,position:{left:x,top:y,width:w,height:h}});
}
for(const [index, data] of slides.entries()) {
  const s=p.slides.add();s.background.fill=index===0?'#F5F7FA':'#FFFFFF';
  text(s,'TrailerSaaS',52,28,900,44,26,'#2563EB',true);
  text(s,String(index+1).padStart(2,'0'),1488,30,60,40,22,'#475569');
  const hasImages=!!data.image||!!data.images;
  text(s,data.title,52,index===0?190:95,index===0?1420:1496,index===0?210:80,index===0?72:44,'#1E293B',true);
  if(index===0){
    text(s,data.subtitle,56,450,1400,55,32,'#0F766E',true);
    text(s,data.body[0],56,545,1320,100,32);
    text(s,data.body[1],56,735,1400,98,25);
  } else if(hasImages) {
    data.body.forEach((v,i)=>text(s,v,52,225+i*175,345,150,27));
    if(data.image) await picture(s,data.image,430,192,1118,630);
    else {await picture(s,data.images[0],485,192,450,640);await picture(s,data.images[1],1000,192,450,640);}
    text(s,data.caption,430,845,1118,48,18,'#475569');
  } else {
    data.body.forEach((v,i)=>text(s,v,56,245+i*190,1430,150,36,i===0?'#0F766E':'#475569',i===0));
  }
  s.speakerNotes.text=data.notes+'\nApplication source: fa0898e12cbdc358ea7cd7119c76a1e3a11e248c. Screenshot hashes and per-image provenance: demo-assets/asset-index.md and manifests.';
}
// Previews derive from the authored objects; actual screenshots remain unedited.
await fs.mkdir(path.join(workspaceDir,'previews'),{recursive:true});
for(const [i,s] of p.slides.items.entries()) {
  const png=await p.export({slide:s,format:'png',scale:1});
  await fs.writeFile(path.join(workspaceDir,'previews',`slide-${String(i+1).padStart(2,'0')}.png`),new Uint8Array(await png.arrayBuffer()));
}
const staging=path.join(workspaceDir,'build');await fs.mkdir(staging,{recursive:true});
const candidatePath=path.join(staging,'candidate.pptx');
await (await PresentationFile.exportPptx(p)).save(candidatePath);
// The optional skill finalizer validates structure, geometry, font and re-import.
if(!process.env.PRESENTATIONS_SKILL_DIR || !process.env.RUNTIME_PYTHON) throw Error('Set PRESENTATIONS_SKILL_DIR and RUNTIME_PYTHON for validated export');
const skill=process.env.PRESENTATIONS_SKILL_DIR;
const {finalizePresentation}=await import(pathToFileURL(path.join(skill,'container_tools/artifact_tool_utils.mjs')).href);
const receipt=await finalizePresentation({workspaceDir,candidatePath,finalPath,
  pythonExecutable:process.env.RUNTIME_PYTHON,
  integrityValidatorPath:path.join(skill,'container_tools/inspect_presentation_package_integrity.py'),
  layoutValidatorPath:path.join(skill,'container_tools/inspect_presentation_layout_geometry.py'),
  layoutArgs:['--expected-slide-size-emu','15240000,8572500','--validate-bullet-geometry','--validate-heading-fit'],
  explicitTotalSlideCount:slides.length,requiredNativeTableOwnerSlides:[],requiredNativeChartOwnerSlides:[],
  fontPolicy:{basis:'design',families:[font]},verifyArtifactToolImport:true,
  receiptPath:path.join(staging,'validation.json')});
console.log(JSON.stringify(receipt,null,2));
