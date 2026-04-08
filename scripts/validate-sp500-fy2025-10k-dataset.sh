#!/usr/bin/env bash

set -euo pipefail

DATASET_DIR="${1:-src/main/resources/datasets/sp500-fy2025-10k}"

if [[ ! -d "${DATASET_DIR}" ]]; then
  echo "Dataset directory not found: ${DATASET_DIR}" >&2
  exit 1
fi

python3 - "$DATASET_DIR" <<'PY'
import json
import sys
from pathlib import Path

dataset_dir = Path(sys.argv[1])
required_files = {
    "manifest": dataset_dir / "manifest.json",
    "schema": dataset_dir / "schema.json",
    "coverage": dataset_dir / "coverage-template.json",
    "records": dataset_dir / "sample-records.json",
}

missing = [name for name, path in required_files.items() if not path.exists()]
if missing:
    raise SystemExit(f"Missing required dataset files: {', '.join(missing)}")

def load_json(path: Path):
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)

manifest = load_json(required_files["manifest"])
schema = load_json(required_files["schema"])
coverage = load_json(required_files["coverage"])
records = load_json(required_files["records"])

if not isinstance(records, list) or not records:
    raise SystemExit("sample-records.json must contain a non-empty JSON array.")

schema_required = schema.get("required", [])
record_required = {
    "id",
    "companyName",
    "ticker",
    "sector",
    "sectionName",
    "filingYear",
    "filingDate",
    "secUrl",
    "chunkText",
}

if not record_required.issubset(schema_required):
    raise SystemExit("schema.json is missing one or more required FilingChunk fields.")

for index, record in enumerate(records):
    missing_fields = sorted(field for field in record_required if field not in record or record[field] in ("", None))
    if missing_fields:
        raise SystemExit(
            f"sample-records.json record {index} is missing required fields: {', '.join(missing_fields)}"
        )

if manifest.get("datasetName") != "sp500-fy2025-10k":
    raise SystemExit("manifest.json has an unexpected datasetName.")

coverage_required = {
    "targetUniverse",
    "targetCompanies",
    "indexedCompanies",
    "skippedCompanies",
    "indexedSections",
    "indexedChunks",
    "estimatedCompressedBytes",
    "estimatedUncompressedBytes",
    "generatedAt",
    "notes",
}

missing_coverage = sorted(field for field in coverage_required if field not in coverage)
if missing_coverage:
    raise SystemExit(
        "coverage-template.json is missing required fields: " + ", ".join(missing_coverage)
    )

indexed_companies = len({record["companyName"] for record in records})
indexed_sections = len({record["sectionName"] for record in records})
indexed_chunks = len(records)

print("Dataset validation passed.")
print(f"  Directory: {dataset_dir}")
print(f"  Records: {indexed_chunks}")
print(f"  Companies in sample set: {indexed_companies}")
print(f"  Sections in sample set: {indexed_sections}")
print(f"  Target universe: {coverage['targetUniverse']}")
PY
