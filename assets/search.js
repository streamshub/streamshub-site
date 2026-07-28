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
  - matches on literal substring rather than fuzzy edit-distance (see transformQuery/buildQuery
    below) and shows a highlighted preview snippet of where the match was found in the page body
*/}}

(function () {
  const searchDataURL = '{{ partial "docs/links/resource-precache" $searchData }}';
  const indexConfig = Object.assign({{ $searchConfig }}, {
    includeScore: true,
    includeMatches: true,
    useExtendedSearch: true,
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

  /**
   * Fuse's default matching is fuzzy (edit-distance) - on a long page-body field like "content"
   * this readily produces false positives (e.g. "flink" fuzzy-matching a Console page that never
   * mentions Flink at all, just because its scattered letters appear within a large enough blob
   * of unrelated text). Prefixing every word with "'" switches Fuse's extended-search syntax to
   * literal substring inclusion instead - each word must actually appear in the title/content as
   * written (case-insensitive), and multiple words are required together (space = logical AND).
   * This also gives cleaner, more predictable match indices to build the preview snippet from.
   * @param {String} raw
   * @returns {String}
   */
  function transformQuery(raw) {
    return raw
      .trim()
      .split(/\s+/)
      .filter(Boolean)
      .map(word => "'" + word)
      .join(' ');
  }

  function onQueryChange() {
    if (!input.value || !window.bookSearchIndex) {
      activeProject = null;
      renderFilters({});
      renderResults([]);
      return;
    }

    const hits = window.bookSearchIndex.search(transformQuery(input.value))
      .slice(0, RESULT_LIMIT)
      .map(hit => Object.assign({}, hit.item, { _matches: hit.matches }));

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
          const li = element('<li class="book-search-result"><a href><span class="book-search-result-title"></span><small></small><p class="book-search-result-snippet"></p></a></li>');
          const a = li.querySelector('a'), titleEl = li.querySelector('.book-search-result-title'), small = li.querySelector('small');
          const snippetEl = li.querySelector('.book-search-result-snippet');

          a.href = item.href;
          titleEl.textContent = item.title;
          small.textContent = item.section;

          const contentMatch = findContentMatch(item);
          if (contentMatch) {
            renderSnippet(snippetEl, contentMatch.value, contentMatch.indices);
          } else {
            snippetEl.remove();
          }

          results.appendChild(li);
        });
      });
    });
  }

  /**
   * @param {Object} item a search-data entry with a "_matches" array attached (from Fuse's
   *   includeMatches option)
   * @returns {Object|null} the page's full plain-text content ("value") plus the first matched
   *   character range within it ("indices"), or null if the match was only on the title (nothing
   *   to preview from the body)
   */
  function findContentMatch(item) {
    if (!item._matches) {
      return null;
    }

    const match = item._matches.find(m => m.key === 'content');
    if (!match || !match.indices || !match.indices.length) {
      return null;
    }

    return { value: match.value, indices: match.indices[0] };
  }

  /**
   * Builds a short "...before [match] after..." excerpt around a matched range, trimmed to word
   * boundaries so it doesn't start/end mid-word.
   * @param {String} text full field value the match was found in
   * @param {[Number, Number]} indices inclusive [start, end] character range of the match
   * @returns {Object} "prefix"/"match"/"suffix" string parts to render
   */
  function buildSnippet(text, indices) {
    const CONTEXT = 50;
    const start = indices[0];
    const end = indices[1] + 1; // Fuse's end index is inclusive; make it exclusive for slicing

    let from = Math.max(0, start - CONTEXT);
    let to = Math.min(text.length, end + CONTEXT);

    if (from > 0) {
      const spaceIdx = text.indexOf(' ', from);
      if (spaceIdx !== -1 && spaceIdx < start) {
        from = spaceIdx + 1;
      }
    }
    if (to < text.length) {
      const spaceIdx = text.lastIndexOf(' ', to);
      if (spaceIdx !== -1 && spaceIdx > end) {
        to = spaceIdx;
      }
    }

    return {
      prefix: (from > 0 ? '… ' : '') + text.slice(from, start),
      match: text.slice(start, end),
      suffix: text.slice(end, to) + (to < text.length ? ' …' : '')
    };
  }

  /**
   * Renders a snippet (prefix text + highlighted <mark> match + suffix text) into an element,
   * built from text nodes rather than innerHTML since the snippet contains raw page content.
   * @param {Element} el
   * @param {String} text
   * @param {[Number, Number]} indices
   */
  function renderSnippet(el, text, indices) {
    const snippet = buildSnippet(text, indices);
    el.appendChild(document.createTextNode(snippet.prefix));
    const mark = document.createElement('mark');
    mark.textContent = snippet.match;
    el.appendChild(mark);
    el.appendChild(document.createTextNode(snippet.suffix));
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
