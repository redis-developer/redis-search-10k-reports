# Redis Search Demo

This repo is a Redis Search demo built around S&P 500 FY2025 10-K filings. It shows how Redis can support structured filtering, autocomplete, full-text search, vector search, and hybrid search over the same application data.

## Prerequisites

- Java 25
- Docker and Docker Compose
- A Redis runtime on port `6379` with Redis Search, JSON, and vector support
- Enough local disk and patience for a large packaged dataset on first run

Notes:

- This repo is intentionally pinned to Java 25 in [`build.gradle.kts`](/Users/raphaeldelio/Documents/GitHub/demos/redis-search-demo/build.gradle.kts).
- The Docker setup in this repo is now pinned to versioned Redis images for a more reproducible demo environment.

## Start Redis

```bash
docker compose up -d
```

This starts Redis and RedisInsight.

- Redis: `redis://localhost:6379`
- RedisInsight: `http://localhost:5540`

Note:

- The compose file uses `redis/redis-stack-server:7.4.0-v8` and `redis/redisinsight:2.70`.
- Vector and hybrid search also depend on a working embedding runtime.
- If the embedder is not ready, full-text, filters, and autocomplete can still work while vector and hybrid fail at runtime.

## Run The App

```bash
./gradlew bootRun
```

Open the demo at:

```text
http://localhost:8080
```

## Use The UI

The browser UI is the main way to experience this demo.

1. Open `http://localhost:8080`.
2. If this is the first run, choose how many companies to index and initialize the dataset.
3. Use the free-text query box to search the 10-K corpus.
4. Use the filters to narrow by company, ticker, sector, section, filing year, or filing date.
5. Switch between `Full-Text`, `Vector`, and `Hybrid` modes.
6. Review the ranked chunk results and the live diagnostics shown in the UI.

Use RedisInsight if you want to inspect the underlying Redis data, but the demo itself is meant to be driven from the app UI.

## First Run

The first startup may take longer because the app indexes the packaged dataset into Redis and generates embeddings for vector search.

- The packaged dataset in `src/main/resources/datasets` is large, roughly `274 MB`.
- First-run indexing time depends on how many companies you load and whether the local transformer embedder is configured and ready.
- If vector or hybrid search fail, check your local embedding setup first.

If the dataset is not loaded yet, initialize it with:

```bash
curl -s -X POST http://localhost:8080/dataset/initialize \
  -H 'Content-Type: application/json' \
  -d '{"companyCount":100}'
```

You can check status with:

```bash
curl -s http://localhost:8080/dataset/status
```

## Demo Flow

The UI follows this flow:

1. Initialize the dataset if needed.
2. Use the filters to narrow company, ticker, sector, year, and filing date.
3. Use autocomplete for company names, tickers, and section names.
4. Switch between full-text, vector, and hybrid search.
5. Review the returned chunks and the live diagnostics.

Useful endpoints:

- `GET /dataset/status`
- `POST /dataset/initialize`
- `GET /filters`
- `GET /autocomplete`
- `POST /search`

## Hands-On Lab

If you want the workshop version of the narrative, start with:

- [Part 1: Introduction](hands-on-lab/part-1-introduction.md)
- [Part 2: Understanding the Problem](hands-on-lab/part-2-understanding-the-problem.md)
- [Part 3: The Solution](hands-on-lab/part-3-the-solution.md)
- [Part 4: Testing the Demo](hands-on-lab/part-4-testing-the-demo.md)

## Demo Queries

For presenter-safe query ideas, see:

- [docs/demo-queries.md](docs/demo-queries.md)

## Embedder Setup

For the checked-in vector configuration, see:

- [docs/embedder-setup.md](docs/embedder-setup.md)
