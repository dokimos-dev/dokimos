import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";

interface TruncatedTextProps {
  text: string;
  maxLength: number;
}

export default function TruncatedText({ text, maxLength }: TruncatedTextProps) {
  if (text.length <= maxLength) {
    return <span>{text}</span>;
  }

  const truncated = text.slice(0, maxLength) + "...";

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <span className="cursor-help">{truncated}</span>
        </TooltipTrigger>
        <TooltipContent>
          <p className="max-w-xs wrap-break-word">{text}</p>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}
