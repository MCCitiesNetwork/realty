import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { createApiClient } from "./api/client";
import { loadConfig } from "./config";
import { AppRoutes } from "./router";
import { applyFavicon } from "./ui/favicon";
import "./styles.css";

/**
 * Resolves configuration before the first render, so no screen has to cope with a
 * client that does not yet know where the API is.
 *
 * A function rather than a top-level await: the latter constrains the build target
 * for no gain here, since nothing renders until this resolves either way.
 */
async function bootstrap(): Promise<void> {
  const config = await loadConfig();
  const client = createApiClient(config.apiBaseUrl);
  applyFavicon(config.logoUrl);

  const container = document.getElementById("root");
  if (!container) throw new Error("index.html is missing its #root element");

  createRoot(container).render(
    <StrictMode>
      <BrowserRouter>
        <AppRoutes client={client} config={config} />
      </BrowserRouter>
    </StrictMode>,
  );
}

void bootstrap();
