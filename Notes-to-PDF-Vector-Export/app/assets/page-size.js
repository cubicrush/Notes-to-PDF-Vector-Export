import { PDFDocument } from './pdf-lib.js';

// Samsung Notes uses a 600-point width and truncates the proportional
// height of ordinary note pages. The drawing keeps its uniform scale.
const SAMSUNG_PAGE_WIDTH = 600;

export async function useSamsungPageSize(bytes) {
    const pdf = await PDFDocument.load(bytes, { updateMetadata: false });
    for (const page of pdf.getPages()) {
        const { width, height } = page.getMediaBox();
        const scale = SAMSUNG_PAGE_WIDTH / width;
        const scaledHeight = SAMSUNG_PAGE_WIDTH * height / width;
        const pageHeight = Math.max(1, Math.floor(scaledHeight));
        page.scaleContent(scale, scale);
        page.translateContent(0, pageHeight - scaledHeight);
        page.setSize(SAMSUNG_PAGE_WIDTH, pageHeight);
    }
    return pdf.save({ addDefaultPage: false, updateFieldAppearances: false });
}
