# Part 1: Introduction

Redis is a key-value database. That works well when you know the key. It does not work well when you need to query by fields, search inside text, support autocomplete, or retrieve by semantic similarity. For that, you need indexes. Redis Search adds those indexes directly on Redis data.

Because those indexes live in Redis, searchable data can stay close to operational data instead of moving into a separate search system.

In this lab, you will use a Java and Spring demo built on S&P 500 10-K filings to learn:

- Secondary indexes
- Autocomplete
- Full-text search
- Vector search
- Hybrid search

A 10-K is a public company's annual report filed with the SEC. It gives us both structured fields and long-form text, which makes it a good dataset for learning search.

## How you'll learn it

1. Start with the problem: key-value access is not enough for search.
2. Learn secondary indexes for structured queries.
3. Learn autocomplete for prefix matching.
4. Learn full-text search with inverted indexes.
5. Learn vector search with embeddings and HNSW indexes.
6. Learn hybrid search by combining lexical and vector retrieval.
7. Implement and test the solution in the Java and Spring demo with Redis OM Spring.
