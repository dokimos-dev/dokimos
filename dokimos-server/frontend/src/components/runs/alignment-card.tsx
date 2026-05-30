import { useAlignment } from "@/lib/api/alignment-controller/alignment-controller";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";

function AlignmentRate({ rate }: { rate?: number | null }) {
  if (rate == null) {
    return <span className="text-muted-foreground">n/a</span>;
  }

  const percentage = (rate * 100).toFixed(1);
  const colorClass = cn({
    "text-destructive": rate < 0.5,
    "text-warning": rate >= 0.5 && rate < 0.8,
    "text-success": rate >= 0.8,
  });

  return (
    <span className={cn("tabular-nums", colorClass)}>{percentage}%</span>
  );
}

interface AlignmentCardProps {
  runId: string;
}

export default function AlignmentCard({ runId }: AlignmentCardProps) {
  const { data: response } = useAlignment(runId, {
    swr: { enabled: !!runId },
  });
  const alignment = response?.data;

  const annotatedItems = alignment?.annotatedItems ?? 0;
  const evaluators = alignment?.evaluators ?? [];

  if (annotatedItems === 0 && evaluators.length === 0) {
    return null;
  }

  return (
    <Card className="mb-6">
      <CardHeader>
        <CardTitle>Judge vs human alignment</CardTitle>
      </CardHeader>
      <CardContent>
        <p className="text-sm text-muted-foreground mb-4">
          {annotatedItems} {annotatedItems === 1 ? "item" : "items"} annotated.
        </p>
        {evaluators.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            No evaluator alignment available yet.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Evaluator</TableHead>
                  <TableHead>Agreement</TableHead>
                  <TableHead>Comparable</TableHead>
                  <TableHead>Agreed</TableHead>
                  <TableHead>Excluded (unsure)</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {evaluators.map((evaluator) => (
                  <TableRow key={evaluator.evaluatorName}>
                    <TableCell className="font-medium">
                      {evaluator.evaluatorName}
                    </TableCell>
                    <TableCell>
                      <AlignmentRate rate={evaluator.alignmentRate} />
                    </TableCell>
                    <TableCell className="tabular-nums">
                      {evaluator.comparableCount ?? 0}
                    </TableCell>
                    <TableCell className="tabular-nums">
                      {evaluator.agreedCount ?? 0}
                    </TableCell>
                    <TableCell className="tabular-nums">
                      {evaluator.excludedUnsure ?? 0}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
