const API = {
    datasetStatus: "/dataset/status",
    datasetInitialize: "/dataset/initialize",
    filters: "/filters",
    search: "/search",
    autocomplete: "/autocomplete"
};

const INITIAL_MODE = "hybrid";
const INITIAL_RESULTS_HEADING = "Chunk-Level Matches";
const INITIAL_RESULTS_SUBTITLE =
    "Use filters or a natural-language query to inspect Redis OM Spring ranking.";
const INITIAL_SINGLE_RESULTS_EMPTY_STATE =
    '<div class="empty-state">Run a retrieval query to inspect ranked FY2025 filing chunks.</div>';

const state = {
    mode: INITIAL_MODE,
    filters: {
        coverage: {
            indexedCompanies: 0,
            targetCompanies: 500
        },
        sectors: [],
        sections: []
    },
    dataset: {
        initialized: false,
        minCompanyCount: 10,
        maxCompanyCount: 500
    },
    selections: {
        sectors: [],
        sections: []
    },
    autocompleteIndex: -1,
    activeAutocompleteField: null
};

function debounce(callback, delay) {
    let timeoutId;
    return (...args) => {
        window.clearTimeout(timeoutId);
        timeoutId = window.setTimeout(() => callback(...args), delay);
    };
}

function escapeHtml(value = "") {
    return String(value ?? "")
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;");
}

function highlightTerms(text, query, enabled) {
    const safeText = escapeHtml(text || "");
    if (!enabled || !query || !query.trim()) {
        return safeText;
    }

    const terms = [...new Set(query.trim().split(/\s+/).filter(term => term.length > 1))];
    if (terms.length === 0) {
        return safeText;
    }

    const pattern = new RegExp(`(${terms.map(term => term.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")).join("|")})`, "gi");
    return safeText.replace(pattern, "<mark>$1</mark>");
}

function modeClass(value = "") {
    return value.toString().toLowerCase().replace(/[^a-z0-9]+/g, "-");
}

function normalizeSuggestions(payload) {
    const suggestions = payload?.suggestions || payload || [];
    return suggestions.map(item => {
        if (typeof item === "string") {
            return { label: item, value: item };
        }
        return {
            label: item.label || item.text || item.value || item.name || "",
            value: item.value || item.text || item.label || item.name || "",
            hint: item.hint || item.payload || item.type || ""
        };
    });
}

function autocompleteContainerId(field) {
    if (field === "companyName") {
        return "company-autocomplete";
    }
    if (field === "ticker") {
        return "ticker-autocomplete";
    }
    return `${field}-autocomplete`;
}

async function fetchJson(url, options = {}) {
    const response = await fetch(url, options);
    if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
    }
    return response.json();
}

async function loadDatasetStatus() {
    const payload = await fetchJson(API.datasetStatus);
    state.dataset = {
        initialized: Boolean(payload.initialized),
        minCompanyCount: payload.minCompanyCount || 10,
        maxCompanyCount: payload.maxCompanyCount || 500
    };

    state.filters.coverage = payload.coverage || state.filters.coverage;
    renderCoverage();
    updateDatasetSetupBounds();
    toggleDatasetSetup(!state.dataset.initialized);
    setSearchEnabled(state.dataset.initialized);
}

async function loadFilters() {
    try {
        const payload = await fetchJson(API.filters);
        state.filters = {
            coverage: payload.coverage || state.filters.coverage,
            sectors: payload.sectors || payload.filters?.sectors || [],
            sections: payload.sections || payload.filters?.sections || []
        };
    } catch (error) {
        console.error("Unable to load live filters", error);
        state.filters = {
            coverage: {
                indexedCompanies: 0,
                targetCompanies: 500
            },
            sectors: [],
            sections: []
        };
    }

    renderCoverage();
    renderOptionList("sector", state.filters.sectors);
    renderOptionList("section", state.filters.sections);
    renderSelectedPills("sector");
    renderSelectedPills("section");
}

function renderCoverage() {
    const coverage = state.filters.coverage || {};
    document.getElementById("coverage-status").textContent =
        `${coverage.indexedCompanies || 0} / ${coverage.targetCompanies || 500} companies indexed`;
}

function updateDatasetSetupBounds() {
    const range = document.getElementById("company-count-range");
    if (!range) {
        return;
    }
    range.min = state.dataset.minCompanyCount;
    range.max = state.dataset.maxCompanyCount;
    range.step = 1;
    if (Number(range.value) < Number(range.min) || Number(range.value) > Number(range.max)) {
        range.value = Math.min(Math.max(Number(range.value), Number(range.min)), Number(range.max));
    }
    renderDatasetSetupValue();
}

function renderDatasetSetupValue() {
    const range = document.getElementById("company-count-range");
    const value = Number(range.value);
    document.getElementById("company-count-value").textContent = `${value} companies`;
}

function toggleDatasetSetup(visible) {
    const modal = document.getElementById("dataset-setup-modal");
    modal.classList.toggle("hidden", !visible);
}

function setSearchEnabled(enabled) {
    const form = document.getElementById("search-form");
    form.querySelectorAll("input, select, textarea, button").forEach(element => {
        if (element.id === "initialize-dataset-button" || element.id === "company-count-range") {
            return;
        }
        element.disabled = !enabled;
    });
    form.classList.toggle("is-disabled", !enabled);
}

function renderOptionList(filterKey, options) {
    const listElement = document.getElementById(`${filterKey}-list`);
    const selectedValues = filterKey === "sector" ? state.selections.sectors : state.selections.sections;
    const markup = options
        .map(option => `
            <label class="option-item">
                <input
                    type="checkbox"
                    data-filter-option="${filterKey}"
                    value="${escapeHtml(option)}"
                    ${selectedValues.includes(option) ? "checked" : ""}
                >
                <span>${escapeHtml(option)}</span>
            </label>
        `)
        .join("");
    listElement.innerHTML = markup || '<div class="empty-state">No options available.</div>';
    updateTriggerText(filterKey);
}

function updateTriggerText(filterKey) {
    const selectedValues = filterKey === "sector" ? state.selections.sectors : state.selections.sections;
    const trigger = document.querySelector(`[data-filter="${filterKey}"] .trigger-text`);
    if (selectedValues.length === 0) {
        trigger.textContent = filterKey === "sector" ? "All sectors" : "All sections";
        return;
    }
    if (selectedValues.length <= 2) {
        trigger.textContent = selectedValues.join(", ");
        return;
    }
    trigger.textContent = `${selectedValues.length} selected`;
}

function renderSelectedPills(filterKey) {
    const selectedValues = filterKey === "sector" ? state.selections.sectors : state.selections.sections;
    const pills = selectedValues
        .map(value => `
            <span class="pill">
                ${escapeHtml(value)}
                <button type="button" data-remove-pill="${filterKey}" value="${escapeHtml(value)}" aria-label="Remove ${escapeHtml(value)}">×</button>
            </span>
        `)
        .join("");
    document.getElementById(`${filterKey}-pills`).innerHTML = pills;
    updateTriggerText(filterKey);
}

function buildSearchPayload() {
    return {
        mode: state.mode,
        query: document.getElementById("query-input").value.trim(),
        companyName: document.getElementById("company-input").value.trim(),
        ticker: document.getElementById("ticker-input").value.trim(),
        sectors: [...state.selections.sectors],
        sections: [...state.selections.sections],
        filingYear: document.getElementById("filing-year").value,
        filingDate: document.getElementById("filing-date").value
    };
}

function renderSearchStatus(text = "", visible = true) {
    const status = document.getElementById("search-status");
    status.textContent = text;
    status.classList.toggle("hidden", !visible || !text);
}

function resetResultsView() {
    document.getElementById("single-results-view").classList.remove("hidden");
    document.getElementById("results-heading").textContent = INITIAL_RESULTS_HEADING;
    document.getElementById("results-subtitle").textContent = INITIAL_RESULTS_SUBTITLE;
    document.getElementById("single-results").innerHTML = INITIAL_SINGLE_RESULTS_EMPTY_STATE;
    renderSearchStatus("", false);
}

function resetInteractiveState() {
    document.querySelectorAll("[data-filter-search]").forEach(input => {
        input.value = "";
    });
    document.querySelectorAll(".multi-select-panel").forEach(panel => panel.classList.add("hidden"));
    state.selections.sectors = [];
    state.selections.sections = [];
    state.autocompleteIndex = -1;
    state.activeAutocompleteField = null;
    hideAutocomplete("companyName");
    hideAutocomplete("ticker");
    renderOptionList("sector", state.filters.sectors);
    renderOptionList("section", state.filters.sections);
    renderSelectedPills("sector");
    renderSelectedPills("section");
    setMode(INITIAL_MODE);
}

async function runSearch() {
    if (!state.dataset.initialized) {
        renderSearchStatus("Initialize the dataset first to start searching.", true);
        return;
    }

    const payload = buildSearchPayload();
    const subtitle = document.getElementById("results-subtitle");

    subtitle.textContent = "Running retrieval against Redis indexes...";

    try {
        const response = await fetchJson(API.search, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
        renderSearchResponse(response, payload);
    } catch (error) {
        console.error("Live search request failed", error);
        renderSearchFailure(error);
    }
}

async function initializeDataset() {
    const button = document.getElementById("initialize-dataset-button");
    const range = document.getElementById("company-count-range");
    const companyCount = Number(range.value);

    button.disabled = true;
    button.textContent = "Indexing...";
    document.getElementById("dataset-setup-hint").textContent =
        "Loading the selected companies into Redis and generating embeddings...";

    try {
        const payload = await fetchJson(API.datasetInitialize, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ companyCount })
        });

        state.dataset.initialized = true;
        state.filters.coverage = payload.coverage || state.filters.coverage;
        renderCoverage();
        toggleDatasetSetup(false);
        setSearchEnabled(true);
        await loadFilters();
        resetResultsView();
        renderSearchStatus(buildInitializationStatus(payload), true);
    } catch (error) {
        console.error("Dataset initialization failed", error);
        document.getElementById("dataset-setup-hint").textContent =
            `Initialization failed: ${error.message}. Check the Spring app and Redis logs.`;
    } finally {
        button.disabled = false;
        button.textContent = "Initialize Dataset";
    }
}

function buildInitializationStatus(payload) {
    const companyCount = Number(payload?.companyCount || 0);
    const chunkCount = Number(payload?.chunkCount || 0);
    const duration = formatDuration(payload?.indexingDurationMs);

    if (companyCount > 0 && chunkCount > 0 && duration) {
        return `Indexed ${companyCount} companies and ${chunkCount.toLocaleString()} chunks into Redis in ${duration}.`;
    }
    if (companyCount > 0 && duration) {
        return `Indexed ${companyCount} companies into Redis in ${duration}.`;
    }
    if (companyCount > 0) {
        return `Indexed ${companyCount} companies into Redis.`;
    }
    return "Indexed the selected dataset into Redis.";
}

function formatDuration(value) {
    const milliseconds = Number(value);
    if (!Number.isFinite(milliseconds) || milliseconds < 0) {
        return "";
    }

    if (milliseconds < 1000) {
        return `${milliseconds} ms`;
    }

    const totalSeconds = Math.round(milliseconds / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;

    if (minutes === 0) {
        return `${totalSeconds}s`;
    }
    if (seconds === 0) {
        return `${minutes}m`;
    }
    return `${minutes}m ${seconds}s`;
}

function renderSearchFailure(error) {
    const message = error?.message ? `Live search failed: ${error.message}` : "Live search failed.";
    document.getElementById("results-heading").textContent = "Search Error";
    document.getElementById("results-subtitle").textContent =
        "The UI could not retrieve live results from the backend.";
    document.getElementById("single-results-view").classList.remove("hidden");
    document.getElementById("single-results").innerHTML =
        `<div class="empty-state">${escapeHtml(message)} Check the Spring app and Redis logs.</div>`;
    renderSearchStatus(message, true);
}

function renderSearchResponse(response, request) {
    const mode = response.mode || request.mode;
    const results = response.results || response.chunks || [];
    const latency =
        response.totalLatencyMs ??
        response.diagnostics?.latencyMs ??
        response.searchTime ??
        0;
    const count = response.count ?? results.length;
    const source = response.source || "Redis OM Spring";

    document.getElementById("single-results-view").classList.remove("hidden");
    document.getElementById("results-heading").textContent = `${titleCase(mode)} Results`;
    document.getElementById("results-subtitle").textContent =
        count > 0
            ? `${count} chunk${count === 1 ? "" : "s"} ranked by relevance.`
            : "No chunk matches found. Try widening filters or adjusting the query.";
    renderSearchStatus(
        `${titleCase(mode)} returned ${count} ${count === 1 ? "result" : "results"} in ${latency} ms from ${source}.`
    );

    document.getElementById("single-results").innerHTML = renderResultList(
        results,
        request.query,
        mode
    );
}

function renderResultList(results, query, mode) {
    if (!results || results.length === 0) {
        return '<div class="empty-state">No results yet. Run a query or widen the filters.</div>';
    }

    return results
        .map(result => renderResultCard(result, query, mode))
        .join("");
}

function renderResultCard(result, query, mode) {
    const snippetText = result.snippet || result.chunkText || result.text || "";
    const highlightedSnippet = highlightTerms(snippetText, query, mode === "full-text" || mode === "hybrid");
    const normalizedModeClass = modeClass(mode);
    const similarityBadge = hasDisplayScore(result.similarityScore)
        ? `<span class="result-score">similarity ${formatScore(result.similarityScore)}</span>`
        : "";
    const footerLabel = `${result.companyName || "Issuer"} ${result.filingYear || "FY2025"} 10-K`;

    return `
        <article class="result-card">
            <div class="result-header">
                <div>
                    <div class="result-title-wrap">
                        <h3 class="result-title">${escapeHtml(result.companyName || "Unknown issuer")} (${escapeHtml(result.ticker || "--")})</h3>
                        <span class="result-badge">${escapeHtml(result.sectionName || result.section || "Section")}</span>
                    </div>
                </div>
                <div class="result-title-wrap">
                    <span class="result-mode result-mode--${normalizedModeClass}">${titleCase(mode)}</span>
                    ${similarityBadge}
                </div>
            </div>

            <div class="result-metadata">
                <div class="result-meta">
                    <span class="result-meta-label">Sector</span>
                    <strong>${escapeHtml(result.sector || "Unknown")}</strong>
                </div>
                <div class="result-meta">
                    <span class="result-meta-label">Filing Year</span>
                    <strong>${escapeHtml(result.filingYear || "FY2025")}</strong>
                </div>
                <div class="result-meta">
                    <span class="result-meta-label">Filing Date</span>
                    <strong>${escapeHtml(result.filingDate || "--")}</strong>
                </div>
            </div>

            <div class="snippet-block">
                <p class="result-snippet is-clamped">${highlightedSnippet}</p>
            </div>

            <div class="result-footer">
                <span class="result-source">${escapeHtml(footerLabel)}</span>
                <a class="result-link" href="${escapeHtml(result.secUrl || "https://www.sec.gov/")}" target="_blank" rel="noreferrer">Open SEC filing</a>
            </div>
        </article>
    `;
}

function titleCase(value = "") {
    return value
        .replace("-", " ")
        .replace(/\b\w/g, match => match.toUpperCase());
}

function formatScore(value) {
    if (!hasDisplayScore(value)) {
        return "--";
    }
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric.toFixed(2) : "--";
}

function hasDisplayScore(value) {
    return value !== null && value !== undefined && value !== "" && Number.isFinite(Number(value));
}

async function fetchAutocomplete(field, query) {
    if (!query.trim()) {
        return [];
    }

    const params = new URLSearchParams({ field, q: query });

    try {
        const payload = await fetchJson(`${API.autocomplete}?${params.toString()}`);
        return normalizeSuggestions(payload);
    } catch (error) {
        console.error("Live autocomplete failed", error);
        return [];
    }
}

function showAutocomplete(field, suggestions) {
    const container = document.getElementById(autocompleteContainerId(field));
    if (!container) {
        return;
    }

    if (!suggestions.length) {
        container.classList.add("hidden");
        container.innerHTML = "";
        return;
    }

    state.activeAutocompleteField = field;
    state.autocompleteIndex = -1;
    container.innerHTML = suggestions
        .map((suggestion, index) => `
            <button type="button" class="autocomplete-item" data-autocomplete-index="${index}" data-field="${field}" data-value="${escapeHtml(suggestion.value)}">
                <strong>${escapeHtml(suggestion.label)}</strong>
                ${suggestion.hint ? `<span>${escapeHtml(suggestion.hint)}</span>` : ""}
            </button>
        `)
        .join("");
    container.classList.remove("hidden");
}

function hideAutocomplete(field) {
    const container = document.getElementById(autocompleteContainerId(field));
    if (!container) {
        return;
    }
    container.classList.add("hidden");
    container.innerHTML = "";
}

function setMode(mode) {
    state.mode = mode;
    document.querySelectorAll(".mode-chip").forEach(button => {
        button.classList.toggle("active", button.dataset.mode === mode);
    });
}

function initializeModeToggle() {
    document.querySelectorAll(".mode-chip").forEach(button => {
        button.addEventListener("click", () => setMode(button.dataset.mode));
    });
}

function initializeMultiSelects() {
    document.querySelectorAll(".multi-select-trigger").forEach(button => {
        button.addEventListener("click", () => {
            const panel = document.getElementById(button.dataset.target);
            const shouldShow = panel.classList.contains("hidden");
            document.querySelectorAll(".multi-select-panel").forEach(item => item.classList.add("hidden"));
            if (shouldShow) {
                panel.classList.remove("hidden");
            }
        });
    });

    document.addEventListener("change", event => {
        const filterKey = event.target.dataset.filterOption;
        if (!filterKey) {
            return;
        }

        const stateKey = filterKey === "sector" ? "sectors" : "sections";
        const currentSelection = state.selections[stateKey];
        if (event.target.checked) {
            if (!currentSelection.includes(event.target.value)) {
                currentSelection.push(event.target.value);
            }
        } else {
            state.selections[stateKey] = currentSelection.filter(value => value !== event.target.value);
        }

        renderSelectedPills(filterKey);
    });

    document.addEventListener("click", event => {
        const removeButton = event.target.closest("[data-remove-pill]");
        if (removeButton) {
            const filterKey = removeButton.dataset.removePill;
            const stateKey = filterKey === "sector" ? "sectors" : "sections";
            state.selections[stateKey] = state.selections[stateKey].filter(value => value !== removeButton.value);
            renderOptionList(filterKey, filterKey === "sector" ? state.filters.sectors : state.filters.sections);
            renderSelectedPills(filterKey);
            return;
        }

        if (!event.target.closest(".multi-select")) {
            document.querySelectorAll(".multi-select-panel").forEach(panel => panel.classList.add("hidden"));
        }
    });

    document.querySelectorAll("[data-filter-search]").forEach(input => {
        input.addEventListener("input", () => {
            const filterKey = input.dataset.filterSearch;
            const source = filterKey === "sector" ? state.filters.sectors : state.filters.sections;
            const filtered = source.filter(option =>
                option.toLowerCase().includes(input.value.trim().toLowerCase())
            );
            renderOptionList(filterKey, filtered);
        });
    });
}

function initializeAutocomplete() {
    const debouncedFetch = debounce(async (field, query) => {
        const suggestions = await fetchAutocomplete(field, query);
        showAutocomplete(field, suggestions);
    }, 180);

    const fields = [
        { id: "company-input", apiField: "companyName" },
        { id: "ticker-input", apiField: "ticker" }
    ];

    fields.forEach(({ id, apiField }) => {
        const input = document.getElementById(id);
        input.addEventListener("input", () => debouncedFetch(apiField, input.value));
        input.addEventListener("focus", () => {
            if (input.value.trim()) {
                debouncedFetch(apiField, input.value);
            }
        });
    });

    document.addEventListener("click", event => {
        const item = event.target.closest("[data-autocomplete-index]");
        if (item) {
            const field = item.dataset.field;
            document.getElementById(field === "companyName" ? "company-input" : "ticker-input").value = item.dataset.value;
            hideAutocomplete(field);
            return;
        }

        if (!event.target.closest(".autocomplete-field")) {
            hideAutocomplete("companyName");
            hideAutocomplete("ticker");
        }
    });
}

function initializeForm() {
    const form = document.getElementById("search-form");

    form.addEventListener("submit", event => {
        event.preventDefault();
        runSearch();
    });

    document.getElementById("reset-button").addEventListener("click", () => {
        form.reset();
        document.getElementById("query-input").value = "";
        document.getElementById("company-input").value = "";
        document.getElementById("ticker-input").value = "";
        document.getElementById("filing-date").value = "";
        resetInteractiveState();
        resetResultsView();
    });
}

function initializeDatasetSetup() {
    const form = document.getElementById("dataset-setup-form");
    const range = document.getElementById("company-count-range");

    range.addEventListener("input", renderDatasetSetupValue);
    form.addEventListener("submit", event => {
        event.preventDefault();
        initializeDataset();
    });
    renderDatasetSetupValue();
}

document.addEventListener("DOMContentLoaded", async () => {
    initializeModeToggle();
    initializeMultiSelects();
    initializeAutocomplete();
    initializeForm();
    initializeDatasetSetup();
    resetResultsView();
    await loadDatasetStatus();
    if (state.dataset.initialized) {
        await loadFilters();
    }
});
