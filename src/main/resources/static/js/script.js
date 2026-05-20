const API = {
    datasetStatus: "/dataset/status",
    datasetInitialize: "/dataset/initialize",
    filters: "/filters",
    search: "/search",
    autocomplete: "/autocomplete"
};

const INITIAL_MODE = "hybrid";
const INITIAL_LIMIT = 20;
const INITIAL_RESULTS_HEADING = "Chunk-Level Matches";
const INITIAL_RESULTS_SUBTITLE =
    "Enter a natural-language query to inspect Redis OM Spring ranking.";
const INITIAL_SINGLE_RESULTS_EMPTY_STATE =
    '<div class="empty-state">Enter a query to inspect ranked FY2025 filing chunks.</div>';

const state = {
    mode: INITIAL_MODE,
    filters: {
        sectors: [],
        sections: []
    },
    dataset: {
        initialized: false,
        companyCount: 0,
        chunkCount: 0
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
    try {
        const payload = await fetchJson(API.datasetStatus);
        state.dataset = {
            initialized: Boolean(payload.initialized),
            companyCount: Number(payload.companyCount || 0),
            chunkCount: Number(payload.chunkCount || 0)
        };
        renderDatasetStatus();
        renderDatasetAction();
    } catch (error) {
        console.error("Unable to load dataset status", error);
        document.getElementById("dataset-status").textContent = "Dataset status unavailable";
        renderDatasetAction();
    }
}

async function loadFilters() {
    try {
        const payload = await fetchJson(API.filters);
        state.filters = {
            sectors: payload.sectors || payload.filters?.sectors || [],
            sections: payload.sections || payload.filters?.sections || []
        };
    } catch (error) {
        console.error("Unable to load live filters", error);
        state.filters = {
            sectors: [],
            sections: []
        };
    }

    renderOptionList("sector", state.filters.sectors);
    renderOptionList("section", state.filters.sections);
    renderSelectedPills("sector");
    renderSelectedPills("section");
}

function renderDatasetStatus() {
    const status = document.getElementById("dataset-status");
    if (!state.dataset.initialized) {
        status.textContent = "No filing chunks indexed in Redis";
        return;
    }
    const companyCount = Number(state.dataset.companyCount || 0);
    const chunkCount = Number(state.dataset.chunkCount || 0);
    status.textContent = `${companyCount} companies, ${chunkCount.toLocaleString()} chunks indexed`;
}

function renderDatasetAction() {
    const button = document.getElementById("initialize-dataset-button");
    button.textContent = state.dataset.initialized ? "Reload Data" : "Load Data";
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
    const emptyMessage = state.dataset.initialized ? "No options available." : "Load data to show options.";
    listElement.innerHTML = markup || `<div class="empty-state">${emptyMessage}</div>`;
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
    const limit = Number(document.getElementById("limit-input").value || INITIAL_LIMIT);
    return {
        mode: state.mode,
        query: document.getElementById("query-input").value.trim(),
        companyName: document.getElementById("company-input").value.trim(),
        ticker: document.getElementById("ticker-input").value.trim(),
        sectors: [...state.selections.sectors],
        sections: [...state.selections.sections],
        filingYear: document.getElementById("filing-year").value,
        filingDate: document.getElementById("filing-date").value,
        limit: Number.isFinite(limit) ? limit : INITIAL_LIMIT
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
    const payload = buildSearchPayload();
    if (!payload.query) {
        renderSearchStatus("Enter a free-text query to run retrieval.", true);
        document.getElementById("query-input").focus();
        return;
    }

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

    button.disabled = true;
    button.textContent = "Loading Data...";
    document.getElementById("dataset-status").textContent = "Indexing the workshop dataset into Redis...";
    renderSearchStatus("", false);

    try {
        const payload = await fetchJson(API.datasetInitialize, {
            method: "POST"
        });

        state.dataset = {
            initialized: Boolean(payload.initialized),
            companyCount: Number(payload.companyCount || 0),
            chunkCount: Number(payload.chunkCount || 0)
        };
        renderDatasetStatus();
        renderDatasetAction();
        await loadFilters();
        resetResultsView();
        renderSearchStatus(buildDatasetLoadedStatus(payload), true);
    } catch (error) {
        console.error("Dataset initialization failed", error);
        document.getElementById("dataset-status").textContent =
            `Dataset load failed: ${error.message}. Check the Spring app and Redis logs.`;
    } finally {
        button.disabled = false;
        renderDatasetAction();
    }
}

function buildDatasetLoadedStatus(payload) {
    const companyCount = Number(payload?.companyCount || 0);
    const chunkCount = Number(payload?.chunkCount || 0);
    if (companyCount > 0 && chunkCount > 0) {
        return `Loaded ${companyCount} companies and ${chunkCount.toLocaleString()} chunks into Redis.`;
    }
    return "Loaded the workshop dataset into Redis.";
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
    const queryInput = document.getElementById("query-input");

    form.addEventListener("submit", event => {
        event.preventDefault();
        queryInput.setCustomValidity("");
        if (!queryInput.value.trim()) {
            queryInput.setCustomValidity("Enter a query to run retrieval.");
            queryInput.reportValidity();
            renderSearchStatus("Enter a free-text query to run retrieval.", true);
            return;
        }
        runSearch();
    });

    queryInput.addEventListener("input", () => {
        queryInput.setCustomValidity("");
    });

    document.getElementById("reset-button").addEventListener("click", () => {
        form.reset();
        document.getElementById("query-input").value = "";
        document.getElementById("company-input").value = "";
        document.getElementById("ticker-input").value = "";
        document.getElementById("filing-date").value = "";
        document.getElementById("limit-input").value = INITIAL_LIMIT;
        resetInteractiveState();
        resetResultsView();
    });

    document.getElementById("initialize-dataset-button").addEventListener("click", () => {
        initializeDataset();
    });
}

document.addEventListener("DOMContentLoaded", async () => {
    initializeModeToggle();
    initializeMultiSelects();
    initializeAutocomplete();
    initializeForm();
    resetResultsView();
    await loadDatasetStatus();
    await loadFilters();
});
