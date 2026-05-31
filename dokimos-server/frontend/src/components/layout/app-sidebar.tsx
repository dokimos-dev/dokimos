import { Link, NavLink } from "react-router";
import {
  Activity,
  Database,
  House,
  KeyRound,
  ListChecks,
  ListTodo,
  Plug,
  type LucideIcon,
} from "lucide-react";
import { useCallback } from "react";
import { useTheme } from "@/lib/theme-context";
import {
  ThemeToggleButton,
  useThemeTransition,
} from "@/components/ui/shadcn-io/theme-toggle-button";
import ProjectSwitcher from "./project-switcher";
import { cn } from "@/lib/utils";

interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  end?: boolean;
}

const NAV_GROUPS: { label: string; items: NavItem[] }[] = [
  { label: "Overview", items: [{ to: "/", label: "Home", icon: House, end: true }] },
  {
    label: "Observability",
    items: [
      { to: "/traces", label: "Traces", icon: Activity },
      { to: "/trace-eval-rules", label: "Trace eval rules", icon: ListChecks },
    ],
  },
  {
    label: "Experiments",
    items: [{ to: "/review-queue", label: "Review queue", icon: ListTodo }],
  },
  {
    label: "Datasets",
    items: [{ to: "/datasets", label: "Datasets", icon: Database }],
  },
  {
    label: "Settings",
    items: [
      { to: "/llm-connections", label: "LLM connections", icon: Plug },
      { to: "/api-keys", label: "API keys", icon: KeyRound },
    ],
  },
];

function BrandMark() {
  return (
    <span className="grid size-7 place-items-center rounded-[7px] bg-black text-white">
      <svg viewBox="0 0 100 100" fill="currentColor" fillRule="evenodd" className="size-[60%]" aria-hidden>
        <path d="M100 0 100 100 0 100Z M86 28 86 86 28 86Z" />
      </svg>
    </span>
  );
}

/** Sidebar content shared by the desktop rail and the mobile drawer. */
export default function AppSidebar({ onNavigate }: { onNavigate?: () => void }) {
  const { theme, toggleTheme } = useTheme();
  const { startTransition } = useThemeTransition();

  const handleThemeToggle = useCallback(() => {
    startTransition(() => toggleTheme());
  }, [toggleTheme, startTransition]);

  return (
    <div className="flex h-full flex-col bg-sidebar text-sidebar-foreground">
      <Link
        to="/"
        onClick={onNavigate}
        className="flex items-center gap-2.5 px-4 pb-3 pt-4 font-semibold tracking-tight"
      >
        <BrandMark />
        <span className="text-[15px]">Dokimos</span>
      </Link>

      <ProjectSwitcher />

      <nav className="flex-1 overflow-y-auto px-3 py-2">
        {NAV_GROUPS.map((group) => (
          <div key={group.label} className="mb-4">
            <div className="px-2 pb-1.5 text-[10px] font-medium uppercase tracking-[0.12em] text-faint">
              {group.label}
            </div>
            <div className="space-y-0.5">
              {group.items.map((item) => {
                const Icon = item.icon;
                return (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.end}
                    onClick={onNavigate}
                    className={({ isActive }) =>
                      cn(
                        "relative flex items-center gap-2.5 rounded-md px-2 py-1.5 text-[13px] transition-colors",
                        isActive
                          ? "bg-primary/10 font-medium text-foreground"
                          : "text-text-2 hover:bg-accent hover:text-foreground",
                      )
                    }
                  >
                    {({ isActive }) => (
                      <>
                        {isActive && (
                          <span className="absolute inset-y-1 -left-1 w-0.5 rounded-full bg-primary shadow-[0_0_8px_var(--accent-glow)]" />
                        )}
                        <Icon
                          className={cn(
                            "size-4 shrink-0",
                            isActive ? "text-[var(--accent-glow)]" : "text-muted-foreground",
                          )}
                          strokeWidth={1.5}
                        />
                        <span className="truncate">{item.label}</span>
                      </>
                    )}
                  </NavLink>
                );
              })}
            </div>
          </div>
        ))}
      </nav>

      <div className="flex items-center gap-2 border-t border-sidebar-border px-3 py-2.5">
        <ThemeToggleButton
          theme={theme}
          onClick={handleThemeToggle}
          variant="circle"
          start="bottom-left"
        />
        <span className="ml-auto flex items-center gap-1.5 text-[11px] text-muted-foreground">
          <span className="size-1.5 rounded-full bg-success" />
          healthy
        </span>
      </div>
    </div>
  );
}
