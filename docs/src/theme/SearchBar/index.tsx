import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import OriginalSearchBar from "@theme-original/SearchBar";

import styles from "./styles.module.css";

// Opens the offline search in a centered modal on Cmd/Ctrl+K or "/".
export default function SearchBarModal(): ReactNode {
  const [open, setOpen] = useState(false);
  const overlayRef = useRef<HTMLDivElement>(null);
  const isMac = typeof navigator !== "undefined" && /Mac|iPhone|iPad/.test(navigator.platform);

  const close = useCallback(() => setOpen(false), []);

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      const inField =
        e.target instanceof HTMLElement &&
        (e.target.tagName === "INPUT" ||
          e.target.tagName === "TEXTAREA" ||
          e.target.isContentEditable);
      const cmdK = (e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k";
      const slash = e.key === "/" && !inField;
      if (cmdK || slash) {
        e.preventDefault();
        setOpen(true);
      } else if (e.key === "Escape") {
        setOpen(false);
      }
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, []);

  // Focus the input when the modal opens.
  useEffect(() => {
    if (!open) return;
    const input = overlayRef.current?.querySelector<HTMLInputElement>(".navbar__search-input");
    input?.focus();
  }, [open]);

  return (
    <>
      <button
        type="button"
        className={styles.trigger}
        aria-label="Search"
        onClick={() => setOpen(true)}
      >
        <svg width="15" height="15" viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path
            d="M14.4 14.4 18 18m-2-9a7 7 0 1 1-14 0 7 7 0 0 1 14 0Z"
            stroke="currentColor"
            strokeWidth="1.6"
            strokeLinecap="round"
          />
        </svg>
        <span className={styles.triggerLabel}>Search</span>
        <kbd className={styles.triggerKbd}>{isMac ? "⌘K" : "Ctrl K"}</kbd>
      </button>

      {open && (
        <div
          className={styles.backdrop}
          ref={overlayRef}
          onClick={(e) => {
            if (e.target === e.currentTarget) close();
          }}
        >
          <div className={styles.modal} role="dialog" aria-modal="true" aria-label="Search">
            <OriginalSearchBar />
            <div className={styles.modalHint}>
              <kbd>esc</kbd> to close
            </div>
          </div>
        </div>
      )}
    </>
  );
}
