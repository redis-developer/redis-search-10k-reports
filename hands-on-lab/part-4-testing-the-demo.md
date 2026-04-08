# Part 4: Testing the Demo

At this point, the goal is simple: run the application, load the dataset, and verify that each search mode behaves the way we expect.

## 1. Start the application

Run the Spring Boot application:

```bash
./gradlew bootRun
```

The demo should be available at:

```text
http://localhost:8080
```

## 2. Initialize the dataset

First, check whether the dataset is already loaded:

```bash
curl -s http://localhost:8080/dataset/status
```

If it is not initialized, load it:

```bash
curl -s -X POST http://localhost:8080/dataset/initialize \
  -H 'Content-Type: application/json' \
  -d '{"companyCount":100}'
```

What to verify:

- `initialized` is `true`
- the response includes `companyCount`
- the response includes `chunkCount`
- the response includes `indexingDurationMs`

## 3. Verify filters and autocomplete

Load filter metadata:

```bash
curl -s http://localhost:8080/filters
```

Test autocomplete:

```bash
curl -s 'http://localhost:8080/autocomplete?field=companyName&q=Mic'
curl -s 'http://localhost:8080/autocomplete?field=ticker&q=MS'
curl -s 'http://localhost:8080/autocomplete?field=sectionName&q=Risk'
```

What to verify:

- company suggestions return names such as `Microsoft`
- ticker suggestions return values such as `MSFT`
- section suggestions return values such as `Risk Factors`

## 4. Test full-text search

Use full-text when you want exact filing language.

```bash
curl -s -X POST http://localhost:8080/search \
  -H 'Content-Type: application/json' \
  -d '{
    "mode":"full-text",
    "query":"tariffs",
    "limit":5
  }'
```

```bash
curl -s -X POST http://localhost:8080/search \
  -H 'Content-Type: application/json' \
  -d '{
    "mode":"full-text",
    "query":"supply chain",
    "limit":5
  }'
```

What to look for:

- `tariffs` should return strong exact-term matches such as `General Motors`, `Regeneron`, and `First Solar`
- `supply chain` should return strong exact-term matches such as `Procter & Gamble`, `Lam Research`, and `AutoZone`

Takeaway:

- full-text is usually the cleanest mode when the user already knows the filing language

## 5. Test vector search

Use vector search when you want semantic retrieval instead of exact wording.

```bash
curl -s -X POST http://localhost:8080/search \
  -H 'Content-Type: application/json' \
  -d '{
    "mode":"vector",
    "query":"companies dependent on a small number of major customers",
    "limit":5
  }'
```

```bash
curl -s -X POST http://localhost:8080/search \
  -H 'Content-Type: application/json' \
  -d '{
    "mode":"vector",
    "query":"companies exposed to supplier disruption and component shortages",
    "limit":5
  }'
```

What to look for:

- customer concentration should surface results such as `Assurant`, `Allegion`, and `Arista`
- supplier disruption should surface results such as `Apple`, `Church & Dwight`, and `Colgate-Palmolive`

Takeaway:

- vector search is strong when the user describes a concept instead of the exact filing terms

## 6. Test hybrid search

Use hybrid search when both lexical clues and semantic meaning matter.

```bash
curl -s -X POST http://localhost:8080/search \
  -H 'Content-Type: application/json' \
  -d '{
    "mode":"hybrid",
    "query":"AI data center power demand",
    "limit":5
  }'
```

```bash
curl -s -X POST http://localhost:8080/search \
  -H 'Content-Type: application/json' \
  -d '{
    "mode":"hybrid",
    "query":"autonomous vehicles robotaxis regulatory risk",
    "limit":5
  }'
```

```bash
curl -s -X POST http://localhost:8080/search \
  -H 'Content-Type: application/json' \
  -d '{
    "mode":"hybrid",
    "query":"AI infrastructure capital expenditures",
    "limit":5
  }'
```

What to look for:

- `AI data center power demand` should return strong results such as `NiSource` and `Ciena`
- `autonomous vehicles robotaxis regulatory risk` should return strong results such as `General Motors`, `Uber`, and `Tesla`
- `AI infrastructure capital expenditures` should return strong results such as `Tesla` and `Alphabet`

Takeaway:

- hybrid is often the most interesting demo mode because it combines keyword precision with semantic retrieval

## 7. Compare the modes

As you test, compare how the same system behaves across different retrieval styles:

- full-text is best when the query uses exact filing language
- vector is best when the query expresses an idea or theme
- hybrid is best when you want both lexical precision and semantic recall

## 8. Use with caution

This query can still be useful in demos, but it is not as clean as the others:

```text
export controls advanced chips AI China
```

Why:

- top hits can be very strong
- some results can drift off-theme
- it should not be presented as flawless

## 9. Wrap-up

At this point, you have validated the full flow:

- dataset loading
- filter metadata
- autocomplete
- full-text search
- vector search
- hybrid search

The demo is now ready to show how Redis Search supports multiple retrieval patterns over the same 10-K dataset.
