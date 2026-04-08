# Demo Queries

These are the strongest demo-safe queries for this repo. They were chosen because they usually produce clear, easy-to-explain hits.

## Full-Text

Use full-text when you want exact filing language.

```bash
curl -s -X POST http://localhost:8080/search \
  -H 'Content-Type: application/json' \
  -d '{"mode":"full-text","query":"tariffs","limit":5}'
```

Expected good hits:

- `General Motors`
- `Regeneron`
- `First Solar`

```bash
curl -s -X POST http://localhost:8080/search \
  -H 'Content-Type: application/json' \
  -d '{"mode":"full-text","query":"supply chain","limit":5}'
```

Expected good hits:

- `Procter & Gamble`
- `Lam Research`
- `AutoZone`

## Vector

Use vector search when the user describes a concept instead of the exact filing terms.

```bash
curl -s -X POST http://localhost:8080/search \
  -H 'Content-Type: application/json' \
  -d '{"mode":"vector","query":"companies dependent on a small number of major customers","limit":5}'
```

Expected good hits:

- `Assurant`
- `Allegion`
- `Arista Networks`

```bash
curl -s -X POST http://localhost:8080/search \
  -H 'Content-Type: application/json' \
  -d '{"mode":"vector","query":"companies exposed to supplier disruption and component shortages","limit":5}'
```

Expected good hits:

- `Apple`
- `Church & Dwight`
- `Colgate-Palmolive`

## Hybrid

Use hybrid when exact terms and semantic meaning both matter.

```bash
curl -s -X POST http://localhost:8080/search \
  -H 'Content-Type: application/json' \
  -d '{"mode":"hybrid","query":"AI data center power demand","limit":5}'
```

Expected good hits:

- `NiSource`
- `Ciena`

```bash
curl -s -X POST http://localhost:8080/search \
  -H 'Content-Type: application/json' \
  -d '{"mode":"hybrid","query":"autonomous vehicles robotaxis regulatory risk","limit":5}'
```

Expected good hits:

- `General Motors`
- `Uber`
- `Tesla`

```bash
curl -s -X POST http://localhost:8080/search \
  -H 'Content-Type: application/json' \
  -d '{"mode":"hybrid","query":"AI infrastructure capital expenditures","limit":5}'
```

Expected good hits:

- `Tesla`
- `Alphabet`

## Use With Caution

This query can still be useful, but it is a bit noisier:

```bash
curl -s -X POST http://localhost:8080/search \
  -H 'Content-Type: application/json' \
  -d '{"mode":"hybrid","query":"export controls advanced chips AI China","limit":5}'
```

Why:

- it can return strong matches
- it can also drift into less relevant AI or compliance results
- it should not be presented as a perfect query
