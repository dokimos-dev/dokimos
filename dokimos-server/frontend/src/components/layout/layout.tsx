import { Outlet } from "react-router";
import Header from "./header";
import Breadcrumbs from "./breadcrumbs";
import { useBreadcrumbs } from "@/lib/breadcrumb-context";

export default function Layout() {
  const { breadcrumbs } = useBreadcrumbs();

  return (
    <div className="min-h-screen bg-background">
      <Header />
      <div className="max-w-6xl mx-auto px-4 py-4">
        <Breadcrumbs items={breadcrumbs} />
        <main className="mt-4">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
