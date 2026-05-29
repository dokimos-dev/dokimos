import { cn } from "@/lib/utils";

interface PassRateProps {
  rate?: number | null;
}

export default function PassRate({ rate }: PassRateProps) {
  if (rate == null) {
    return <span className="text-muted-foreground">—</span>;
  }

  const percentage = (rate * 100).toFixed(1);

  const colorClass = cn({
    "text-destructive": rate < 0.5,
    "text-warning": rate >= 0.5 && rate < 0.8,
    "text-success": rate >= 0.8,
  });

  return <span className={colorClass}>{percentage}%</span>;
}
