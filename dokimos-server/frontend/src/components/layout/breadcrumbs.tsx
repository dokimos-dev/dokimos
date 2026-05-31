import { Link } from "react-router";

export interface BreadcrumbItem {
  label: string;
  href: string;
}

interface BreadcrumbsProps {
  items: BreadcrumbItem[];
}

export default function Breadcrumbs({ items }: BreadcrumbsProps) {
  if (items.length === 0) {
    return null;
  }

  return (
    <nav className="flex items-center gap-2 truncate text-[12px] text-muted-foreground">
      {items.map((item, index) => {
        const isLast = index === items.length - 1;
        return (
          <span key={item.href} className="flex items-center gap-2">
            {index > 0 && <span className="text-faint">/</span>}
            {isLast ? (
              <span className="truncate font-medium text-foreground">{item.label}</span>
            ) : (
              <Link to={item.href} className="truncate transition-colors hover:text-foreground">
                {item.label}
              </Link>
            )}
          </span>
        );
      })}
    </nav>
  );
}
