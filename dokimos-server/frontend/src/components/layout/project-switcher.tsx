import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router";
import { Check, ChevronsUpDown, FolderGit2 } from "lucide-react";
import { useListProjects } from "@/lib/api/project-controller/project-controller";
import { cn } from "@/lib/utils";

/** Project combobox at the top of the sidebar. Current project is derived from
 *  the URL (/projects/:name); selecting one navigates there. */
export default function ProjectSwitcher() {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const location = useLocation();
  const { data } = useListProjects();
  const projects = data?.data ?? [];

  const match = location.pathname.match(/^\/projects\/([^/]+)/);
  const current = match ? decodeURIComponent(match[1]) : null;

  useEffect(() => {
    function onDown(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onDown);
    return () => document.removeEventListener("mousedown", onDown);
  }, []);

  return (
    <div ref={ref} className="relative px-3 pb-2">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex w-full items-center gap-2 rounded-md border border-border bg-card px-2.5 py-2 text-left text-foreground transition-colors hover:bg-accent"
      >
        <span className="size-1.5 shrink-0 rounded-full bg-success shadow-[0_0_0_3px_var(--pass-tint)]" />
        <span className="min-w-0 flex-1 truncate text-[13px] font-medium">
          {current ?? "All projects"}
        </span>
        <span className="text-[10px] uppercase tracking-wider text-faint">project</span>
        <ChevronsUpDown className="size-3.5 shrink-0 text-muted-foreground" />
      </button>

      {open && (
        <div className="absolute left-3 right-3 z-50 mt-1 max-h-80 overflow-auto rounded-md border border-border-strong bg-popover p-1 shadow-[var(--shadow)]">
          <button
            type="button"
            onClick={() => {
              navigate("/");
              setOpen(false);
            }}
            className="flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-left text-[13px] text-foreground hover:bg-accent"
          >
            <FolderGit2 className="size-3.5 text-muted-foreground" />
            <span className="flex-1">All projects</span>
            {!current && <Check className="size-3.5 text-primary" />}
          </button>
          {projects.length > 0 && <div className="my-1 h-px bg-border" />}
          {projects.map((p) => (
            <button
              key={p.name}
              type="button"
              onClick={() => {
                navigate(`/projects/${encodeURIComponent(p.name!)}`);
                setOpen(false);
              }}
              className={cn(
                "flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-left text-[13px] hover:bg-accent",
                current === p.name ? "text-foreground" : "text-text-2",
              )}
            >
              <span className="size-1.5 rounded-full bg-success/70" />
              <span className="flex-1 truncate">{p.name}</span>
              {current === p.name && <Check className="size-3.5 text-primary" />}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
