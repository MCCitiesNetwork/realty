/**
 * The disclaimer Mojang's brand guidelines require of anything built on Minecraft.
 *
 * <p>Not configurable and not conditional: the terms ask for it on every page, and a
 * footer an operator can switch off is one that will be. It credits nothing else --
 * the resource pack's credit belongs beside the preview that uses the pack.</p>
 */
export function SiteFooter() {
  return (
    <footer className="site-footer">
      <p>
        NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR
        MICROSOFT.
      </p>
    </footer>
  );
}
