function initAxoTalesGuidebook() {
  document.documentElement.classList.add("axotales-ready");
  initCardRails();
}

function initCardRails() {
  const railSelectors = [".card-grid", ".stat-strip", ".timeline-grid"];
  const railTargets = document.querySelectorAll(railSelectors.join(", "));

  railTargets.forEach((grid) => {
    if (grid.dataset.cardRailReady === "true") {
      return;
    }

    const cards = Array.from(grid.children).filter((child) => child.nodeType === Node.ELEMENT_NODE);
    if (cards.length <= 3) {
      return;
    }

    grid.dataset.cardRailReady = "true";
    grid.classList.add("card-rail");

    const shell = document.createElement("div");
    shell.className = "card-rail-shell";

    const controls = document.createElement("div");
    controls.className = "card-rail__controls";

    const previous = createRailButton("Previous cards", "←");
    const next = createRailButton("Next cards", "→");

    controls.append(previous, next);

    grid.parentNode.insertBefore(shell, grid);
    shell.append(grid, controls);

    const scrollByPage = (direction) => {
      const firstCard = grid.firstElementChild;
      const computed = window.getComputedStyle(grid);
      const gap = parseFloat(computed.columnGap || computed.gap || "16");
      const cardWidth = firstCard ? firstCard.getBoundingClientRect().width : grid.clientWidth;
      const multiplier = window.matchMedia("(max-width: 760px)").matches
        ? 1
        : window.matchMedia("(max-width: 1100px)").matches
          ? 2
          : 3;
      const amount = (cardWidth + gap) * multiplier;
      grid.scrollBy({ left: direction * amount, behavior: "smooth" });
    };

    const syncLayout = () => {
      const computed = window.getComputedStyle(grid);
      const gap = parseFloat(computed.columnGap || computed.gap || "16");
      const visible = window.matchMedia("(max-width: 760px)").matches
        ? 1
        : window.matchMedia("(max-width: 1100px)").matches
          ? 2
          : 3;
      const viewportWidth = Math.max(0, shell.getBoundingClientRect().width);
      const width = Math.max(220, (viewportWidth - (gap * (visible - 1))) / visible);
      grid.style.setProperty("--ax-rail-card-width", `${width}px`);
    };

    const syncButtons = () => {
      const maxScrollLeft = Math.max(0, grid.scrollWidth - grid.clientWidth - 2);
      previous.disabled = grid.scrollLeft <= 2;
      next.disabled = grid.scrollLeft >= maxScrollLeft;
    };

    const syncRail = () => {
      syncLayout();
      syncButtons();
    };

    previous.addEventListener("click", () => scrollByPage(-1));
    next.addEventListener("click", () => scrollByPage(1));
    grid.addEventListener("scroll", syncButtons, { passive: true });
    window.addEventListener("resize", syncRail);
    syncRail();
  });
}

function createRailButton(label, glyph) {
  const button = document.createElement("button");
  button.type = "button";
  button.className = "card-rail__button";
  button.setAttribute("aria-label", label);

  const icon = document.createElement("span");
  icon.setAttribute("aria-hidden", "true");
  icon.textContent = glyph;
  button.append(icon);

  return button;
}

if (typeof document$ !== "undefined") {
  document$.subscribe(initAxoTalesGuidebook);
} else {
  window.addEventListener("DOMContentLoaded", initAxoTalesGuidebook);
}
