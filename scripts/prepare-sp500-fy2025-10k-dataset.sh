#!/usr/bin/env bash

set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 <chunk-records.json> [output-dir]" >&2
  echo "Example: $0 ./local-data/fy2025-chunks.json src/main/resources/datasets/sp500-fy2025-10k" >&2
  exit 1
fi

INPUT_JSON="$1"
OUTPUT_DIR="${2:-src/main/resources/datasets/sp500-fy2025-10k}"
TEMPLATE_DIR="$(dirname "$0")/../src/main/resources/datasets/sp500-fy2025-10k"

if [[ ! -f "${INPUT_JSON}" ]]; then
  echo "Input file not found: ${INPUT_JSON}" >&2
  exit 1
fi

mkdir -p "${OUTPUT_DIR}"

for template_file in manifest.json schema.json retrieval-diagnostics-template.json; do
  if [[ -f "${TEMPLATE_DIR}/${template_file}" && ! -f "${OUTPUT_DIR}/${template_file}" ]]; then
    cp "${TEMPLATE_DIR}/${template_file}" "${OUTPUT_DIR}/${template_file}"
  fi
done

python3 - "$INPUT_JSON" "$OUTPUT_DIR" <<'PY'
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

input_path = Path(sys.argv[1])
output_dir = Path(sys.argv[2])

required_fields = {
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

with input_path.open("r", encoding="utf-8") as handle:
    records = json.load(handle)

if not isinstance(records, list) or not records:
    raise SystemExit("Input JSON must be a non-empty array of FilingChunk-like records.")

for index, record in enumerate(records):
    missing = sorted(field for field in required_fields if field not in record or record[field] in ("", None))
    if missing:
        raise SystemExit(
            f"Input record {index} is missing required fields: {', '.join(missing)}"
        )

sample_records_path = output_dir / "sample-records.json"
sample_records_path.write_text(json.dumps(records, indent=2) + "\n", encoding="utf-8")

indexed_companies = len({record["companyName"] for record in records})
indexed_sections = len({record["sectionName"] for record in records})
indexed_chunks = len(records)
estimated_uncompressed_bytes = sample_records_path.stat().st_size

coverage = {
    "targetUniverse": "S&P 500 FY2025-era constituents",
    "targetCompanies": 500,
    "indexedCompanies": indexed_companies,
    "skippedCompanies": max(0, 500 - indexed_companies),
    "indexedSections": indexed_sections,
    "indexedChunks": indexed_chunks,
    "estimatedCompressedBytes": 0,
    "estimatedUncompressedBytes": estimated_uncompressed_bytes,
    "generatedAt": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
    "notes": [
        "Generated from local chunk-record input.",
        "This scaffold assumes the input is already section-aligned and chunked.",
    ],
}

(output_dir / "coverage-template.json").write_text(
    json.dumps(coverage, indent=2) + "\n",
    encoding="utf-8",
)

print("Prepared dataset scaffold.")
print(f"  Input: {input_path}")
print(f"  Output directory: {output_dir}")
print(f"  sample-records.json records: {indexed_chunks}")
print(f"  coverage-template.json indexedCompanies: {indexed_companies}")
PY

bash "$(dirname "$0")/validate-sp500-fy2025-10k-dataset.sh" "${OUTPUT_DIR}"
