import { createBrowserRouter } from "react-router";
import Layout from "@/components/layout/layout";
import Dashboard from "@/pages/dashboard";
import ProjectPage from "@/pages/project-page";
import ExperimentPage from "@/pages/experiment-page";
import RunPage from "@/pages/run-page";

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
    ],
  },
]);
