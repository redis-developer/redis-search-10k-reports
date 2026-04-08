# S&P 500 FY2025 10-K Dataset Contract

This repository uses a prebuilt, classpath-packaged dataset for a Redis search demo focused on retrieval over `FY2025` `10-K` filings.

## Goals

- Optimize for breadth across the S&P 500 while staying near the desired size envelope.
- Index section-aligned chunks instead of whole filings.
- Support hybrid retrieval, full-text retrieval, and vector retrieval over the same chunk corpus.
- Keep the data shape stable so the backend and frontend can evolve independently.

## Scope

The corpus targets one recent `FY2025` `10-K` per company, with the following narrative sections preferred:

- `Business`
- `Risk Factors`
- `Management's Discussion and Analysis` (`MD&A`)

If a filing cannot be cleanly parsed for those preferred sections, the builder may fall back to a `Filing Extract` section so every company can still be represented in the packaged corpus.
If an exact `FY2025` filing is unavailable, the builder may fall back to the nearest available `10-K` for completeness.

## Record Shape

Each indexed record represents one chunk from one filing section.

Required fields:

- `id`
- `companyName`
- `ticker`
- `sector`
- `sectionName`
- `filingYear`
- `filingDate`
- `secUrl`
- `chunkText`

Recommended fields for retrieval, diagnostics, and UX:

- `companyId`
- `companyCik`
- `filingType`
- `accessionNumber`
- `chunkIndex`
- `chunkCount`
- `chunkStartChar`
- `chunkEndChar`
- `sectionOrder`
- `sourceTitle`
- `sourceYear`
- `sourceSection`
- `language`
- `summary`
- `keywords`
- `embeddingModel`
- `contentHash`

## Chunking Rules

- Chunk aligned to filing sections first, then split into smaller retrieval units.
- Use a small overlap between adjacent chunks to preserve context across boundaries.
- Keep chunk size consistent enough to make retrieval behavior easy to explain in the demo.
- Avoid storing raw filing bodies in the repository if the chunked text is already preserved.

## Coverage Reporting

The dataset should ship with a compact coverage summary so the UI can show how complete the corpus is.

Suggested coverage fields:

- `targetUniverse`
- `targetCompanies`
- `indexedCompanies`
- `skippedCompanies`
- `indexedSections`
- `indexedChunks`
- `estimatedCompressedBytes`
- `estimatedUncompressedBytes`
- `generatedAt`
- `notes`

## File Layout

Recommended classpath location:

`src/main/resources/datasets/sp500-fy2025-10k/`

Suggested files:

- `manifest.json`
- `schema.json`
- `coverage-template.json`
- `sample-records.json`

## Notes

- The sample files in this scaffold are synthetic and exist only to define the contract.
- The real corpus can be populated later without changing the expected field names.
- The dataset builder caches raw SEC submissions JSON and filing HTML locally and reuses them by default.
- Pass `--refresh` to the builder when you want to bypass the local SEC cache and download fresh artifacts.
- If the final dataset needs to fit a tighter size envelope, section selection and chunk length are the first knobs to tune.
