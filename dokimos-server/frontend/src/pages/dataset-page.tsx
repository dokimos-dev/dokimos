import DatasetsPage from "./datasets-page";

// The `/datasets/:name` route renders the same list + main-pane layout as
// `/datasets`. The URL param drives the selected dataset, which highlights
// the row on the left and renders its versions and items on the right. This
// keeps a single source of truth for layout while matching the design
// reference where the list stays visible alongside the detail content.
export default function DatasetPage() {
  return <DatasetsPage />;
}
