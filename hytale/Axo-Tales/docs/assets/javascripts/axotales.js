
(() => {
  let lifecycle;
  let currentArticle;
  function initGuidebook() {
    const article = document.querySelector(".md-content__inner");
    if (!article || article === currentArticle) return;
    lifecycle?.abort();
    currentArticle = article;
    lifecycle = new AbortController();
    const { signal } = lifecycle;
    article.querySelectorAll(".md-typeset__table > table").forEach((table) => {
      table.tabIndex = 0;
    });
    if (!article.querySelector('a[id^="item-"]')) return;

    article
      .querySelectorAll(".catalog-tools, .catalog-empty")
      .forEach((element) => element.remove());
    const previousResults = article.querySelector("#catalog-results");
    if (previousResults)
      previousResults.replaceWith(...previousResults.childNodes);
    const groups = Array.from(article.querySelectorAll("h2"))
      .map((heading) => {
        const wrapper = heading.nextElementSibling;
        const table = wrapper?.querySelector("table");
        if (!table) return null;
        wrapper.classList.add("catalog-table");
        heading.classList.add("catalog-heading");
        return {
          heading,
          wrapper,
          name: heading.firstChild.textContent.trim(),
          rows: Array.from(table.tBodies[0].rows).map((row) => ({
            element: row,
            text: row.textContent.toLocaleLowerCase(),
          })),
        };
      })
      .filter(Boolean);
    if (!groups.length) return;
    const tools = document.createElement("section");
    tools.className = "catalog-tools";
    tools.setAttribute("aria-label", "Filter item catalog");
    tools.innerHTML = `
      <div class="catalog-fields">
        <label for="catalog-query">Find an item or ingredient
          <input id="catalog-query" type="search" placeholder="Try mana, Mithril or Leech" autocomplete="off" aria-controls="catalog-results">
        </label>
        <label for="catalog-category">Category
          <select id="catalog-category" aria-controls="catalog-results"><option value="">All categories</option></select>
        </label>
      </div>
      <div class="catalog-summary">
        <p class="catalog-count" role="status" aria-live="polite" aria-atomic="true"></p>
        <button class="catalog-reset" type="button" hidden>Clear filters</button>
      </div>`;
    const empty = document.createElement("p");
    empty.className = "catalog-empty";
    empty.textContent =
      "No matching items. Try another name or ingredient, or clear the filters.";
    empty.hidden = true;
    const results = document.createElement("div");
    results.id = "catalog-results";
    groups[0].heading.before(tools, empty, results);
    groups.forEach(({ heading, wrapper, name }) => {
      const option = document.createElement("option");
      option.value = option.textContent = name;
      tools.querySelector("select").append(option);
      results.append(heading, wrapper);
    });
    const input = tools.querySelector("input");
    const select = tools.querySelector("select");
    const reset = tools.querySelector("button");
    const count = tools.querySelector("[role=status]");
    const total = groups.reduce((sum, group) => sum + group.rows.length, 0);
    function filter() {
      const terms = input.value
        .trim()
        .toLocaleLowerCase()
        .split(/\s+/)
        .filter(Boolean);
      let visible = 0;
      groups.forEach((group) => {
        let matches = 0;
        group.rows.forEach(({ element, text }) => {
          const show =
            (!select.value || group.name === select.value) &&
            terms.every((term) => text.includes(term));
          element.hidden = !show;
          if (show) matches++;
        });
        group.heading.hidden = group.wrapper.hidden = matches === 0;
        visible += matches;
      });
      count.textContent = `${visible} of ${total} items`;
      empty.hidden = visible !== 0;
      reset.hidden = !input.value && !select.value;
    }
    function clear() {
      input.value = "";
      select.value = "";
      filter();
    }
    function revealHash() {
      if (!window.location.hash) return;
      let id;
      try {
        id = decodeURIComponent(window.location.hash.slice(1));
      } catch {
        return;
      }
      const target = document.getElementById(id);
      if (!target || !results.contains(target)) return;
      clear();
      target.scrollIntoView({ block: "start" });
    }
    input.addEventListener("input", filter, { signal });
    select.addEventListener("change", filter, { signal });
    reset.addEventListener(
      "click",
      () => {
        clear();
        input.focus();
      },
      { signal },
    );
    document.addEventListener(
      "click",
      (event) => {
        const link = event.target.closest?.('a[href*="#"]');
        if (!link) return;
        const url = new URL(link.href, window.location.href);
        if (url.pathname === window.location.pathname && url.hash) clear();
      },
      { signal },
    );
    window.addEventListener("hashchange", revealHash, { signal });
    filter();
    revealHash();
  }
  if (typeof document$ !== "undefined") document$.subscribe(initGuidebook);
  else if (document.readyState === "loading")
    document.addEventListener("DOMContentLoaded", initGuidebook, {
      once: true,
    });
  else initGuidebook();
})();
