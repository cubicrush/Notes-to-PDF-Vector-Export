import fontkit from './fontkit.js';
import {PDFDocument,PDFName,PDFNumber,PDFArray,PDFRawStream,decodePDFRawStream,rgb,pushGraphicsState,popGraphicsState,rectangle,clip,endPath,StandardFonts,moveTo,lineTo,appendBezierCurve,closePath} from './pdf-lib.js';
import {unzipSync,zlibSync,unzlibSync} from './fflate.js';
const N=s=>PDFName.of(s),hash=async b=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',b)),x=>x.toString(16).padStart(2,'0')).join('');
function decode(s){return s.dict.get(N('Filter'))?.toString()==='/FlateDecode'&&!s.dict.has(N('DecodeParms'))?unzlibSync(s.contents):decodePDFRawStream(s).decode();}
function replace(s,text){s.contents=zlibSync(new TextEncoder().encode(text),{level:4});s.dict.set(N('Filter'),N('FlateDecode'));s.dict.delete(N('DecodeParms'));s.dict.set(N('Length'),PDFNumber.of(s.contents.length));}
async function key(s){const bytes=s.dict.get(N('Filter'))?.toString()==='/DCTDecode'?s.contents:decode(s);return `${s.dict.get(N('Width'))}x${s.dict.get(N('Height'))}:${await hash(bytes)}`;}
function streams(doc,p){const c=p.node.Contents();return c instanceof PDFArray?c.asArray().map(r=>doc.context.lookup(r)):c?[c]:[];}
async function removeInlineImages(doc,keys){
 const targets=new Set();for(const [r,o] of doc.context.enumerateIndirectObjects())if(o instanceof PDFRawStream&&o.dict.get(N('Subtype'))?.toString()==='/Image'&&o.dict.get(N('ColorSpace'))?.toString()!=='/DeviceGray'&&keys.has(await key(o)))targets.add(r.toString());
 const visited=new Set();
 function edit(s,res){if(!(s instanceof PDFRawStream)||visited.has(s))return;visited.add(s);const xs=res?.lookup(N('XObject'));if(!xs)return;const remove=[];
  for(const [n,r]of xs.entries()){const o=doc.context.lookup(r);if(targets.has(r.toString()))remove.push(n.toString());else if(o instanceof PDFRawStream&&o.dict.get(N('Subtype'))?.toString()==='/Form')edit(o,o.dict.lookup(N('Resources'))||res);}
  if(remove.length){let text=new TextDecoder().decode(decode(s));for(const n of remove)text=text.replace(new RegExp(n.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')+'\\s+Do(?=\\s|$)','g'),'');replace(s,text);}
 }
 for(const p of doc.getPages())for(const s of streams(doc,p))edit(s,p.node.Resources());
}
function stripCanvas(doc,p){for(const s of streams(doc,p)){if(!(s instanceof PDFRawStream))continue;const text=new TextDecoder().decode(decode(s));
 const re=/(q [\d.eE+\-]+ 0 0 -[\d.eE+\-]+ 0 [\d.eE+\-]+ cm (?:[\d.eE+\-]+ ){1,4}(?:g|rg|k) 0 0 m [\d.eE+\-]+ 0 l [\d.eE+\-]+ [\d.eE+\-]+ l 0 [\d.eE+\-]+ l\s+h\s+)f(\s+Q)/;
 if(re.test(text)){replace(s,text.replace(re,'$1n$2'));return;}}
 throw new Error('The page background could not be identified safely.');}
export function decodeCard(bytes,obj){const d=new DataView(bytes.buffer,bytes.byteOffset,bytes.byteLength);let p=obj.offset;
 while(p+10<=obj.offset+obj.size){const size=d.getUint32(p,true);if(size<10||p+size>obj.offset+obj.size)throw new Error('Corrupt card.');if(d.getUint16(p+4,true)===13){if(d.getUint32(p+6,true)!==15)throw new Error('Unknown card format.');const mask=d.getUint16(p+13,true);let at=p+15;const int=()=>{const v=d.getInt32(at,true);at+=4;return v;};const str=()=>{const n=d.getUint16(at,true);at+=2;if(at+2*n>p+size)throw new Error('Corrupt card text.');const s=new TextDecoder('utf-16le').decode(bytes.subarray(at,at+2*n));at+=2*n;return s;};if(mask&1)int();return {bbox:obj.base.bbox,thumbnail:mask&2?int():null,body:mask&4?str():'',title:mask&8?str():'',uri:mask&16?str():''};}p+=size;}throw new Error('Missing card.');}
function roundedPath(x,y,w,h,r){
 const k=.5522847498307936*r;
 return [moveTo(x+r,y),lineTo(x+w-r,y),appendBezierCurve(x+w-r+k,y,x+w,y+r-k,x+w,y+r),lineTo(x+w,y+h-r),appendBezierCurve(x+w,y+h-r+k,x+w-r+k,y+h,x+w-r,y+h),lineTo(x+r,y+h),appendBezierCurve(x+r-k,y+h,x,y+h-r+k,x,y+h-r),lineTo(x,y+r),appendBezierCurve(x,y+r-k,x+r-k,y,x+r,y),closePath()];
}
export async function drawCard(doc,p,c,asset,scale,height,font){
 const b=c.bbox,x=b.x_min*scale,y=height-b.y_max*scale,w=(b.x_max-b.x_min)*scale,h=(b.y_max-b.y_min)*scale;
 const unit=h/208,pad=24*unit,thumb=160*unit,radius=48*unit;
 const inset=3*unit,fw=w-2*inset,fh=h-2*inset;
 const d=`M ${radius} 0 H ${fw-radius} Q ${fw} 0 ${fw} ${radius} V ${fh-radius} Q ${fw} ${fh} ${fw-radius} ${fh} H ${radius} Q 0 ${fh} 0 ${fh-radius} V ${radius} Q 0 0 ${radius} 0 Z`;
 p.drawSvgPath(d,{x:x+inset,y:y+h-inset,color:rgb(252/255,252/255,252/255),borderColor:rgb(.5,.5,.5),borderWidth:unit});
 if(c.thumbnail!==null){const bytes=asset(c.thumbnail),im=bytes[0]===255?await doc.embedJpg(bytes):await doc.embedPng(bytes);
  const ix=x+pad,iy=y+(h-thumb)/2,cover=Math.max(thumb/im.width,thumb/im.height);
  p.pushOperators(pushGraphicsState(),...roundedPath(ix,iy,thumb,thumb,radius),clip(),endPath());
  p.drawImage(im,{x:ix+(thumb-im.width*cover)/2,y:iy+(thumb-im.height*cover)/2,width:im.width*cover,height:im.height*cover});p.pushOperators(popGraphicsState());
 }
 const tx=x+244*unit,tw=w-268*unit;
 const short=(text,size,limit=tw)=>{let t=text.replace(/[\r\n]+/g,' ');if(font.widthOfTextAtSize(t,size)<=limit)return t;while(t.length&&font.widthOfTextAtSize(t+'...',size)>limit)t=t.slice(0,-1);return t+'...';};
 let host=c.uri;try{host=new URL(c.uri).hostname.replace(/^www\./,'');}catch{}
 for(const [text,dy,size,col]of [[c.title,65,32,37],[c.body,108,24,128],[host,164,20,128]]){
  const value=short(text,size*unit,text===c.body?Math.min(tw,932*unit):tw),yy=y+h-dy*unit;p.drawText(value,{x:tx,y:yy,font,size:size*unit,color:rgb(col/255,col/255,col/255)});
  if(text===host)p.drawLine({start:{x:tx,y:yy-2*unit},end:{x:tx+font.widthOfTextAtSize(value,size*unit),y:yy-2*unit},thickness:unit,color:rgb(.5,.5,.5)});
 }
}

function bodySize(body,index,metadata){
 let size=17;for(const span of body.spans||[])if(span.kind==='FontSize'&&span.start_utf16<=index&&index<span.end_utf16&&span.payload.length>=4)size=new DataView(Uint8Array.from(span.payload).buffer).getFloat32(0,true);
 const density=(metadata.default_page_dimensions?.[metadata.orientation?1:0]||360)/360;
 const delta=Number.isFinite(metadata.body_font_size_delta)&&metadata.body_font_size_delta!==-2147483648?metadata.body_font_size_delta:0;
 return Math.max(1,size+delta)*density;
}
function restoreInlineImageFlow(body,placed,metadata){
 if(!Object.hasOwn(metadata,'body_font_size_delta'))return;
 const density=(metadata.default_page_dimensions?.[metadata.orientation?1:0]||360)/360;
 for(const [page,images]of placed){
  const section=body.text_sections[page];let text=body.text.slice(section.start_utf16,section.start_utf16+section.length_utf16),at=section.start_utf16;
  if(page>0&&text.startsWith('\n')){text=text.slice(1);at++;}
  const lines=text.split('\n');if(lines.some(l=>l!==''&&l!=='\uFFFC')||lines.filter(l=>l==='\uFFFC').length!==images.length)continue;
  let y=(body.margins?.[1]??10)*density,j=0;
  for(const line of lines){const size=bodySize(body,at,metadata);if(line==='\uFFFC'){
    const im=images[j++],h=im.bbox.y_max-im.bbox.y_min;im.bbox={...im.bbox,y_min:y,y_max:y+h};y+=h+.35*size;
   }else y+=1.35*size;at+=line.length+1;}
 }
}
function urlLayout(body,section,pi,fontSize,density){
 const text=body.text.slice(section.start_utf16,section.start_utf16+section.length_utf16).replace(/^\n/,'');
 const m=text.match(/^(\n*)(https?:\/\/[\x21-\x7e]+)\n*$/);if(!m)return null;
 const at=body.text.indexOf(m[2],section.start_utf16),end=at+m[2].length;
 if((body.spans||[]).some(s=>s.start_utf16<end&&s.end_utf16>at&&['Bold','Italic','FontName','FontFamily'].includes(s.kind)))return null;
 let color=[37,37,37,255];for(const s of body.spans||[])if(s.kind==='ForegroundColor'&&s.start_utf16<=at&&at<s.end_utf16&&s.payload.length>=4)color=[s.payload[2],s.payload[1],s.payload[0],s.payload[3]];
 const hyperlink=(body.spans||[]).some(s=>s.kind==='Hyperlink'&&s.start_utf16<=at&&s.end_utf16>=end);
 if(hyperlink)color=[0,84,255,255];
 const bands=[...(pi.semanticObjects||[]).flatMap(o=>{const im=o.content?.Element?.PlacedImage;return im?[im.bbox]:[];}),...(pi.webCards||[]).map(c=>c.bbox)].sort((a,b)=>a.y_min-b.y_min);
 let top=(body.margins?.[1]??10)*density;const advance=fontSize*1.35;
 for(let line=0;line<=m[1].length;line++){
  for(let changed=true;changed;){changed=false;for(const b of bands)if(top+advance>b.y_min&&top<b.y_max){top=b.y_max;changed=true;break;}}
  if(line===m[1].length)return {text:m[2],x:(body.margins?.[0]??16)*density,baseline:top+fontSize,fontSize,obstacles:bands.length,color,hyperlink};
  top+=advance;
 }
 return null;
}
function unkernUrlText(doc,p){
 const array=/\[((?:\((?:\\[\s\S]|[^\\)])*\)|<[\da-fA-F\s]*>|[-+\d.eE\s])*)\]\s*TJ/g;
 for(const s of streams(doc,p)){if(!(s instanceof PDFRawStream))continue;let text=new TextDecoder().decode(decode(s));text=text.replace(/BT[\s\S]*?ET/g,block=>block.replace(array,(_,inside)=>'['+inside.replace(/(\((?:\\[\s\S]|[^\\)])*\)|<[\da-fA-F\s]*>)|[-+]?(?:\d*\.\d+|\d+\.?\d*)(?:[eE][-+]?\d+)?/g,(v,literal)=>literal||'0')+']TJ'));replace(s,text);}
}
function quantizeRobotoWidths(doc){
 const floorArray=a=>{if(!(a instanceof PDFArray))return;for(let i=0;i<a.size();i++){const v=a.get(i);if(v instanceof PDFArray)floorArray(v);else if(v instanceof PDFNumber)a.set(i,PDFNumber.of(Math.floor(v.asNumber()+1e-8)));}};
 for(const [,o]of doc.context.enumerateIndirectObjects())if(o?.get?.(N('Subtype'))?.toString()==='/CIDFontType2'&&/Roboto-Regular/.test(o.get(N('BaseFont'))?.toString()||''))floorArray(o.lookup(N('W')));
}

// Samsung stores a raster preview of its own annotation layer inside PDF paper.
// The SDOCX objects are rendered separately; replaying this tagged cache duplicates ink.
function stripSamsungAnnotationCache(doc,p,hasNativeObjects){
 if(!hasNativeObjects)return 0;let removed=0;
 const re=/q\s*0 0 0 RG 0 0 0 rg 1 w 0 J 0 j\s*\/[^\s]+ gs\s*\/SPenSDK_PAGE_LIST BMC\s*q\s*(?:[-+\d.eE]+\s+){6}cm\s*\/[^\s]+ Do\s*Q\s*EMC\s*Q/g;
 for(const stream of streams(doc,p)){if(!(stream instanceof PDFRawStream))continue;const text=new TextDecoder().decode(decode(stream));let n=0;const clean=text.replace(re,()=>{n++;return '';});if(n){replace(stream,clean);removed+=n;}}
 return removed;
}
export async function compose(note,rendered,info,pages,{alreadyScaled=false,fontBytes}={}){
 if(info.metadata.page_mode!==0)throw new Error('This version requires a note with separate pages.');
 const media=unzipSync(note,{filter:f=>f.name.startsWith('media/')&&!/\.spi$|mediaInfo\.dat$/.test(f.name)}),manifest=info.metadata.media_manifest?.entries||[];
 function asset(id){const matches=manifest.filter(x=>x.bind_id===id);if(matches.length!==1||!media['media/'+matches[0].file_name])throw new Error(`Missing media: ${id}`);return media['media/'+matches[0].file_name];}
 const doc=await PDFDocument.load(rendered,{updateMetadata:false});doc.registerFontkit(fontkit);
 const cardFont=fontBytes?await doc.embedFont(fontBytes,{subset:false}):await doc.embedFont(StandardFonts.Helvetica);
 const body=info.note?.body,images=new Map(),keys=new Set(),placed=new Map(),sources=new Map();
 for(const span of body?.object_spans||[]){const im=span.content?.Image;if(!im)continue;if(!images.has(im.media_id)){const bytes=asset(im.media_id);images.set(im.media_id,bytes[0]===255&&bytes[1]===216?await doc.embedJpg(bytes):await doc.embedPng(bytes));}
 const found=(body.text_sections||[]).map((s,i)=>({s,i})).filter(({s})=>s.start_utf16<=span.text_index_utf16&&span.text_index_utf16<s.start_utf16+s.length_utf16);if(found.length!==1)throw new Error('The image page could not be identified.');const i=found[0].i;if(!placed.has(i))placed.set(i,[]);placed.get(i).push({...im,bbox:{...im.bbox}});}
 restoreInlineImageFlow(body,placed,info.metadata);
 await doc.flush();for(const im of images.values())keys.add(await key(doc.context.lookup(im.ref)));await removeInlineImages(doc,keys);
 // Recover one isolated body URL when stale flow pagination placed it on an
 // otherwise empty physical page. Never move page-local text or handwriting.
 const movedBody=new Map(),cleared=new Set(),bodyMoves=[];
 const nativeFont=fontBytes?fontkit.create(fontBytes):null;
 for(let i=0;i<doc.getPageCount();i++){
  const texts=(pages[i].semanticObjects||[]).flatMap(o=>{const t=o.content?.Element?.TextBox;return t?[t.text.trim()]:[];});
  if(texts.length===1&&/^https?:\/\/[\x21-\x7e]+$/.test(texts[0]))unkernUrlText(doc,doc.getPage(i));
 }
 const lines=(body?.text||'').split('\n').map(x=>x.replace(/\uFFFC/g,'').trim()).filter(Boolean);
 if(lines.length===1 && /^https?:\/\/\S+$/.test(lines[0])){
  const at=body.text.indexOf(lines[0]),targets=(body.text_sections||[]).map((s,i)=>({s,i})).filter(({s})=>s.start_utf16<=at&&at<s.start_utf16+s.length_utf16);
  const candidates=[];
  for(let i=0;i<doc.getPageCount();i++)if(pages[i].layers.every(l=>l.objects===0)&&!pages[i].background?.pdf_paper?.length){
   const count=streams(doc,doc.getPage(i)).reduce((n,s)=>n+(s instanceof PDFRawStream?(new TextDecoder().decode(decode(s)).match(/\bBT\b/g)||[]).length:0),0);
   if(count===1)candidates.push(i);
  }
  if(targets.length===1&&candidates.length===1&&targets[0].i!==candidates[0]){
   const from=candidates[0],to=targets[0].i,p=doc.getPage(from);stripCanvas(doc,p);
   const content=streams(doc,p).map(s=>new TextDecoder().decode(decode(s))).join(' '),sizes=[...content.matchAll(/\/[A-Za-z0-9]+\s+([\d.]+)\s+Tf/g)].map(m=>Number(m[1]));
   const density=(info.metadata.default_page_dimensions?.[info.metadata.orientation?1:0]||0)/360;
   const layout=nativeFont&&sizes.length===1&&density>0?urlLayout(body,targets[0].s,pages[to],sizes[0],density):null;
   if(layout&&layout.baseline+layout.fontSize*.35<pages[to].header.height){movedBody.set(to,{layout});}
   else{const embedded=await doc.embedPage(p);await doc.flush();movedBody.set(to,{embedded,width:p.getWidth(),height:p.getHeight()});}
   p.node.set(N('Contents'),doc.context.obj([]));p.contentStream=undefined;p.contentStreamRef=undefined;cleared.add(from);bodyMoves.push({from:from+1,to:to+1,layout:layout||null});
  }
 }
 let count=doc.getPageCount();const last=pages[count-1],section=body?.text_sections?.[count-1];
 const omitted=count>1&&last.layers.every(l=>l.objects===0)&&!last.background?.pdf_paper?.length&&last.template==null&&last.background?.image_id==null&&(!section||section.length_utf16===0)&&!placed.has(count-1);if(omitted)count--;
 const report={pages:count,pdfBackgrounds:[],bodyImages:[],cards:[],omittedTrailingBlank:omitted,bodyMoves,removedAnnotationCaches:[]};
 for(let i=0;i<count;i++){
  const pi=pages[i],w=pi.header.width,h=pi.header.height,papers=pi.background?.pdf_paper||[];let width=600,height=Math.floor(600*h/w),back;
  if(papers.length>1)throw new Error('Multiple PDF backgrounds are not supported yet.');
  if(papers.length){const r=papers[0],rect=Object.values(r.rectangle)[0];if(rect[0]!==0||rect[1]!==0||Math.abs(rect[2]-w)>1||Math.abs(rect[3]-h)>1)throw new Error('The PDF background does not cover the full page.');if(!sources.has(r.media_id))sources.set(r.media_id,await PDFDocument.load(asset(r.media_id),{updateMetadata:false}));const src=sources.get(r.media_id),sp=src.getPage(r.page_index);if(sp.getRotation().angle)throw new Error('Rotated PDF background.');({width,height}=sp.getSize());if(stripSamsungAnnotationCache(src,sp,pi.layers.some(l=>l.objects>0)))report.removedAnnotationCaches.push(i+1);[back]=await doc.embedPdf(src,[r.page_index]);report.pdfBackgrounds.push(i+1);}
  const p=doc.getPage(i);if(!cleared.has(i))stripCanvas(doc,p);const factor=width/p.getWidth(),oldHeight=p.getHeight();p.scaleContent(factor,factor);p.translateContent(0,height-oldHeight*factor);
  const overlay=p.node.Contents().asArray().slice();p.node.set(N('Contents'),doc.context.obj([]));p.contentStream=undefined;p.contentStreamRef=undefined;p.setSize(width,height);p.setCropBox(0,0,width,height);
  const col=pi.backgroundColor||{r:255,g:255,b:255};p.drawRectangle({x:0,y:0,width,height,color:rgb(col.r/255,col.g/255,col.b/255)});if(back)p.drawPage(back,{x:0,y:0,width,height});
  const scale=width/w;
  for(const im of placed.get(i)||[]){if(im.rotation_degrees)throw new Error('Rotated inline image.');const b=im.bbox,img=images.get(im.media_id),x=b.x_min*scale,y=height-b.y_max*scale,bw=(b.x_max-b.x_min)*scale,bh=(b.y_max-b.y_min)*scale;
   p.pushOperators(pushGraphicsState(),rectangle(x,y,bw,bh),clip(),endPath());if(im.crop_rect){const [l,t,r,bot]=im.crop_rect,cw=r-l,ch=bot-t;if(cw<=0||ch<=0)throw new Error('Invalid image crop.');p.drawImage(img,{x:x-l*bw/cw,y:y-(img.height-bot)*bh/ch,width:img.width*bw/cw,height:img.height*bh/ch});}else p.drawImage(img,{x,y,width:bw,height:bh});p.pushOperators(popGraphicsState());report.bodyImages.push({page:i+1,id:im.media_id,bbox:im.bbox});}
  for(const c of pi.webCards||[]){await drawCard(doc,p,c,asset,scale,height,cardFont);report.cards.push(i+1);}
  const moved=movedBody.get(i);if(moved?.layout){
   const l=moved.layout,size=l.fontSize*scale,baseline=height-l.baseline*scale;let x=l.x*scale;
   for(const char of l.text){p.drawText(char,{x,y:baseline,size,font:cardFont,color:rgb(l.color[0]/255,l.color[1]/255,l.color[2]/255),opacity:l.color[3]/255});x+=Math.floor(nativeFont.glyphForCodePoint(char.codePointAt(0)).advanceWidth/nativeFont.unitsPerEm*1000)*size/1000;}
   const underlineWidth=nativeFont.layout(l.text).positions.reduce((n,p)=>n+p.xAdvance,0)/nativeFont.unitsPerEm*size;
   if(l.hyperlink)p.drawRectangle({x:l.x*scale,y:baseline-size/9-size/18,width:underlineWidth,height:size/18,color:rgb(0,84/255,1)});
  }else if(moved){const f=width/moved.width;p.drawPage(moved.embedded,{x:0,y:height-moved.height*f,width,height:moved.height*f});}
  const underlay=p.node.Contents().asArray().slice();p.node.set(N('Contents'),doc.context.obj([...underlay,...overlay]));if(i%20===0)console.log('Oldal',i+1);
 }
 while(doc.getPageCount()>count)doc.removePage(doc.getPageCount()-1);await doc.flush();quantizeRobotoWidths(doc);return {bytes:await doc.save(),report};
}
