import { useMemo, useState } from "react";
import { format } from "date-fns";
import {
  useGetDataset,
  useGetVersion,
  useListItems,
} from "@/lib/api/dataset-controller/dataset-controller";
import type {
  DatasetVersionDetails,
  VersionSummary,
} from "@/lib/api/generated.schemas";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import TruncatedText from "@/components/shared/truncated-text";
import Pagination from "@/components/shared/pagination";

const PAGE_SIZE = 50;

function stringify(value: unknown, fallback = ""): string {
  if (value == null) return fallback;
  if (typeof value === "string") return value;
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

function pickLatestVersion(versions: VersionSummary[] | undefined): VersionSummary | undefined {
  if (!versions || versions.length === 0) return undefined;
  return [...versions].sort((a, b) => (b.version ?? 0) - (a.version ?? 0))[0];
}

interface DatasetMainPaneProps {
  datasetName: string;
}

export default function DatasetMainPane({ datasetName }: DatasetMainPaneProps) {
  const [selectedVersion, setSelectedVersion] = useState<number | null>(null);
  const [currentPage, setCurrentPage] = useState(0);

  const {
    data: datasetResponse,
    error: datasetError,
    isLoading: datasetLoading,
  } = useGetDataset(datasetName);
  const dataset = datasetResponse?.data;

  const sortedVersions = useMemo(() => {
    return [...(dataset?.versions ?? [])].sort(
      (a, b) => (b.version ?? 0) - (a.version ?? 0)
    );
  }, [dataset?.versions]);

  // Default to the latest version once it loads; an explicit user choice sticks.
  // Adjusting state during render is the recommended alternative to a
  // state-syncing effect. Switching datasets remounts this component (keyed on
  // the name), which resets the selection.
  const latestVersion = pickLatestVersion(dataset?.versions)?.version ?? null;
  if (selectedVersion == null && latestVersion != null) {
    setSelectedVersion(latestVersion);
  }
  const versionString = selectedVersion != null ? String(selectedVersion) : "";

  const {
    data: versionResponse,
    error: versionError,
    isLoading: versionLoading,
  } = useGetVersion(datasetName, versionString, {
    swr: { enabled: !!datasetName && !!versionString },
  });
  const version = versionResponse?.data;

  const {
    data: itemsResponse,
    error: itemsError,
    isLoading: itemsLoading,
  } = useListItems(
    datasetName,
    versionString,
    { pageable: { page: currentPage, size: PAGE_SIZE } },
    { swr: { enabled: !!datasetName && !!versionString } }
  );
  const itemsPage = itemsResponse?.data;

  if (datasetLoading) {
    return (
      <div className="rounded-lg border border-border bg-card overflow-hidden">
        <div className="flex items-center gap-2 px-5 py-3.5 border-b border-border">
          <Skeleton className="h-3.5 w-32" />
        </div>
        <div className="p-5">
          <Skeleton className="h-3.5 w-3/5 mb-2" />
          <Skeleton className="h-3 w-2/5" />
        </div>
        <div className="border-t border-border p-5 space-y-3">
          <Skeleton className="h-3.5 w-full" />
          <Skeleton className="h-3.5 w-4/5" />
          <Skeleton className="h-3.5 w-full" />
          <Skeleton className="h-3.5 w-3/4" />
        </div>
      </div>
    );
  }

  if (datasetError) {
    return (
      <p className="text-sm text-destructive">Error loading dataset: {datasetError.message}</p>
    );
  }

  if (!dataset) {
    return <p className="text-sm text-muted-foreground">Dataset not found.</p>;
  }

  const hasVersions = sortedVersions.length > 0;
  const items = itemsPage?.content ?? [];

  return (
    <section className="rounded-lg border border-border bg-card overflow-hidden">
      <div className="flex flex-col gap-3 px-5 py-3.5 border-b border-border sm:flex-row sm:items-center sm:justify-between">
        <span className="font-mono text-[13px] font-semibold truncate">{dataset.name}</span>
        <div className="flex items-center gap-2 flex-wrap">
          {hasVersions && selectedVersion != null && (
            <select
              value={selectedVersion}
              onChange={(e) => {
                setSelectedVersion(Number(e.target.value));
                setCurrentPage(0);
              }}
              className="min-h-8 border border-border rounded-md px-2.5 py-1 text-xs font-mono bg-background text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/50"
              aria-label="Select version"
            >
              {sortedVersions.map((v) => (
                <option key={v.id ?? v.version} value={v.version ?? 0}>
                  v{v.version}
                </option>
              ))}
            </select>
          )}
          <Button variant="outline" size="sm" disabled title="Coming soon">
            Import JSON/CSV
          </Button>
          <Button size="sm" disabled title="Coming soon">
            <PlusIcon className="size-4" />
            Add item
          </Button>
        </div>
      </div>

      <div className="px-5 py-4 border-b border-border">
        {dataset.description && (
          <p className="font-prose text-[13px] text-muted-foreground mb-2 max-w-[64ch]">
            {dataset.description}
          </p>
        )}
        {hasVersions ? (
          <p className="font-mono text-[11.5px] text-muted-foreground tabular-nums">
            {formatProvenance(version, itemsPage?.totalElements)}
          </p>
        ) : (
          <p className="font-prose text-[12.5px] text-muted-foreground">
            No versions yet. Push a version from the SDK to get started.
          </p>
        )}
      </div>

      {!hasVersions ? (
        <div className="p-10 text-center">
          <h3 className="text-base font-semibold mb-2">No versions yet</h3>
          <p className="text-muted-foreground text-sm font-prose">
            Use the SDK to push items into <span className="font-mono">{dataset.name}</span>.
            Versions will appear here once they exist.
          </p>
        </div>
      ) : versionLoading || itemsLoading ? (
        <div className="p-5 space-y-3">
          <Skeleton className="h-3.5 w-full" />
          <Skeleton className="h-3.5 w-4/5" />
          <Skeleton className="h-3.5 w-full" />
          <Skeleton className="h-3.5 w-3/4" />
        </div>
      ) : versionError ? (
        <p className="p-5 text-sm text-destructive">Error loading version: {versionError.message}</p>
      ) : itemsError ? (
        <p className="p-5 text-sm text-destructive">Error loading items: {itemsError.message}</p>
      ) : items.length === 0 ? (
        <div className="p-10 text-center">
          <h3 className="text-base font-semibold mb-2">No items in this version</h3>
          <p className="text-muted-foreground text-sm font-prose">
            Push items to this dataset version via the SDK to see them here.
          </p>
        </div>
      ) : (
        <>
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-1/2 text-[11px] uppercase tracking-wider text-muted-foreground font-semibold">
                    Input
                  </TableHead>
                  <TableHead className="text-[11px] uppercase tracking-wider text-muted-foreground font-semibold">
                    Expected output
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.map((item) => (
                  <TableRow key={item.id ?? item.ordinal}>
                    <TableCell className="align-top font-mono text-[12.5px]">
                      <TruncatedText
                        text={stringify(item.inputs)}
                        maxLength={140}
                      />
                    </TableCell>
                    <TableCell className="align-top font-mono text-[12.5px]">
                      <TruncatedText
                        text={stringify(item.expectedOutputs, "—")}
                        maxLength={140}
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <div className="px-5 pb-1">
            <Pagination
              currentPage={itemsPage?.number ?? currentPage}
              totalItems={itemsPage?.totalElements ?? 0}
              pageSize={itemsPage?.size ?? PAGE_SIZE}
              onPageChange={setCurrentPage}
            />
          </div>
        </>
      )}
    </section>
  );
}

function PlusIcon({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      <path d="M12 5v14" />
      <path d="M5 12h14" />
    </svg>
  );
}

function formatProvenance(
  version: DatasetVersionDetails | undefined,
  totalItems: number | undefined,
): string {
  if (!version) return "";
  const parts: string[] = [];
  const itemCount = totalItems ?? version.itemCount;
  if (itemCount != null) {
    parts.push(`${itemCount} item${itemCount === 1 ? "" : "s"}`);
  }
  parts.push(`v${version.version}`);
  if (version.createdAt) {
    parts.push(`created ${format(new Date(version.createdAt), "MMM d")}`);
  }
  if (version.createdBy) {
    parts.push(`by ${version.createdBy}`);
  }
  return parts.join(" · ");
}
