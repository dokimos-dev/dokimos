interface JsonDisplayProps {
  data: string | object | null | undefined;
}

export default function JsonDisplay({ data }: JsonDisplayProps) {
  if (data == null) {
    return (
      <pre className="font-mono text-sm text-muted-foreground bg-muted p-4 rounded-md overflow-auto">
        —
      </pre>
    );
  }

  let parsed: unknown;

  if (typeof data === "string") {
    if (data === "") {
      return (
        <pre className="font-mono text-sm text-muted-foreground bg-muted p-4 rounded-md overflow-auto">
          —
        </pre>
      );
    }
    try {
      parsed = JSON.parse(data);
    } catch {
      return (
        <pre className="font-mono text-sm text-muted-foreground bg-muted p-4 rounded-md overflow-auto">
          {data}
        </pre>
      );
    }
  } else {
    parsed = data;
  }

  const formatted = JSON.stringify(parsed, null, 2);

  return (
    <pre className="font-mono text-sm text-muted-foreground bg-muted p-4 rounded-md overflow-auto">
      {formatted}
    </pre>
  );
}
