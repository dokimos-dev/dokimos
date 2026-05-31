import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router";
import {
  Activity,
  Database,
  FolderGit2,
  House,
  KeyRound,
  ListChecks,
  ListTodo,
  Plug,
  Search,
  type LucideIcon,
} from "lucide-react";
import { useListProjects } from "@/lib/api/project-controller/project-controller";
import { cn } from "@/lib/utils";

interface Command {
  id: string;
  label: string;
  hint?: string;
  icon: LucideIcon;
  to: string;
}

const NAV_COMMANDS: Command[] = [
  { id: "home", label: "Home", hint: "Projects", icon: House, to: "/" },
  { id: "traces", label: "Traces", hint: "Observability", icon: Activity, to: "/traces" },
  { id: "rules", label: "Trace eval rules", hint: "Observability", icon: ListChecks, to: "/trace-eval-rules" },
  { id: "review", label: "Review queue", hint: "Evaluation", icon: ListTodo, to: "/review-queue" },
  { id: "datasets", label: "Datasets", icon: Database, to: "/datasets" },
  { id: "connections", label: "LLM connections", hint: "Settings", icon: Plug, to: "/llm-connections" },
  { id: "keys", label: "API keys", hint: "Settings", icon: KeyRound, to: "/api-keys" },
];

export default function CommandPalette({ open, onClose }: { open: boolean; onClose: () => void }) {
  const navigate = useNavigate();
  const inputRef = useRef<HTMLInputElement>(null);
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);
  const { data } = useListProjects();
  const projects = data?.data ?? [];

  const commands = useMemo<Command[]>(() => {
    const projectCommands: Command[] = projects.map((p) => ({
      id: `project-${p.name}`,
      label: p.name ?? "",
      hint: "Project",
      icon: FolderGit2,
      to: `/projects/${encodeURIComponent(p.name ?? "")}`,
    }));
    const all = [...NAV_COMMANDS, ...projectCommands];
    const q = query.trim().toLowerCase();
    if (!q) return all;
    return all.filter(
      (c) => c.label.toLowerCase().includes(q) || c.hint?.toLowerCase().includes(q),
    );
  }, [projects, query]);

  useEffect(() => {
    if (open) {
      setQuery("");
      setActive(0);
      // focus after paint
      requestAnimationFrame(() => inputRef.current?.focus());
    }
  }, [open]);

  useEffect(() => setActive(0), [query]);

  if (!open) return null;

  const select = (cmd: Command | undefined) => {
    if (!cmd) return;
    onClose();
    navigate(cmd.to);
  };

  return (
    <div
      className="fixed inset-0 z-[60] flex items-start justify-center bg-black/60 p-4 pt-[12vh] backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        className="w-full max-w-lg overflow-hidden rounded-lg border border-border-strong bg-popover shadow-[var(--shadow)]"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center gap-2.5 border-b border-border px-3.5">
          <Search className="size-4 shrink-0 text-muted-foreground" />
          <input
            ref={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "ArrowDown") {
                e.preventDefault();
                setActive((i) => Math.min(i + 1, commands.length - 1));
              } else if (e.key === "ArrowUp") {
                e.preventDefault();
                setActive((i) => Math.max(i - 1, 0));
              } else if (e.key === "Enter") {
                e.preventDefault();
                select(commands[active]);
              }
            }}
            placeholder="Jump to a page or project…"
            className="h-11 w-full bg-transparent text-[13px] text-foreground outline-none placeholder:text-faint"
          />
          <kbd className="rounded border border-border bg-muted px-1.5 py-0.5 text-[10px] text-faint">
            esc
          </kbd>
        </div>
        <div className="max-h-[320px] overflow-y-auto p-1.5">
          {commands.length === 0 ? (
            <div className="px-3 py-8 text-center text-[12px] text-muted-foreground">
              No matches for “{query}”
            </div>
          ) : (
            commands.map((cmd, i) => {
              const Icon = cmd.icon;
              return (
                <button
                  key={cmd.id}
                  type="button"
                  onMouseEnter={() => setActive(i)}
                  onClick={() => select(cmd)}
                  className={cn(
                    "flex w-full items-center gap-2.5 rounded-md px-2.5 py-2 text-left text-[13px]",
                    i === active ? "bg-accent text-foreground" : "text-text-2",
                  )}
                >
                  <Icon className="size-4 shrink-0 text-muted-foreground" strokeWidth={1.5} />
                  <span className="flex-1 truncate">{cmd.label}</span>
                  {cmd.hint && (
                    <span className="text-[10px] uppercase tracking-wider text-faint">{cmd.hint}</span>
                  )}
                </button>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
}
