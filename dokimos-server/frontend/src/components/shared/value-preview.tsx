import { cn } from "@/lib/utils";

/** A readable one-line preview of an input/output value: shows the value(s),
 *  not the raw map keys (e.g. {"output":"Paris"} -> "Paris"). */
export function previewText(value: unknown): string {
  if (value === null || value === undefined) return "—";
  if (typeof value === "string") return value;
  if (typeof value !== "object") return String(value);
  const values = Object.values(value as Record<string, unknown>);
  if (values.length === 0) return "{}";
  if (values.length === 1) return previewText(values[0]);
  try {
    return values
      .map((v) => (typeof v === "string" ? v : JSON.stringify(v)))
      .join("  ·  ");
  } catch {
    return "[object]";
  }
}

/** Single-line, truncating cell preview with the full value on hover. */
export default function ValuePreview({
  value,
  className,
}: {
  value: unknown;
  className?: string;
}) {
  const text = previewText(value);
  return (
    <span className={cn("block truncate", className)} title={text}>
      {text}
    </span>
  );
}
