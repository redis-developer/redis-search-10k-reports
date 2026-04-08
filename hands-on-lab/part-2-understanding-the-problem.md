# Part 2: Understanding the Problem

Key-value access is efficient when the application already knows the key. Search is a different problem. In this demo, we need to retrieve 10-K data in several different ways, and each one has a different query pattern.

## 1. Exact field queries

- User need: Find filings or chunks for a specific company, ticker, sector, or filing year.
- Example query: `companyName = Apple`, `ticker = MSFT`, `filingYear = 2025`
- Why naive search fails: In a key-value model, those fields are inside the value. If they are not part of the key, Redis cannot query them efficiently by default.
- What kind of capability is needed: A way to query by fields that are not part of the key.

## 2. Autocomplete

- User need: Help the user find valid company names, tickers, or section names while typing.
- Example query: `Mic` -> `Microsoft`, `A` -> `AAPL`, `Risk` -> `Risk Factors`
- Why naive search fails: Prefix matching across many values is not efficient if the application has to inspect stored values one by one.
- What kind of capability is needed: A way to support low-latency prefix matching.

## 3. Full-text search

- User need: Find chunks that contain specific terms or phrases.
- Example query: `pricing pressure`, `supply chain`, `data center`
- Why naive search fails: Raw value scans do not provide efficient keyword retrieval or ranking across a large text corpus.
- What kind of capability is needed: A way to search inside text and rank results by keyword relevance.

## 4. Vector search

- User need: Find chunks that are relevant even when they do not use the exact same words as the query.
- Example query: `AI infrastructure demand`
- Why naive search fails: Keyword matching depends on exact terms. It does not capture semantic similarity between different ways of expressing the same idea.
- What kind of capability is needed: A way to retrieve results by semantic similarity, not just keyword overlap.

## 5. Hybrid search

- User need: Combine exact terminology with semantic relevance in one search flow.
- Example query: `cloud demand`, `tariff exposure`, `capital allocation strategy`
- Why naive search fails: Keyword-only search and semantic-only search each miss cases the other can catch. Real queries often need both.
- What kind of capability is needed: A way to combine lexical retrieval and semantic retrieval in the same query.

Now that we understand the retrieval problems, we can look at the Redis Search capabilities that solve them.
