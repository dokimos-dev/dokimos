import { cn } from "@/lib/utils";

interface ScoreCellProps {
  score: number;
  success: boolean;
}

export default function ScoreCell({ score, success }: ScoreCellProps) {
  return (
    <span
      className={cn({
        "text-green-500": success,
        "text-red-500": !success,
      })}
    >
      {score.toFixed(2)}
    </span>
  );
}
