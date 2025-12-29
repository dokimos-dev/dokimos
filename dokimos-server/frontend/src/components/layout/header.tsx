import { Link } from "react-router";
import { useCallback } from "react";
import { useTheme } from "@/lib/theme-context";
import {
  ThemeToggleButton,
  useThemeTransition,
} from "@/components/ui/shadcn-io/theme-toggle-button";

export default function Header() {
  const { theme, toggleTheme } = useTheme();
  const { startTransition } = useThemeTransition();

  const handleThemeToggle = useCallback(() => {
    startTransition(() => {
      toggleTheme();
    });
  }, [toggleTheme, startTransition]);

  return (
    <header className="border-b">
      <div className="max-w-6xl mx-auto flex h-14 items-center justify-between px-4">
        <Link
          to="/"
          className="text-lg font-semibold hover:opacity-80 flex items-center"
        >
          <img
            src="/logo.jpeg"
            alt="Dokimos Logo"
            className="inline h-7 mr-2"
          />
          Dokimos
        </Link>
        <ThemeToggleButton
          theme={theme}
          onClick={handleThemeToggle}
          variant="circle"
          start="top-right"
        />
      </div>
    </header>
  );
}
