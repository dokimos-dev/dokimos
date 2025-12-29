import { Link } from "react-router";

export default function Header() {
  return (
    <header className="border-b">
      <div className="max-w-6xl mx-auto flex h-14 items-center px-4">
        <Link to="/" className="text-lg font-semibold hover:opacity-80">
          dokimos
        </Link>
      </div>
    </header>
  );
}
