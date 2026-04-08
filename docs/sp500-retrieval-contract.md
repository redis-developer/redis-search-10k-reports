# S&P 500 FY2025 10-K Retrieval Brief

This document is the product-level brief for the demo. The canonical dataset field contract lives in [sp500-fy2025-10k-dataset-contract.md](./sp500-fy2025-10k-dataset-contract.md).

## Scope

- Refactor the movie demo into a retrieval-only S&P 500 FY2025 10-K console.
- Keep the existing stack: Spring Boot, Redis OM Spring, static frontend.
- Preserve automatic startup ingestion and Redis OM Spring embedding generation.
- Ship a prebuilt in-repo dataset; do not store raw filings in the repo.

## Retrieval Model

- Universe: fixed FY2025-era S&P 500 constituent list.
- Filing type: 10-K only.
- Coverage goal: all 500 if possible, otherwise the largest clean subset with transparent coverage reporting.
- Preferred sections: `Business`, `Risk Factors`, and `Management's Discussion and Analysis`.
- Record unit: section-aligned chunk with small overlap.
- Primary result unit: individual matched chunk.

## Search Experience

- Modes:
  - `full-text`
  - `vector`
  - `hybrid`
  - `compare`
- Default mode: `hybrid`
- Filters:
  - company name
  - ticker
  - sector
  - section
  - filing year
  - filing date
- Free-text query is optional.
- Filters-only searches are allowed.
- Autocomplete should support company names, tickers, and section names.
- Expose latency, result counts, mode details, and coverage summary in the UI.

## UI Brief

- Visual reference: the workshop family in `/Users/raphaeldelio/Documents/GitHub/workshops/redis-beyond-the-cache-workshop`
- Dark mode only
- Compact control-console feel
- Minimal explanatory copy
- Sticky toolbar with Redis logo and short title only
- Top controls, results below
- Inline diagnostics near the controls
- Compact stacked result cards
- Three equal-width compare columns
- Subtle Redis branding
- Dense, technical overall feel
- Filters always visible
- Outlined workshop-style inputs
- Clamped snippets with a `More` affordance
- Dropdown checklist multi-selects
- `Run Retrieval` as the main CTA
- No animation
- SEC link as a button-style action
- Full-width app layout

## Open Work

- Replace the synthetic sample corpus with the real FY2025 10-K chunk dataset.
- Tighten the hybrid implementation if Redis-native hybrid support becomes reliable in this environment.
- Keep the dataset contract and generated coverage metadata in sync with the real corpus builder.
