import { useEffect, useState } from "react";
import { Outlet, useLocation } from "react-router";
import { X } from "lucide-react";
import AppSidebar from "./app-sidebar";
import Topbar from "./topbar";

export default function Layout() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const location = useLocation();

  // Close the mobile drawer on route change.
  useEffect(() => setMobileOpen(false), [location.pathname]);

  return (
    <div className="min-h-screen bg-background">
      {/* Desktop rail */}
      <aside className="fixed inset-y-0 left-0 z-40 hidden w-[252px] border-r border-sidebar-border lg:block">
        <AppSidebar />
      </aside>

      {/* Mobile drawer */}
      {mobileOpen && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div
            className="absolute inset-0 bg-black/60"
            onClick={() => setMobileOpen(false)}
          />
          <div className="absolute inset-y-0 left-0 w-[252px] border-r border-sidebar-border shadow-[var(--shadow)]">
            <button
              type="button"
              onClick={() => setMobileOpen(false)}
              aria-label="Close navigation"
              className="absolute right-2 top-3 z-10 grid size-7 place-items-center rounded-md text-muted-foreground hover:bg-accent"
            >
              <X className="size-4" />
            </button>
            <AppSidebar onNavigate={() => setMobileOpen(false)} />
          </div>
        </div>
      )}

      <div className="lg:pl-[252px]">
        <Topbar onMenuClick={() => setMobileOpen(true)} />
        <main className="mx-auto w-full max-w-[1400px] p-4 sm:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
