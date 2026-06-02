import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router";
import { SWRConfig } from "swr";
import axios from "axios";
import { router } from "./routes";
import { BreadcrumbProvider } from "./lib/breadcrumb-context";
import { ThemeProvider } from "./lib/theme-context";
import "./index.css";

// The generated client models Spring's Pageable as a nested `pageable` object,
// but the server binds it from flat `page`/`size`/`sort` query params. Flatten
// `pageable` (and serialize arrays as repeated keys) so pagination works.
axios.defaults.paramsSerializer = (params: Record<string, unknown>) => {
  const usp = new URLSearchParams();
  const add = (k: string, v: unknown) => {
    if (v !== undefined && v !== null) usp.append(k, String(v));
  };
  for (const [key, value] of Object.entries(params ?? {})) {
    if (value === undefined || value === null) continue;
    if (key === "pageable" && typeof value === "object" && !Array.isArray(value)) {
      for (const [pk, pv] of Object.entries(value as Record<string, unknown>)) {
        if (Array.isArray(pv)) pv.forEach((x) => add(pk, x));
        else add(pk, pv);
      }
    } else if (Array.isArray(value)) {
      value.forEach((x) => add(key, x));
    } else {
      add(key, value);
    }
  }
  return usp.toString();
};

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
