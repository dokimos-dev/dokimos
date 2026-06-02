import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

interface MetricCardProps {
  label: string;
  value: ReactNode;
  sub?: ReactNode;
  /** Tints the value (e.g. "success" for Passed, color-coded pass rate). */
  tone?: "default" | "success" | "warning" | "destructive" | "primary";
  /** Draws the accent left edge used on the headline metric. */
  accent?: boolean;
  icon?: ReactNode;
  className?: string;
}

const TONE: Record<string, string> = {
  default: "text-foreground",
  success: "text-success",
  warning: "text-warning",
  destructive: "text-destructive",
  primary: "text-primary",
};

export default function MetricCard({
  label,
  value,
  sub,
  tone = "default",
  accent = false,
  icon,
  className,
}: MetricCardProps) {
  return (
    <div
      className={cn(
        "relative overflow-hidden rounded-lg border bg-card px-4 py-3.5",
        accent && "border-l-2 border-l-primary",
        className,
      )}
    >
      <div className="flex items-center gap-1.5 text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
        {icon}
        {label}
      </div>
      <div className={cn("mt-1.5 font-mono text-[26px] leading-none tabular-nums", TONE[tone])}>
        {value}
      </div>
      {sub != null && <div className="mt-2 text-[12px] text-muted-foreground">{sub}</div>}
    </div>
  );
}

export function MetricGrid({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={cn("grid grid-cols-2 gap-3 lg:grid-cols-4", className)}>{children}</div>
  );
}
