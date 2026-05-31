import type { ReactNode } from "react";

/** Consistent centered empty/zero state used across list pages. */
export default function EmptyState({
  title,
  description,
  icon,
  action,
}: {
  title: string;
  description?: ReactNode;
  icon?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 px-4 py-16 text-center">
      {icon && (
        <div className="grid size-10 place-items-center rounded-lg border border-border bg-muted text-muted-foreground">
          {icon}
        </div>
      )}
      <div className="text-[13px] font-medium text-foreground">{title}</div>
      {description && (
        <p className="max-w-sm text-[12px] text-muted-foreground">{description}</p>
      )}
      {action}
    </div>
  );
}
