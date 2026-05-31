import { Menu, Search } from "lucide-react";
import Breadcrumbs from "./breadcrumbs";
import { useBreadcrumbs } from "@/lib/breadcrumb-context";

export default function Topbar({ onMenuClick }: { onMenuClick: () => void }) {
  const { breadcrumbs } = useBreadcrumbs();

  return (
    <header className="sticky top-0 z-30 flex h-12 items-center gap-3 border-b border-border bg-background/85 px-4 backdrop-blur supports-[backdrop-filter]:bg-background/70 sm:px-6">
      <button
        type="button"
        onClick={onMenuClick}
        aria-label="Open navigation"
        className="grid size-8 place-items-center rounded-md text-muted-foreground hover:bg-accent hover:text-foreground lg:hidden"
      >
        <Menu className="size-4" />
      </button>

      <div className="min-w-0 flex-1">
        <Breadcrumbs items={breadcrumbs} />
      </div>

      <button
        type="button"
        className="hidden items-center gap-2 rounded-md border border-border bg-card px-2.5 py-1.5 text-[12px] text-muted-foreground hover:bg-accent sm:flex"
      >
        <Search className="size-3.5" />
        <span>Search</span>
        <kbd className="ml-3 rounded border border-border bg-muted px-1 text-[10px] text-faint">⌘K</kbd>
      </button>
    </header>
  );
}
