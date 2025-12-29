interface JsonDisplayProps {
  data: string | object;
}

export default function JsonDisplay({ data }: JsonDisplayProps) {
  let parsed: object;

  if (typeof data === "string") {
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
