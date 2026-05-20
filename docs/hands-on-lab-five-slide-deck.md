# Hands On Lab: Redis Search in 6 Slides

This version is intentionally Redis Search centric.

Each slide is built around:

1. the Redis command snippet
2. what that command is doing
3. what the audience should take away

## Slide 0

### Title
The Search Problem in a Key Value Database

### What the slide should look like
Top:

`Redis is excellent when you know the key. Search starts when you do not.`

Left:

```redis
GET filing:1
```

Small caption under left block:

`Fast if I already know the key`

Right:

```text
Find all 2025 filings for Microsoft
Suggest company names starting with Mic
Find chunks about supply chain risk
Find semantically similar text about AI demand
```

Small caption under right block:

`Not a key lookup problem`

Bottom:

`Key value access answers "where is this record?"`

`Redis Search answers "which records match this condition or meaning?"`

### What to say
Open with the limitation, not the feature. Redis as a key value database is extremely fast when the application already knows the key. But the moment the user wants to search by field, search inside text, autocomplete a prefix, or retrieve by semantic similarity, the problem changes. That is the reason Redis Search exists. It adds the retrieval layer that plain key lookup does not provide.

### What the command is doing
`GET filing:1` is a direct key lookup.

That works well for known identifiers.

It does not solve:

1. filtering by fields inside the value
2. prefix matching across many values
3. full text retrieval
4. vector similarity search

That gap is what the rest of the deck covers.

## Slide 1

### Title
From Redis JSON to Redis Search

### What the slide should look like
Top:

`One JSON document. One Redis Search index. Multiple query patterns.`

Middle left:

```redis
JSON.SET filing:1 $ '{
  "companyName": "Microsoft",
  "ticker": "MSFT",
  "sector": "Information Technology",
  "sectionName": "Risk Factors",
  "filingYear": 2025,
  "chunkText": "Demand for AI infrastructure..."
}'
```

Middle right:

```redis
FT.CREATE idx:filings ON JSON PREFIX 1 filing: SCHEMA \
  $.companyName AS companyName TAG \
  $.ticker AS ticker TAG \
  $.sector AS sector TAG \
  $.sectionName AS sectionName TAG \
  $.filingYear AS filingYear NUMERIC SORTABLE \
  $.chunkText AS chunkText TEXT
```

Bottom:

`Redis Search adds queryable indexes directly on Redis data.`

### What to say
Start with the core idea. We already have JSON documents in Redis. Redis Search is the layer that makes those documents queryable by field, text, and later vectors. This is the anchor slide because it shows that we are not moving data into a separate search system. We are indexing the data where it already lives.

### What the command is doing
`FT.CREATE` defines a search index over JSON documents with the prefix `filing:`.

It maps JSON paths into indexed fields:

1. `TAG` for categorical filters
2. `NUMERIC` for ranges and sorting
3. `TEXT` for full text search

## Slide 2

### Title
Structured Search with Secondary Indexes

### What the slide should look like
Left:

```redis
FT.SEARCH idx:filings '@ticker:{MSFT}'
```

Right:

```redis
FT.SEARCH idx:filings \
  '@sector:{Information\ Technology} @filingYear:[2025 2025]' \
  RETURN 4 companyName ticker sectionName filingYear
```

Bottom row with three short callouts:

1. `TAG fields filter exact values`
2. `NUMERIC fields filter ranges`
3. `No document scan required`

### What to say
This is the first retrieval win. Without an index, Redis would only know the key. With Redis Search, we can jump directly to filings for a ticker, a sector, or a year. This is the moment to explain that secondary indexes are what turn Redis from pure key lookup into structured retrieval.

### What the command is doing
`FT.SEARCH` is using the indexed schema from slide 1.

1. `@ticker:{MSFT}` filters a `TAG` field
2. `@filingYear:[2025 2025]` filters a `NUMERIC` field
3. `RETURN` trims the response to the fields we want to show

## Slide 3

### Title
Autocomplete for Fast Prefix Matching

### What the slide should look like
Top left:

```redis
FT.SUGADD sug:company "Microsoft" 1
FT.SUGADD sug:company "Micron Technology" 1
FT.SUGADD sug:ticker "MSFT" 1
FT.SUGADD sug:section "Risk Factors" 1
```

Top right:

```redis
FT.SUGGET sug:company Mic MAX 5
FT.SUGGET sug:ticker MS MAX 5
FT.SUGGET sug:section Ris MAX 5
```

Bottom:

`Mic -> Microsoft`

`MS -> MSFT`

`Ris -> Risk Factors`

### What to say
Autocomplete is a separate retrieval pattern from search. The user is not searching a corpus yet. They are trying to get to a valid value quickly. Redis Search supports this with suggestion dictionaries, which is why the UI can feel responsive while the user is still typing.

### What the command is doing
`FT.SUGADD` stores suggestions in a dictionary.

`FT.SUGGET` looks up prefix matches from that dictionary.

This is not full text ranking. It is low latency prefix retrieval.

## Slide 4

### Title
Full Text Search over Filing Language

### What the slide should look like
Top:

```redis
FT.ALTER idx:filings SCHEMA ADD $.chunkText AS chunkText TEXT
```

Middle:

```redis
FT.SEARCH idx:filings '@chunkText:(pricing pressure)' \
  RETURN 4 companyName ticker sectionName filingDate
```

Bottom left:

`Redis builds an inverted index`

Bottom right:

`Results are ranked by lexical relevance`

### What to say
This is classic search behavior. We index the filing text, then retrieve documents that contain the terms we care about. This is where you can mention BM25 style scoring at a high level, but keep it simple. Better lexical matches rank higher. This mode is strongest when the user already knows the language used in the filing.

### What the command is doing
`FT.ALTER` adds a `TEXT` field to the existing index.

`FT.SEARCH` looks up terms in the inverted index and returns ranked matches.

This is exact language retrieval, not semantic retrieval.

## Slide 5

### Title
Vector Search and Hybrid Search

### What the slide should look like
Top left:

```redis
FT.ALTER idx:filings SCHEMA ADD $.chunkEmbedding AS chunkEmbedding \
  VECTOR HNSW 6 TYPE FLOAT32 DIM 4 DISTANCE_METRIC COSINE
```

Middle left:

```redis
FT.SEARCH idx:filings \
  '*=>[KNN 3 @chunkEmbedding $query_vector AS vector_score]' \
  PARAMS 2 query_vector <binary-encoded-query-vector> \
  SORTBY vector_score \
  DIALECT 2
```

Middle right:

```redis
FT.SEARCH idx:filings \
  '(@chunkText:(cloud demand))=>[KNN 3 @chunkEmbedding $query_vector AS vector_score]' \
  PARAMS 2 query_vector <binary-encoded-query-vector> \
  SORTBY vector_score \
  DIALECT 2
```

Bottom:

`Vector = semantic similarity`

`Hybrid = lexical filter + semantic ranking`

### What to say
This is the most important closing slide because it shows why Redis Search is more than full text. Vector search finds semantically similar chunks even when the words differ. Hybrid search combines both worlds. You can constrain results with text and still rank them by meaning. That is usually the most compelling moment in the demo.

### What the command is doing
The first `FT.ALTER` adds a vector field using `HNSW`.

The vector query uses `KNN` to find nearest neighbors to the query embedding.

The hybrid query keeps a lexical clause and a vector clause in the same search command.

## Presenter guidance

1. Keep the slide design code first and explanation second.
2. Do not put long paragraphs on the slide itself.
3. Use one command block per idea.
4. Use the speaker notes to explain what the command is doing.
5. If you want a clean closer, end on this line:

`Redis Search is the retrieval layer that turns Redis data into filters, autocomplete, full text, vector, and hybrid search.`
