/**
 * Recreation.gov division descriptions arrive as HTML strings (e.g. `<p>...</p>` blocks). These
 * helpers extract plain text via a detached DOMParser document — read-only (.textContent), never
 * written back into the DOM as HTML — so there's no injection risk from upstream content.
 */
export function htmlToParagraphs(html: string | null | undefined): string[] {
  if (!html) return []
  const doc = new DOMParser().parseFromString(html, 'text/html')
  const paragraphs = Array.from(doc.body.querySelectorAll('p'))
    .map((p) => p.textContent?.trim() ?? '')
    .filter((text) => text.length > 0)
  if (paragraphs.length > 0) return paragraphs
  const text = doc.body.textContent?.trim() ?? ''
  return text ? [text] : []
}

export function firstParagraph(html: string | null | undefined): string {
  return htmlToParagraphs(html)[0] ?? ''
}
