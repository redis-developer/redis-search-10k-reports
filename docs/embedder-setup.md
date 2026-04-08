# Embedder Setup

Vector and hybrid search in this demo depend on Redis OM Spring AI being enabled:

```properties
redis.om.spring.ai.enabled=true
```

The checked-in embedder example is on [`FilingChunk.chunkText`](/Users/raphaeldelio/Documents/GitHub/demos/redis-search-demo/src/main/java/com/redis/redissearchdemo/domain/FilingChunk.java):

```java
@Vectorize(
    destination = "chunkEmbedding",
    provider = EmbeddingProvider.TRANSFORMERS
)
private String chunkText;
```

What this means:

- The demo uses a local `TRANSFORMERS` provider for text embeddings.
- The model is resolved by Redis OM Spring AI's local transformers integration unless you override it with a provider-specific configuration.
- The current default path in this repo produces 384-dimensional vectors, which matches the `@VectorIndexed(dimension = 384)` field in this repo.

What to expect on first run:

- Redis OM Spring AI may need to download the transformer model before vector search is ready.
- If that model cannot be downloaded or initialized, full-text search, filters, and autocomplete can still work while vector and hybrid search fail.

Current runtime path in this repo:

1. `chunkText` is vectorized into `chunkEmbedding` when documents are saved.
2. Query text is embedded at runtime inside [`SearchService.embedQuery(...)`](/Users/raphaeldelio/Documents/GitHub/demos/redis-search-demo/src/main/java/com/redis/redissearchdemo/service/SearchService.java#L271).
3. Vector and hybrid search use that embedding to query the vector index.

If you want a different provider later:

- Redis OM Spring AI also supports providers such as OpenAI, Azure OpenAI, Ollama, Vertex AI, and Bedrock.
- For this demo, the checked-in default is local transformers because it keeps the vector configuration explicit in the repo without requiring a vendor API key.
