import init, { DocumentSession } from './sdocx.js';
import { compose, decodeCard } from './compose.js';
import { unzipSync } from './fflate.js';

const diagnosticFields = [
    'text_diagnostics', 'object_diagnostics', 'geometry_diagnostics',
    'background_diagnostics', 'paint_diagnostics',
];

export async function convert(bytes, {fontBytes,onRendered} = {}) {
    if (!fontBytes) {const r=await fetch(new URL("./Roboto-Regular.ttf",import.meta.url));if(!r.ok)throw new Error("Could not load the font.");fontBytes=new Uint8Array(await r.arrayBuffer());}
    const session = new DocumentSession(bytes);
    try {
        const pages = Uint32Array.from({ length: session.page_count }, (_, index) => index);
        if (!pages.length) throw new Error('This note has no exportable pages.');
        const info=JSON.parse(session.debug('{"kind":"document"}'));
        const pageInfo=[];
        for(let i=0;i<session.page_count;i++){const p=JSON.parse(session.debug(JSON.stringify({kind:'page',page:i})));p.semanticObjects=(p.semanticObjects||[]).filter(o=>!o.content?.Stroke);pageInfo.push(p);}
        const issues=info.diagnostics.diagnostics.filter(d=>d.code==='UnsupportedObjectType'&&/Web \(type 13\)/.test(d.message));
        if(issues.length){const entries=unzipSync(bytes,{filter:f=>f.name.endsWith('.page')});
          for(const issue of issues){const i=info.metadata.page_ids.indexOf(issue.archive_entry.replace(/\.page$/,''));const m=issue.message.match(/at 0x([0-9a-f]+)/i);if(i<0||!m)throw new Error('Unknown web card.');const obj=JSON.parse(session.debug(JSON.stringify({kind:'object',page:i,offset:parseInt(m[1],16)})));(pageInfo[i].webCards ||= []).push(decodeCard(entries[issue.archive_entry],obj));}}
        const output = session.render_pdf_pages_detailed(pages, 'auto');
        if (!(output.bytes instanceof Uint8Array)) throw new Error('PDF generation failed.');
        const affected = output.pages.flatMap((page, index) =>
            diagnosticFields.some(field => page[field]?.length) ? [index + 1] : []);
        const { diagnostics } = JSON.parse(session.debug('{"kind":"document"}'));
        const missingImages = diagnostics.diagnostics.some(issue => issue.code === 'UnresolvedImageMedia');
        if(onRendered)onRendered(output.bytes,info,pageInfo);
        const fixed=await compose(bytes,output.bytes,info,pageInfo,{fontBytes});
        return { bytes: fixed.bytes, pages: fixed.report.pages, affected, missingImages, report:fixed.report };
    } finally {
        session.free();
    }
}

export async function start(bridge) {
    try {
        await init();
    } catch (error) {
        if (typeof bridge.startupFailed === 'function') bridge.startupFailed();
        else bridge.error('Could not start the PDF engine. Update Android System WebView.', String(error));
        return;
    }

    globalThis.exportNote = async () => {
        try {
            const response = await fetch('/note', { cache: 'no-store' });
            if (!response.ok) throw new Error('The selected file could not be read.');
            const result = await convert(new Uint8Array(await response.arrayBuffer()));
            const pdfBytes = result.bytes;
            if (!bridge.begin()) throw new Error('Could not open the temporary PDF.');
            const chunkSize = 24 * 1024;
            for (let offset = 0; offset < pdfBytes.length; offset += chunkSize) {
                const data = pdfBytes.subarray(offset, offset + chunkSize);
                if (!bridge.chunk(btoa(String.fromCharCode(...data)))) {
                    throw new Error('Could not write the temporary PDF.');
                }
            }
            const affected = result.affected.slice(0, 15).join(', ')
                + (result.affected.length > 15 ? ', …' : '');
            bridge.finish(result.pages, affected, result.missingImages);
        } catch (error) {
            bridge.error('Could not convert the note.', String(error));
        }
    };
    bridge.ready();
}

if (globalThis.NotesApp) void start(globalThis.NotesApp);
