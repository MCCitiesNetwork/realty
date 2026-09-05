/**
 * Points the tab's icon at the operator's emblem.
 *
 * `index.html` declares no icon of its own -- the emblem is a deployment setting, read
 * from `config.json` at runtime, so it cannot be baked into the built page. With none
 * configured the page is left as it was, and the browser goes on asking for
 * `/favicon.ico` as it always did.
 */
export function applyFavicon(url: string, doc: Document = document): void {
  if (!url) return;
  let link = doc.querySelector<HTMLLinkElement>('link[rel="icon"]');
  if (!link) {
    link = doc.createElement("link");
    link.rel = "icon";
    doc.head.appendChild(link);
  }
  link.href = url;
}
