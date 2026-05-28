import { useEffect, useMemo, useState } from "react";
import { format } from "date-fns";
import {
  useGetDataset,
  useGetVersion,
  useListItems,
} from "@/lib/api/dataset-controller/dataset-controller";
import type { VersionSummary } from "@/lib/api/generated.schemas";
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

  // Default to the latest version when the dataset (or selection state) becomes available.
  useEffect(() => {
    if (selectedVersion == null) {
      const latest = pickLatestVersion(dataset?.versions);
      if (latest?.version != null) {
        setSelectedVersion(latest.version);
      }
    }
  }, [dataset?.versions, selectedVersion]);

  // Reset selection if we switch datasets.
  useEffect(() => {
    setSelectedVersion(null);
    setCurrentPage(0);
  }, [datasetName]);

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
      <div>
        <Skeleton className="h-6 w-40 mb-3" />
        <Skeleton className="h-4 w-64 mb-6" />
        <Skeleton className="h-48 w-full" />
      </div>
    );
  }

  if (datasetError) {
    return (
      <p className="text-destructive">Error loading dataset: {datasetError.message}</p>
    );
  }

  if (!dataset) {
    return <p className="text-muted-foreground">Dataset not found.</p>;
  }

  const hasVersions = sortedVersions.length > 0;
  const items = itemsPage?.content ?? [];

  return (
    <div>
      <div className="flex items-center gap-3 flex-wrap mb-1">
        <h2 className="text-lg font-bold">{dataset.name}</h2>
        {hasVersions && selectedVersion != null && (
          <select
            value={selectedVersion}
            onChange={(e) => {
              setSelectedVersion(Number(e.target.value));
              setCurrentPage(0);
            }}
            className="min-h-9 border rounded-md px-3 py-1 text-xs bg-card text-foreground"
            aria-label="Select version"
          >
            {sortedVersions.map((v) => (
              <option key={v.id ?? v.version} value={v.version ?? 0}>
                version v{v.version}
              </option>
            ))}
          </select>
        )}
      </div>

      {hasVersions ? (
        <p className="text-xs text-muted-foreground mb-4">
          {formatProvenance(version, itemsPage?.totalElements)}
        </p>
      ) : (
        <p className="text-xs text-muted-foreground mb-4">
          No versions yet. Push a version from the SDK to get started.
        </p>
      )}

      <div className="flex justify-between items-center gap-2 mb-3 flex-wrap">
        <div className="text-sm text-muted-foreground">
          {dataset.description}
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" disabled title="Coming soon">
            Import JSON/CSV
          </Button>
          <Button size="sm" disabled title="Coming soon">
            + Add item
          </Button>
        </div>
      </div>

      {!hasVersions ? (
        <div className="rounded-xl border bg-card p-10 text-center">
          <h3 className="text-base font-semibold mb-2">No versions yet</h3>
          <p className="text-muted-foreground text-sm">
            Use the SDK to push items into <span className="font-mono">{dataset.name}</span>.
            Versions will appear here once they exist.
          </p>
        </div>
      ) : versionLoading || itemsLoading ? (
        <Skeleton className="h-48 w-full" />
      ) : versionError ? (
        <p className="text-destructive">Error loading version: {versionError.message}</p>
      ) : itemsError ? (
        <p className="text-destructive">Error loading items: {itemsError.message}</p>
      ) : items.length === 0 ? (
        <div className="rounded-xl border bg-card p-10 text-center">
          <h3 className="text-base font-semibold mb-2">No items in this version</h3>
          <p className="text-muted-foreground text-sm">
            Push items to this dataset version via the SDK to see them here.
          </p>
        </div>
      ) : (
        <>
          <div className="rounded-xl border overflow-hidden">
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-1/2">Input</TableHead>
                    <TableHead>Expected output</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {items.map((item) => (
                    <TableRow key={item.id ?? item.ordinal}>
                      <TableCell className="align-top">
                        <TruncatedText
                          text={stringify(item.inputs)}
                          maxLength={140}
                        />
                      </TableCell>
                      <TableCell className="align-top">
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
          </div>
          <Pagination
            currentPage={itemsPage?.number ?? currentPage}
            totalItems={itemsPage?.totalElements ?? 0}
            pageSize={itemsPage?.size ?? PAGE_SIZE}
            onPageChange={setCurrentPage}
          />
        </>
      )}
    </div>
  );
}

function formatProvenance(version: VersionSummary | undefined, totalItems: number | undefined): string {
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
