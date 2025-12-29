import { Badge } from "@/components/ui/badge";

type Status = "RUNNING" | "SUCCESS" | "FAILED" | "CANCELLED";

interface StatusBadgeProps {
  status: string;
}

const statusConfig: Record<Status, { label: string; variant: "default" | "secondary" | "destructive" | "outline" }> = {
  RUNNING: { label: "Running", variant: "default" },
  SUCCESS: { label: "Success", variant: "default" },
  FAILED: { label: "Failed", variant: "destructive" },
  CANCELLED: { label: "Cancelled", variant: "secondary" },
};

const statusColors: Record<Status, string> = {
  RUNNING: "bg-blue-500 hover:bg-blue-500/90",
  SUCCESS: "bg-green-500 hover:bg-green-500/90",
  FAILED: "bg-red-500 hover:bg-red-500/90",
  CANCELLED: "bg-gray-500 hover:bg-gray-500/90",
};

export default function StatusBadge({ status }: StatusBadgeProps) {
  const s = status as Status;
  const config = statusConfig[s] ?? { label: status, variant: "secondary" as const };
  const color = statusColors[s] ?? "bg-gray-500 hover:bg-gray-500/90";

  return (
    <Badge className={`${color} text-white`}>
      {config.label}
    </Badge>
  );
}
