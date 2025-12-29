import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router";
import { SWRConfig } from "swr";
import { router } from "./routes";
import { BreadcrumbProvider } from "./lib/breadcrumb-context";
import { ThemeProvider } from "./lib/theme-context";
import "./index.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ThemeProvider>
      <SWRConfig value={{ revalidateOnFocus: false }}>
        <BreadcrumbProvider>
          <RouterProvider router={router} />
        </BreadcrumbProvider>
      </SWRConfig>
    </ThemeProvider>
  </StrictMode>
);
