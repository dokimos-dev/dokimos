import { createBrowserRouter } from "react-router";
import Layout from "@/components/layout/layout";
import Dashboard from "@/pages/dashboard";
import ProjectPage from "@/pages/project-page";
import ExperimentPage from "@/pages/experiment-page";
import RunPage from "@/pages/run-page";
import RunDiffPage from "@/pages/run-diff-page";
import DatasetsPage from "@/pages/datasets-page";
import DatasetPage from "@/pages/dataset-page";
import ConnectionsPage from "@/pages/connections-page";
import ReviewQueuePage from "@/pages/review-queue-page";
import TracesPage from "@/pages/traces-page";
import TraceDetailPage from "@/pages/trace-detail-page";
import TraceEvalRulesPage from "@/pages/trace-eval-rules-page";

export const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      {
        path: "/",
        element: <Dashboard />,
      },
      {
        path: "/projects/:name",
        element: <ProjectPage />,
      },
      {
        path: "/experiments/:id",
        element: <ExperimentPage />,
      },
      {
        path: "/runs/:id",
        element: <RunPage />,
      },
      {
        path: "/experiments/:experimentId/runs/:candidateRunId/diff",
        element: <RunDiffPage />,
      },
      {
        path: "/datasets",
        element: <DatasetsPage />,
      },
      {
        path: "/datasets/:name",
        element: <DatasetPage />,
      },
      {
        path: "/llm-connections",
        element: <ConnectionsPage />,
      },
      {
        path: "/review-queue",
        element: <ReviewQueuePage />,
      },
      {
        path: "/traces",
        element: <TracesPage />,
      },
      {
        path: "/traces/:id",
        element: <TraceDetailPage />,
      },
      {
        path: "/trace-eval-rules",
        element: <TraceEvalRulesPage />,
      },
    ],
  },
]);
