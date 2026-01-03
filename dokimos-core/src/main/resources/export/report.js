document.querySelectorAll(".expandable").forEach((row) => {
  row.addEventListener("click", () => {
    row.classList.toggle("expanded");
    const details = row.nextElementSibling;
    if (details && details.classList.contains("details")) {
      details.classList.toggle("show");
    }
  });
});
document.querySelectorAll("th[data-sort]").forEach((th) => {
  th.addEventListener("click", (e) => {
    if (e.target.tagName === "TH") sortTable(th);
  });
});
function sortTable(th) {
  const table = th.closest("table");
  const tbody = table.querySelector("tbody");
  const rows = Array.from(tbody.querySelectorAll("tr:not(.details)"));
  const col = th.cellIndex;
  const asc = th.dataset.asc !== "true";
  rows.sort((a, b) => {
    let va = a.cells[col].textContent.trim();
    let vb = b.cells[col].textContent.trim();
    const na = parseFloat(va.replace("%", ""));
    const nb = parseFloat(vb.replace("%", ""));
    if (!isNaN(na) && !isNaN(nb)) {
      va = na;
      vb = nb;
    }
    return asc ? (va > vb ? 1 : -1) : va < vb ? 1 : -1;
  });
  th.dataset.asc = asc;
  rows.forEach((row) => {
    const details = row.nextElementSibling;
    tbody.appendChild(row);
    if (details && details.classList.contains("details"))
      tbody.appendChild(details);
  });
}
