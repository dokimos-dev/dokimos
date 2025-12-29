// API client for Dokimos frontend
// Types match the backend DTOs in dev.dokimos.server.dto.v1

const BASE_URL = '/api/v1';

// SWR fetcher
export const fetcher = async <T>(url: string): Promise<T> => {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`API error: ${response.status}`);
  }
  return response.json();
};

// Helper to construct API URLs
export const apiUrl = (path: string) => `${BASE_URL}${path}`;

// Run status enum
export type RunStatus = "RUNNING" | "SUCCESS" | "FAILED" | "CANCELLED";

// GET /api/v1/projects
export interface ProjectSummary {
  id: string;
  name: string;
  experimentCount: number;
  createdAt: string;
}

// GET /api/v1/projects/{projectName}/experiments
export interface ExperimentSummary {
  id: string;
  name: string;
  createdAt: string;
  latestRun: LatestRunInfo | null;
}

export interface LatestRunInfo {
  runId: string;
  status: RunStatus;
  passRate: number | null;
  startedAt: string;
}

// GET /api/v1/experiments/{experimentId}/runs
export interface RunSummary {
  id: string;
  status: RunStatus;
  config: Record<string, unknown>;
  itemCount: number;
  passedCount: number;
  passRate: number | null;
  startedAt: string;
  completedAt: string | null;
}

// GET /api/v1/experiments/{experimentId}/trends
export interface TrendData {
  experimentName: string;
  runs: RunPoint[];
}

export interface RunPoint {
  runId: string;
  startedAt: string;
  passRate: number;
  totalItems: number;
  passedItems: number;
}

// GET /api/v1/runs/{runId}
export interface RunDetails {
  id: string;
  experimentName: string;
  projectName: string;
  status: RunStatus;
  config: Record<string, unknown>;
  totalItems: number;
  passedItems: number;
  passRate: number | null;
  startedAt: string;
  completedAt: string | null;
  items: Page<ItemSummary>;
}

export interface ItemSummary {
  id: string;
  input: string;
  expectedOutput: string | null;
  actualOutput: string;
  metadata: Record<string, unknown>;
  evalResults: EvalSummary[];
  createdAt: string;
}

export interface EvalSummary {
  id: string;
  evaluatorName: string;
  score: number;
  threshold: number | null;
  success: boolean;
  reason: string | null;
}

// Spring Data Page
export interface Page<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}
