# Dokimos Frontend

React frontend for the Dokimos evaluation dashboard.

## Tech Stack

- React 19 with TypeScript
- Vite for development and building
- Tailwind CSS v4 for styling
- ShadCN UI components
- SWR for data fetching
- React Router v7

## Development

```bash
# Install dependencies
npm install

# Start development server
npm run dev
```

The development server runs at http://localhost:5173 and proxies `/api` requests to the backend at http://localhost:8080.

## API Client Generation

This project uses [Orval](https://orval.dev/) to generate typed API hooks from the backend's OpenAPI spec.

### Generating the API Client

1. Start the backend server (it must be running to serve the OpenAPI spec)
2. Run the generation command:

```bash
npm run generate-api
```

This generates typed SWR hooks in `src/lib/api/generated.ts`.

### When to Regenerate

Run `npm run generate-api` whenever:
- API endpoints are added or modified
- Request/response types change
- You pull changes that modify the API

For continuous regeneration during development:

```bash
npm run generate-api:watch
```

## Building

```bash
npm run build
```

The build output goes to `dist/` and is included in the Maven build via `frontend-maven-plugin`.

## Linting

```bash
npm run lint
```
