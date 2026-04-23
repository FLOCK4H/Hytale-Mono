function initAxoTalesGuidebook() {
  const revealTargets = document.querySelectorAll("[data-reveal]");
  const tiltTargets = document.querySelectorAll(".tilt-card");

  if (!window.__axotalesRevealObserver) {
    window.__axotalesRevealObserver = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          entry.target.classList.add("is-visible");
          window.__axotalesRevealObserver.unobserve(entry.target);
        }
      }
    }, { threshold: 0.16 });
  }

  revealTargets.forEach((target) => {
    if (!target.classList.contains("is-visible")) {
      window.__axotalesRevealObserver.observe(target);
    }
  });

  tiltTargets.forEach((card) => {
    if (card.dataset.tiltBound === "true") {
      return;
    }

    card.dataset.tiltBound = "true";

    card.addEventListener("pointermove", (event) => {
      const bounds = card.getBoundingClientRect();
      const x = (event.clientX - bounds.left) / bounds.width;
      const y = (event.clientY - bounds.top) / bounds.height;
      const rotateY = (x - 0.5) * 8;
      const rotateX = (0.5 - y) * 8;

      card.style.setProperty("--ry", `${rotateY}deg`);
      card.style.setProperty("--rx", `${rotateX}deg`);
    });

    card.addEventListener("pointerleave", () => {
      card.style.setProperty("--ry", "0deg");
      card.style.setProperty("--rx", "0deg");
    });
  });
}

if (typeof document$ !== "undefined") {
  document$.subscribe(initAxoTalesGuidebook);
} else {
  window.addEventListener("DOMContentLoaded", initAxoTalesGuidebook);
}
