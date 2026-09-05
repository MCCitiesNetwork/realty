/**
 * The IndexedDB databases in which the renderer remembers resource packs between visits.
 *
 * It keeps them in two layers, and both put stored packs back on start-up before
 * looking at the pack they are handed: the outer manager keeps a pack under the name it
 * was given and skips fetching a default pack whose name it already holds, while the
 * inner one drops a handed pack whose bytes it has seen. So once a browser had loaded
 * any pack, a change of pack on the server never reached it: the old pack came back
 * from the store under the same name, and the preview built its atlas from whatever
 * that pack had, or from nothing. The atlas cache is a separate store and is left alone.
 */
const REMEMBERED_PACK_STORES = ["ResourcePacksDB", "cubane-resource-packs", "cubane-cache"];

/** Where this page notes which packs the renderer's stores were last filled from. */
const FINGERPRINT_KEY = "realty.viewer.packs";

async function forgetRememberedPacks(): Promise<void> {
  if (typeof indexedDB === "undefined") return;
  await Promise.all(REMEMBERED_PACK_STORES.map((name) => new Promise<void>((resolve) => {
    const request = indexedDB.deleteDatabase(name);
    // Best effort: a blocked delete finishes once the previous renderer lets go, and a
    // failure to delete is no reason to show no preview at all.
    request.onsuccess = () => resolve();
    request.onerror = () => resolve();
    request.onblocked = () => resolve();
  })));
}

/**
 * Drops the renderer's remembered packs when the server's pack list is not the one
 * they were filled from -- and only then.
 *
 * Wiping on every start kept the stores honest but threw away a parsed 30 MB pack on
 * each region visit. The server's list is the only pack this page means, so the list
 * itself is the fingerprint: while it is unchanged, what the stores hold is exactly
 * what would be loaded again, and the renderer may keep it. `localStorage` is
 * best-effort here too; a browser that refuses it simply wipes each time, as before.
 */
export async function forgetRememberedPacksUnless(packUrls: ReadonlyArray<string>): Promise<void> {
  const fingerprint = packUrls.join("\n");
  let remembered: string | null = null;
  try {
    remembered = localStorage.getItem(FINGERPRINT_KEY);
  } catch {
    // No storage: fall through to a wipe.
  }
  if (remembered === fingerprint) return;

  await forgetRememberedPacks();
  try {
    localStorage.setItem(FINGERPRINT_KEY, fingerprint);
  } catch {
    // Nothing to remember it in; the next visit wipes again, which is safe.
  }
}
