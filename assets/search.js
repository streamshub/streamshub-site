'use strict';

{{ $searchDataFile := printf "%s.search-data.json" .Language.Lang }}
{{ $searchData := resources.Get "search-data.json" | resources.ExecuteAsTemplate $searchDataFile . | resources.Minify | resources.Fingerprint }}
{{ $searchConfig := i18n "bookSearchConfig" | default "{}" }}

{{/*
  StreamsHub override of the hugo-book theme's assets/search.js.

  The theme's version renders a flat, hard-capped-at-10 <li> list with no grouping. This version:
  - raises the cap (RESULT_LIMIT) since results are now grouped rather than a single scroll list
  - renders a row of per-project filter buttons (#book-search-filters, added in
    layouts/_partials/search-modal.html) - highlighted if the current query has matches in that
    project, disabled/gray otherwise
  - clicking a filter button narrows the results list to just that project (click "All" to reset)
  - groups the results list itself by project, then by sub-group within a project (e.g. Console's
    "Documentation" vs "Quick Start"), using the "project"/"projectLabel"/"group" fields added to
    the search index by assets/search-data.json
*/}}

(function () {
  const searchDataURL = '{{ partial "docs/links/resource-precache" $searchData }}';
  const indexConfig = Object.assign({{ $searchConfig }}, {
    includeScore: true,
    useExtendedSearch: true,
    fieldNormWeight: 1.5,
    threshold: 0.2,
    ignoreLocation: true,
    keys: [
      {
        name: 'title',
        weight: 0.7
      },
      {
        name: 'content',
        weight: 0.3
      }
    ]
  });

  // Enough headroom that grouping/filtering has something to work with, without the modal
  // scrolling forever on a broad query. This site has ~40 indexed pages total.
  const RESULT_LIMIT = 200;

  // Preferred display order for filter buttons / result groups; anything indexed that isn't in
  // this list (e.g. orphaned tutorial content) is appended afterward, alphabetically by key.
  const PROJECT_ORDER = ['console', 'flink-sql-runner', 'strimzi-mcp-server', 'explore', 'announcements', 'community', 'home'];

  const input = document.querySelector('#book-search-input');
  const filters = document.querySelector('#book-search-filters');
  const results = document.querySelector('#book-search-results');

  if (!input) {
    return
  }

  // Full set of {key, label} projects present in the index, computed once at load - independent
  // of the current query, so every button always renders (just disabled when there's no match).
  let allProjects = [];
  // Currently selected filter button; null means "All".
  let activeProject = null;

  input.addEventListener('focus', init);
  input.addEventListener('keyup', onQueryChange);

  document.addEventListener('keypress', focusSearchFieldOnKeyPress);

  /**
   * @param {Event} event
   */
  function focusSearchFieldOnKeyPress(event) {
    if (event.target.value !== undefined) {
      return;
    }

    if (input === document.activeElement) {
      return;
    }

    const characterPressed = String.fromCharCode(event.charCode);
    if (!isHotkey(characterPressed)) {
      return;
    }

    input.focus();
    event.preventDefault();
  }

  /**
   * @param {String} character
   * @returns {Boolean}
   */
  function isHotkey(character) {
    const dataHotkeys = input.getAttribute('data-hotkeys') || '';
    return dataHotkeys.indexOf(character) >= 0;
  }

  function init() {
    input.removeEventListener('focus', init); // init once
    input.required = true;

    fetch(searchDataURL)
      .then(pages => pages.json())
      .then(pages => {
        window.bookSearchIndex = new Fuse(pages, indexConfig);
        allProjects = collectProjects(pages);
      })
      .then(() => input.required = false)
      .then(onQueryChange);
  }

  /**
   * @param {Array} pages
   * @returns {Array} [{key, label}, ...] every distinct project in the index, in display order
   */
  function collectProjects(pages) {
    const labels = new Map();
    pages.forEach(function (page) {
      if (!labels.has(page.project)) {
        labels.set(page.project, page.projectLabel || page.project);
      }
    });

    const known = PROJECT_ORDER.filter(key => labels.has(key));
    const extra = Array.from(labels.keys())
      .filter(key => PROJECT_ORDER.indexOf(key) === -1)
      .sort();

    return known.concat(extra).map(key => ({ key, label: labels.get(key) }));
  }

  function onQueryChange() {
    if (!input.value || !window.bookSearchIndex) {
      activeProject = null;
      renderFilters({});
      renderResults([]);
      return;
    }

    const hits = window.bookSearchIndex.search(input.value).slice(0, RESULT_LIMIT).map(hit => hit.item);

    const counts = {};
    hits.forEach(function (item) {
      counts[item.project] = (counts[item.project] || 0) + 1;
    });

    // If the active filter's project no longer has any matches for the new query, fall back to
    // "All" rather than leaving the results panel stuck empty with no visible way back.
    if (activeProject && !counts[activeProject]) {
      activeProject = null;
    }

    renderFilters(counts);

    const visible = activeProject ? hits.filter(item => item.project === activeProject) : hits;
    renderResults(visible);
  }

  /**
   * @param {Object} counts project key -> number of current matches
   */
  function renderFilters(counts) {
    if (!filters) {
      return;
    }

    while (filters.firstChild) {
      filters.removeChild(filters.firstChild);
    }

    if (!input.value) {
      return;
    }

    const total = Object.keys(counts).reduce((sum, key) => sum + counts[key], 0);
    if (!total) {
      return;
    }

    filters.appendChild(makeFilterButton('All', null, total));

    allProjects.forEach(function (project) {
      filters.appendChild(makeFilterButton(project.label, project.key, counts[project.key] || 0));
    });
  }

  /**
   * @param {String} label
   * @param {String|null} projectKey null for the "All" button
   * @param {Number} count
   * @returns {HTMLButtonElement}
   */
  function makeFilterButton(label, projectKey, count) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'book-search-filter' + (activeProject === projectKey ? ' active' : '');
    button.textContent = label;
    button.disabled = count === 0;
    button.addEventListener('click', function () {
      activeProject = projectKey;
      onQueryChange();
    });
    return button;
  }

  /**
   * @param {Array} items matching search-data entries, already filtered to the active project
   */
  function renderResults(items) {
    while (results.firstChild) {
      results.removeChild(results.firstChild);
    }

    if (!input.value) {
      return;
    }

    if (!items.length) {
      results.appendChild(element('<li class="book-search-empty">No results found.</li>'));
      return;
    }

    const projectOrder = allProjects.map(p => p.key);
    const byProject = groupBy(items, item => item.project);
    byProject.sort((a, b) => projectOrder.indexOf(a.key) - projectOrder.indexOf(b.key));

    byProject.forEach(function (project) {
      const heading = element('<li class="book-search-heading"></li>');
      heading.textContent = project.items[0].projectLabel || project.key;
      results.appendChild(heading);

      const byGroup = groupBy(project.items, item => item.group || '');
      byGroup.forEach(function (group) {
        if (group.key) {
          const subheading = element('<li class="book-search-subheading"></li>');
          subheading.textContent = group.key;
          results.appendChild(subheading);
        }

        group.items.forEach(function (item) {
          // Title and section both live inside the <a> (rather than <a> then a sibling
          // <small>) so the whole row is clickable, not just the title text.
          const li = element('<li class="book-search-result"><a href><span class="book-search-result-title"></span><small></small></a></li>');
          const a = li.querySelector('a'), titleEl = li.querySelector('.book-search-result-title'), small = li.querySelector('small');

          a.href = item.href;
          titleEl.textContent = item.title;
          small.textContent = item.section;

          results.appendChild(li);
        });
      });
    });
  }

  /**
   * Groups items into buckets keyed by keyFn(item), preserving first-seen order.
   * @param {Array} items
   * @param {Function} keyFn
   * @returns {Array} [{key, items: [...]}, ...]
   */
  function groupBy(items, keyFn) {
    const order = [];
    const buckets = new Map();
    items.forEach(function (item) {
      const key = keyFn(item);
      if (!buckets.has(key)) {
        buckets.set(key, []);
        order.push(key);
      }
      buckets.get(key).push(item);
    });
    return order.map(key => ({ key, items: buckets.get(key) }));
  }

  /**
   * @param {String} content
   * @returns {Node}
   */
  function element(content) {
    const div = document.createElement('div');
    div.innerHTML = content;
    return div.firstChild;
  }
})();
