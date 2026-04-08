#!/usr/bin/env python3

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
import re
import subprocess
import time
from collections import Counter
from dataclasses import dataclass
from datetime import datetime, timezone
from html import unescape
from html.parser import HTMLParser
from pathlib import Path
from typing import Iterable
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


WIKIPEDIA_CONSTITUENTS_URL = "https://en.wikipedia.org/wiki/List_of_S%26P_500_companies"
SEC_SUBMISSIONS_URL = "https://data.sec.gov/submissions/CIK{cik}.json"
SEC_ARCHIVE_URL = "https://www.sec.gov/Archives/edgar/data/{cik_int}/{accession_no_dash}/{primary_document}"
DEFAULT_OUTPUT_DIR = Path("src/main/resources/datasets/sp500-fy2025-10k")
DEFAULT_CACHE_DIR = Path(".cache/sp500-fy2025-10k/sec")
DEFAULT_USER_AGENT = "redis-search-demo/0.1 (contact unavailable)"
DEFAULT_CONSTITUENTS_SNAPSHOT_NAME = "constituents-snapshot.json"
DEFAULT_CHUNK_CHARS = 1500
DEFAULT_CHUNK_OVERLAP_CHARS = 200
TARGET_SECTIONS = (
    ("Business", 1),
    ("Risk Factors", 2),
    ("Management's Discussion and Analysis", 3),
)
FALLBACK_SECTION_NAME = "Filing Extract"
FALLBACK_SECTION_ORDER = 99
SECTION_PATTERNS = {
    "Business": {
        "start": [
            r"\bITEM\s+1(?:\.|:)?\s+BUSINESS\b",
        ],
        "end": [
            r"\bITEM\s+1A(?:\.|:)?\s+RISK\s+FACTORS\b",
            r"\bITEM\s+1B(?:\.|:)?\s+UNRESOLVED\s+STAFF\s+COMMENTS\b",
            r"\bITEM\s+1C(?:\.|:)?\s+CYBERSECURITY\b",
            r"\bITEM\s+1D(?:\.|:)?\s+INFORMATION\s+ABOUT\s+OUR\s+EXECUTIVE\s+OFFICERS\b",
            r"\bITEM\s+2(?:\.|:)?\s+PROPERTIES\b",
        ],
    },
    "Risk Factors": {
        "start": [
            r"\bITEM\s+1A(?:\.|:)?\s+RISK\s+FACTORS\b",
        ],
        "end": [
            r"\bITEM\s+1B(?:\.|:)?\s+UNRESOLVED\s+STAFF\s+COMMENTS\b",
            r"\bITEM\s+1C(?:\.|:)?\s+CYBERSECURITY\b",
            r"\bITEM\s+1D(?:\.|:)?\s+INFORMATION\s+ABOUT\s+OUR\s+EXECUTIVE\s+OFFICERS\b",
            r"\bITEM\s+2(?:\.|:)?\s+PROPERTIES\b",
        ],
    },
    "Management's Discussion and Analysis": {
        "start": [
            r"\bITEM\s+7(?:\.|:)?\s+MANAGEMENT'?S?\s+DISCUSSION\s+AND\s+ANALYSIS(?:\s+OF\s+FINANCIAL\s+CONDITION\s+AND\s+RESULTS\s+OF\s+OPERATIONS)?\b",
        ],
        "end": [
            r"\bITEM\s+7A(?:\.|:)?\s+QUANTITATIVE\s+AND\s+QUALITATIVE\s+DISCLOSURES\s+ABOUT\s+MARKET\s+RISK\b",
            r"\bITEM\s+8(?:\.|:)?\s+FINANCIAL\s+STATEMENTS\b",
            r"\bITEM\s+9(?:\.|:)?\s+CHANGES\s+IN\s+AND\s+DISAGREEMENTS\s+WITH\s+ACCOUNTANTS\b",
        ],
    },
}
SECTION_ANCHORS = {
    "Business": [r"\bPART\s+I\b"],
    "Risk Factors": [r"\bPART\s+I\b"],
    "Management's Discussion and Analysis": [r"\bPART\s+II\b"],
}
GENERIC_ITEM_HEADING_PATTERN = re.compile(
    r"\bITEM\s+(?:1A|1B|1C|1D|2|3|4|5|6|7|7A|8|9|9A|9B|9C|10|11|12|13|14|15)(?:\.|:)?\s+[A-Z\[]",
    flags=re.IGNORECASE | re.MULTILINE,
)
STOPWORDS = {
    "about", "after", "against", "among", "because", "before", "between", "business", "company",
    "condition", "could", "financial", "following", "from", "have", "including", "management",
    "operations", "other", "results", "risk", "risks", "shall", "should", "their", "there",
    "these", "those", "under", "which", "would", "with", "into", "during", "through",
}


@dataclass
class Constituent:
    ticker: str
    company_name: str
    sector: str
    cik: str
    market_cap_rank: int | None = None
    market_cap_display: str | None = None


class ConstituentsTableParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.capture_table = False
        self.capture_row = False
        self.capture_cell = False
        self.current_row: list[str] = []
        self.current_cell: list[str] = []
        self.rows: list[list[str]] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attr_map = dict(attrs)
        if tag == "table" and attr_map.get("id") == "constituents":
            self.capture_table = True
            return

        if not self.capture_table:
            return

        if tag == "tr":
            self.capture_row = True
            self.current_row = []
        elif tag in {"td", "th"} and self.capture_row:
            self.capture_cell = True
            self.current_cell = []

    def handle_endtag(self, tag: str) -> None:
        if not self.capture_table:
            return

        if tag in {"td", "th"} and self.capture_cell:
            cell_text = clean_cell_text("".join(self.current_cell))
            self.current_row.append(cell_text)
            self.capture_cell = False
        elif tag == "tr" and self.capture_row:
            if any(cell for cell in self.current_row):
                self.rows.append(self.current_row)
            self.capture_row = False
        elif tag == "table" and self.capture_table:
            self.capture_table = False

    def handle_data(self, data: str) -> None:
        if self.capture_table and self.capture_cell:
            self.current_cell.append(data)


class FilingTextExtractor(HTMLParser):
    BLOCK_TAGS = {
        "address", "article", "blockquote", "br", "caption", "dd", "div", "dl", "dt", "figcaption",
        "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "header", "hr", "li", "main",
        "nav", "ol", "p", "pre", "section", "table", "tbody", "td", "tfoot", "th", "thead", "tr", "ul",
    }
    SKIP_TAGS = {"script", "style", "svg", "noscript"}

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.parts: list[str] = []
        self.skip_depth = 0

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag in self.SKIP_TAGS:
            self.skip_depth += 1
            return
        if self.skip_depth == 0 and tag in self.BLOCK_TAGS:
            self.parts.append("\n")

    def handle_endtag(self, tag: str) -> None:
        if tag in self.SKIP_TAGS and self.skip_depth > 0:
            self.skip_depth -= 1
            return
        if self.skip_depth == 0 and tag in self.BLOCK_TAGS:
            self.parts.append("\n")

    def handle_data(self, data: str) -> None:
        if self.skip_depth == 0:
            self.parts.append(data)

    def text(self) -> str:
        return "".join(self.parts)


def clean_cell_text(value: str) -> str:
    value = re.sub(r"\[[^\]]+\]", " ", value)
    value = re.sub(r"\s+", " ", value)
    return value.strip()


def fetch_bytes(url: str, user_agent: str) -> bytes:
    request = Request(
        url,
        headers={
            "User-Agent": user_agent,
            "Accept": "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8",
            "Accept-Language": "en-US,en;q=0.9",
            "Accept-Encoding": "identity",
        },
    )
    with urlopen(request, timeout=45) as response:
        return response.read()


def fetch_cached_bytes(url: str, user_agent: str, cache_path: Path, refresh: bool = False) -> bytes:
    if cache_path.exists() and not refresh:
        cached = cache_path.read_bytes()
        if cached:
            return cached

    payload = fetch_bytes(url, user_agent)
    cache_path.parent.mkdir(parents=True, exist_ok=True)
    temp_path = cache_path.with_suffix(cache_path.suffix + ".tmp")
    temp_path.write_bytes(payload)
    temp_path.replace(cache_path)
    return payload


def resolve_user_agent(explicit_user_agent: str | None) -> str:
    if explicit_user_agent and explicit_user_agent.strip():
        return explicit_user_agent.strip()

    env_user_agent = os.getenv("SEC_USER_AGENT", "").strip()
    if env_user_agent:
        return env_user_agent

    try:
        name = subprocess.run(
            ["git", "config", "user.name"],
            check=False,
            capture_output=True,
            text=True,
        ).stdout.strip()
        email = subprocess.run(
            ["git", "config", "user.email"],
            check=False,
            capture_output=True,
            text=True,
        ).stdout.strip()
        if name and email:
            return f"{name} ({email}) redis-search-demo/0.1"
    except Exception:  # noqa: BLE001
        pass

    return DEFAULT_USER_AGENT


def load_constituents(user_agent: str, local_snapshot: Path | None) -> list[Constituent]:
    if local_snapshot is not None:
        data = json.loads(local_snapshot.read_text(encoding="utf-8"))
        constituents: list[Constituent] = []
        for item in data:
            cik_digits = re.sub(r"\D", "", str(item["cik"])).strip()
            if not cik_digits:
                continue
            constituents.append(
                Constituent(
                    ticker=item["ticker"].strip(),
                    company_name=item["companyName"].strip(),
                    sector=item["sector"].strip(),
                    cik=cik_digits.zfill(10),
                    market_cap_rank=int(item["marketCapRank"]) if item.get("marketCapRank") not in (None, "") else None,
                    market_cap_display=str(item.get("marketCapDisplay", "")).strip() or None,
                )
            )
        return constituents

    parser = ConstituentsTableParser()
    parser.feed(fetch_bytes(WIKIPEDIA_CONSTITUENTS_URL, user_agent).decode("utf-8", "ignore"))
    if not parser.rows:
        raise RuntimeError("Unable to parse S&P 500 constituents table from Wikipedia.")

    header = parser.rows[0]
    column_map = {name: index for index, name in enumerate(header)}
    required_headers = {"Symbol", "Security", "GICS Sector", "CIK"}
    if not required_headers.issubset(column_map.keys()):
        raise RuntimeError(f"Unexpected constituents headers: {header}")

    constituents: list[Constituent] = []
    for row in parser.rows[1:]:
        if len(row) < len(header):
            continue
        cik_digits = re.sub(r"\D", "", row[column_map["CIK"]]).strip()
        if not cik_digits:
            continue
        constituents.append(
            Constituent(
                ticker=row[column_map["Symbol"]].strip(),
                company_name=row[column_map["Security"]].strip(),
                sector=row[column_map["GICS Sector"]].strip(),
                cik=cik_digits.zfill(10),
            )
        )
    return constituents


def dedupe_constituents(constituents: list[Constituent]) -> list[Constituent]:
    unique_by_cik: dict[str, Constituent] = {}
    for constituent in constituents:
        unique_by_cik.setdefault(constituent.cik, constituent)
    return list(unique_by_cik.values())


def select_fy_filing(submissions: dict, target_filing_year: int) -> dict | None:
    recent = submissions.get("filings", {}).get("recent", {})
    forms = recent.get("form", [])
    filing_dates = recent.get("filingDate", [])
    report_dates = recent.get("reportDate", [])
    accession_numbers = recent.get("accessionNumber", [])
    primary_documents = recent.get("primaryDocument", [])

    filings: list[dict] = []
    for form, filing_date, report_date, accession_number, primary_document in zip(
        forms, filing_dates, report_dates, accession_numbers, primary_documents
    ):
        if form != "10-K":
            continue
        filing_year = parse_year(report_date) or parse_year(filing_date)
        filings.append(
            {
                "form": form,
                "filingDate": filing_date,
                "reportDate": report_date,
                "accessionNumber": accession_number,
                "primaryDocument": primary_document,
                "filingYear": filing_year,
            }
        )

    matching_filings = [filing for filing in filings if filing["filingYear"] == target_filing_year]
    if not matching_filings:
        if not filings:
            return None
        fallback = max(
            filings,
            key=lambda filing: (
                abs((filing.get("filingYear") or target_filing_year) - target_filing_year),
                filing.get("reportDate") or filing.get("filingDate") or "",
            ),
        )
        fallback["usedFilingYearFallback"] = True
        return fallback

    return max(
        matching_filings,
        key=lambda filing: filing.get("reportDate") or filing.get("filingDate") or "",
    )


def parse_year(value: str | None) -> int | None:
    if not value:
        return None
    match = re.match(r"(\d{4})-", value)
    return int(match.group(1)) if match else None


def filing_url(cik: str, accession_number: str, primary_document: str) -> str:
    return SEC_ARCHIVE_URL.format(
        cik_int=str(int(cik)),
        accession_no_dash=accession_number.replace("-", ""),
        primary_document=primary_document,
    )


def submissions_cache_path(cache_dir: Path, cik: str) -> Path:
    return cache_dir / "submissions" / f"CIK{cik}.json"


def filing_cache_path(cache_dir: Path, cik: str, accession_number: str, primary_document: str) -> Path:
    safe_document_name = Path(primary_document).name
    accession_no_dash = accession_number.replace("-", "")
    return cache_dir / "filings" / cik / accession_no_dash / safe_document_name


def extract_filing_text(html_bytes: bytes) -> str:
    parser = FilingTextExtractor()
    parser.feed(html_bytes.decode("utf-8", "ignore"))
    text = unescape(parser.text())
    text = text.replace("\xa0", " ").replace("’", "'")
    text = re.sub(r"[ \t\f\v]+", " ", text)
    text = re.sub(r" ?\n ?", "\n", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    text = re.sub(r"[ ]{2,}", " ", text)
    return text.strip()


def find_section_span(text: str, section_name: str) -> tuple[int, int] | None:
    patterns = SECTION_PATTERNS[section_name]
    upper = text.upper()
    starts = sorted(
        match.start()
        for pattern in patterns["start"]
        for match in re.finditer(pattern, upper, flags=re.IGNORECASE | re.MULTILINE)
    )
    ends = sorted(
        match.start()
        for pattern in patterns["end"]
        for match in re.finditer(pattern, upper, flags=re.IGNORECASE | re.MULTILINE)
    )
    if not starts or not ends:
        return None

    anchor_patterns = SECTION_ANCHORS.get(section_name, [])
    anchor_positions = sorted(
        match.start()
        for pattern in anchor_patterns
        for match in re.finditer(pattern, upper, flags=re.IGNORECASE | re.MULTILINE)
    )
    start_floor = anchor_positions[0] if anchor_positions else int(len(text) * 0.03)
    preferred_starts = [start for start in starts if start >= start_floor]
    candidate_starts = preferred_starts or starts

    candidates: list[tuple[int, int]] = []
    min_length = 2500
    for start in candidate_starts:
        if looks_like_table_of_contents_slice(upper, start):
            continue

        immediate_end = next((end for end in ends if end > start), None)
        if immediate_end is not None and immediate_end < start + 1500:
            continue

        next_end = next((end for end in ends if end > start + min_length), None)
        if next_end is None:
            next_end = next_item_heading_after(upper, start, min_length=min_length)
        if next_end is None:
            continue

        candidates.append((start, next_end))

    if not candidates:
        return None

    return min(candidates, key=lambda item: item[0])


def looks_like_table_of_contents_slice(text_upper: str, start: int) -> bool:
    window = text_upper[start:min(len(text_upper), start + 1200)]
    heading_count = len(list(GENERIC_ITEM_HEADING_PATTERN.finditer(window)))
    return heading_count >= 2


def next_item_heading_after(text_upper: str, start: int, min_length: int) -> int | None:
    minimum_position = start + min_length
    for match in GENERIC_ITEM_HEADING_PATTERN.finditer(text_upper):
        if match.start() > minimum_position:
            return match.start()
    return None


def chunk_text(text: str, max_chars: int, overlap_chars: int) -> list[tuple[str, int, int]]:
    sentences = [
        sentence.strip()
        for sentence in re.split(r"(?<=[.!?])\s+(?=[A-Z0-9(])", text)
        if sentence.strip()
    ]
    if not sentences:
        return []

    chunks: list[tuple[str, int, int]] = []
    current: list[str] = []
    section_cursor = 0

    for sentence in sentences:
        sentence_parts = split_long_sentence(sentence, max_chars)
        for part in sentence_parts:
            prospective = " ".join(current + [part]).strip()
            if current and len(prospective) > max_chars:
                chunk_text_value = " ".join(current).strip()
                start = text.find(chunk_text_value, section_cursor)
                if start < 0:
                    start = max(section_cursor, 0)
                end = start + len(chunk_text_value)
                chunks.append((chunk_text_value, start, end))
                overlap = build_overlap(current, overlap_chars)
                current = overlap + [part]
                section_cursor = end
            else:
                current.append(part)

    if current:
        chunk_text_value = " ".join(current).strip()
        start = text.find(chunk_text_value, section_cursor)
        if start < 0:
            start = max(section_cursor, 0)
        end = start + len(chunk_text_value)
        chunks.append((chunk_text_value, start, end))

    return dedupe_chunks(chunks)


def split_long_sentence(sentence: str, max_chars: int) -> list[str]:
    if len(sentence) <= max_chars:
        return [sentence]

    words = sentence.split()
    parts: list[str] = []
    current: list[str] = []
    for word in words:
        candidate = " ".join(current + [word]).strip()
        if current and len(candidate) > max_chars:
            parts.append(" ".join(current).strip())
            current = [word]
        else:
            current.append(word)
    if current:
        parts.append(" ".join(current).strip())
    return parts


def build_overlap(sentences: list[str], overlap_chars: int) -> list[str]:
    if overlap_chars <= 0:
        return []

    overlap: list[str] = []
    size = 0
    for sentence in reversed(sentences):
        overlap.insert(0, sentence)
        size += len(sentence) + 1
        if size >= overlap_chars:
            break
    return overlap


def dedupe_chunks(chunks: list[tuple[str, int, int]]) -> list[tuple[str, int, int]]:
    seen: set[str] = set()
    deduped: list[tuple[str, int, int]] = []
    for chunk_text_value, start, end in chunks:
        if chunk_text_value in seen:
            continue
        seen.add(chunk_text_value)
        deduped.append((chunk_text_value, start, end))
    return deduped


def summarize(text: str, limit: int = 220) -> str:
    if len(text) <= limit:
        return text
    return text[: limit - 3].rstrip() + "..."


def extract_keywords(text: str, limit: int = 5) -> list[str]:
    words = re.findall(r"[A-Za-z][A-Za-z'\-]{3,}", text.lower())
    counts = Counter(word for word in words if word not in STOPWORDS)
    return [word for word, _ in counts.most_common(limit)]


def build_records_for_filing(
    constituent: Constituent,
    filing: dict,
    html_bytes: bytes,
    filing_year: int,
    max_chars: int,
    overlap_chars: int,
) -> list[dict]:
    text = extract_filing_text(html_bytes)
    records: list[dict] = []

    for section_name, section_order in TARGET_SECTIONS:
        span = find_section_span(text, section_name)
        if span is None:
            continue

        section_text = text[span[0]:span[1]].strip()
        if len(section_text) < 2500:
            continue

        chunks = chunk_text(section_text, max_chars=max_chars, overlap_chars=overlap_chars)
        for chunk_index, (chunk_body, chunk_start, chunk_end) in enumerate(chunks):
            chunk_id = f"{constituent.ticker.lower()}-{slugify(section_name)}-{chunk_index:04d}"
            records.append(
                {
                    "id": chunk_id,
                    "companyName": constituent.company_name,
                    "ticker": constituent.ticker,
                    "sector": constituent.sector,
                    "sectionName": section_name,
                    "filingYear": filing_year,
                    "filingDate": filing["filingDate"],
                    "secUrl": filing["secUrl"],
                    "chunkText": chunk_body,
                    "companyId": constituent.ticker.lower(),
                    "companyCik": constituent.cik,
                    "filingType": "10-K",
                    "accessionNumber": filing["accessionNumber"],
                    "chunkIndex": chunk_index,
                    "chunkCount": len(chunks),
                    "chunkStartChar": chunk_start,
                    "chunkEndChar": chunk_end,
                    "sectionOrder": section_order,
                    "sourceTitle": f"{constituent.company_name} FY{filing_year} 10-K",
                    "sourceYear": filing_year,
                    "sourceSection": section_name,
                    "language": "en",
                    "summary": summarize(chunk_body),
                    "keywords": extract_keywords(chunk_body),
                    "embeddingModel": "Redis OM Spring default transformers",
                    "contentHash": hashlib.sha1(chunk_body.encode("utf-8")).hexdigest(),
                }
            )

    if records:
        return records

    return build_fallback_records_for_filing(
        constituent,
        filing,
        text,
        filing_year=filing.get("filingYear") or filing_year,
        max_chars=max_chars,
        overlap_chars=overlap_chars,
    )


def build_fallback_records_for_filing(
    constituent: Constituent,
    filing: dict,
    text: str,
    filing_year: int,
    max_chars: int,
    overlap_chars: int,
) -> list[dict]:
    start = find_fallback_narrative_start(text)
    end = find_fallback_narrative_end(text, start)
    fallback_text = text[start:end].strip()
    if len(fallback_text) < 2500:
        fallback_text = text[max(0, start):].strip()
    if len(fallback_text) < 2500:
        return []

    chunks = chunk_text(fallback_text, max_chars=max_chars, overlap_chars=overlap_chars)
    records: list[dict] = []
    for chunk_index, (chunk_body, chunk_start, chunk_end) in enumerate(chunks):
        chunk_id = f"{constituent.ticker.lower()}-{slugify(FALLBACK_SECTION_NAME)}-{chunk_index:04d}"
        records.append(
            {
                "id": chunk_id,
                "companyName": constituent.company_name,
                "ticker": constituent.ticker,
                "sector": constituent.sector,
                "sectionName": FALLBACK_SECTION_NAME,
                "filingYear": filing_year,
                "filingDate": filing["filingDate"],
                "secUrl": filing["secUrl"],
                "chunkText": chunk_body,
                "companyId": constituent.ticker.lower(),
                "companyCik": constituent.cik,
                "filingType": "10-K",
                "accessionNumber": filing["accessionNumber"],
                "chunkIndex": chunk_index,
                "chunkCount": len(chunks),
                "chunkStartChar": chunk_start,
                "chunkEndChar": chunk_end,
                "sectionOrder": FALLBACK_SECTION_ORDER,
                "sourceTitle": f"{constituent.company_name} FY{filing_year} 10-K",
                "sourceYear": filing_year,
                "sourceSection": FALLBACK_SECTION_NAME,
                "language": "en",
                "summary": summarize(chunk_body),
                "keywords": extract_keywords(chunk_body),
                "embeddingModel": "Redis OM Spring default transformers",
                "contentHash": hashlib.sha1(chunk_body.encode("utf-8")).hexdigest(),
            }
        )
    return records


def find_fallback_narrative_start(text: str) -> int:
    upper = text.upper()
    minimum_offset = int(len(text) * 0.03)
    candidate_patterns = (
        r"\bITEM\s+(?:1|1A|7)(?:\.|:|—|-)?\s+",
        r"\bMANAGEMENT'?S?\s+DISCUSSION\s+AND\s+ANALYSIS\b",
        r"\bMD&A\b",
        r"\bRISK\s+FACTORS\b",
        r"\bBUSINESS\b",
    )

    candidates = sorted(
        match.start()
        for pattern in candidate_patterns
        for match in re.finditer(pattern, upper, flags=re.IGNORECASE | re.MULTILINE)
        if match.start() >= minimum_offset
    )
    for candidate in candidates:
        if not looks_like_table_of_contents_slice(upper, candidate):
            return candidate

    part_match = re.search(r"\bPART\s+I\b", upper[minimum_offset:])
    if part_match:
        return minimum_offset + part_match.start()

    return minimum_offset


def find_fallback_narrative_end(text: str, start: int) -> int:
    upper = text.upper()
    search_start = min(len(text), start + 5000)
    for pattern in (
        r"\bITEM\s+15(?:\.|:|—|-)?\s+",
        r"\bSIGNATURES\b",
    ):
        match = re.search(pattern, upper[search_start:], flags=re.IGNORECASE | re.MULTILINE)
        if match:
            return search_start + match.start()
    return len(text)

    


def slugify(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-")


def load_json(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def write_json(path: Path, payload: object) -> None:
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


def build_dataset(args: argparse.Namespace) -> None:
    output_dir = args.output_dir.resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    cache_dir = args.cache_dir.resolve()
    cache_dir.mkdir(parents=True, exist_ok=True)

    template_dir = DEFAULT_OUTPUT_DIR.resolve()
    for file_name in ("manifest.json", "schema.json", "retrieval-diagnostics-template.json"):
        source = template_dir / file_name
        target = output_dir / file_name
        if source.exists():
            target.write_text(source.read_text(encoding="utf-8"), encoding="utf-8")

    user_agent = resolve_user_agent(args.user_agent)
    constituents = dedupe_constituents(load_constituents(user_agent, args.constituents_snapshot))
    if args.max_companies:
        constituents = constituents[: args.max_companies]

    snapshot_payload = [
        {
            "ticker": constituent.ticker,
            "companyName": constituent.company_name,
            "sector": constituent.sector,
            "cik": constituent.cik,
            **({"marketCapRank": constituent.market_cap_rank} if constituent.market_cap_rank is not None else {}),
            **({"marketCapDisplay": constituent.market_cap_display} if constituent.market_cap_display else {}),
        }
        for constituent in constituents
    ]
    write_json(output_dir / DEFAULT_CONSTITUENTS_SNAPSHOT_NAME, snapshot_payload)

    records: list[dict] = []
    skipped: list[dict] = []
    records_path = output_dir / "sample-records.json"
    coverage_path = output_dir / "coverage-template.json"

    print(f"Resolved {len(constituents)} constituent companies for indexing.", flush=True)
    print(f"Using SEC cache at {cache_dir} ({'refresh enabled' if args.refresh else 'reuse by default'}).", flush=True)

    for index, constituent in enumerate(constituents, start=1):
        try:
            submissions = json.loads(
                fetch_cached_bytes(
                    SEC_SUBMISSIONS_URL.format(cik=constituent.cik),
                    user_agent,
                    submissions_cache_path(cache_dir, constituent.cik),
                    refresh=args.refresh,
                ).decode("utf-8", "ignore")
            )
            filing = select_fy_filing(submissions, args.filing_year)
            if filing is None:
                reason = "No FY filing found"
                skipped.append({"ticker": constituent.ticker, "companyName": constituent.company_name, "reason": reason})
                print(f"[{index}/{len(constituents)}] skipped {constituent.ticker}: {reason}", flush=True)
                continue

            filing["secUrl"] = filing_url(constituent.cik, filing["accessionNumber"], filing["primaryDocument"])
            html_bytes = fetch_cached_bytes(
                filing["secUrl"],
                user_agent,
                filing_cache_path(
                    cache_dir,
                    constituent.cik,
                    filing["accessionNumber"],
                    filing["primaryDocument"],
                ),
                refresh=args.refresh,
            )
            filing_records = build_records_for_filing(
                constituent,
                filing,
                html_bytes,
                filing_year=args.filing_year,
                max_chars=args.chunk_chars,
                overlap_chars=args.chunk_overlap_chars,
            )
            if not filing_records:
                reason = "Target sections not parsed"
                skipped.append({"ticker": constituent.ticker, "companyName": constituent.company_name, "reason": reason})
                print(f"[{index}/{len(constituents)}] skipped {constituent.ticker}: {reason}", flush=True)
                continue

            records.extend(filing_records)
            print(f"[{index}/{len(constituents)}] indexed {constituent.ticker}: {len(filing_records)} chunks", flush=True)
        except HTTPError as error:
            reason = f"HTTP {error.code}"
            skipped.append({"ticker": constituent.ticker, "companyName": constituent.company_name, "reason": reason})
            print(f"[{index}/{len(constituents)}] skipped {constituent.ticker}: {reason}", flush=True)
        except URLError as error:
            reason = f"URL error: {error.reason}"
            skipped.append({"ticker": constituent.ticker, "companyName": constituent.company_name, "reason": reason})
            print(f"[{index}/{len(constituents)}] skipped {constituent.ticker}: {reason}", flush=True)
        except Exception as error:  # noqa: BLE001
            reason = str(error)
            skipped.append({"ticker": constituent.ticker, "companyName": constituent.company_name, "reason": reason})
            print(f"[{index}/{len(constituents)}] skipped {constituent.ticker}: {reason}", flush=True)
        finally:
            time.sleep(args.sleep_seconds)

    records.sort(key=lambda record: (record["ticker"], record["sectionOrder"], record["chunkIndex"]))

    write_json(output_dir / "skipped-companies.json", skipped)

    if not records:
        raise SystemExit("Dataset build produced zero records. Existing packaged dataset files were left unchanged.")

    write_json(records_path, records)

    compressed_bytes = len(gzip.compress(records_path.read_bytes()))
    coverage = {
        "targetUniverse": "S&P 500 FY2025-era constituents",
        "targetCompanies": len(constituents),
        "indexedCompanies": len({record["companyCik"] for record in records}),
        "skippedCompanies": len(skipped),
        "indexedSections": len({record["sectionName"] for record in records}),
        "indexedChunks": len(records),
        "estimatedCompressedBytes": compressed_bytes,
        "estimatedUncompressedBytes": records_path.stat().st_size,
        "generatedAt": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        "notes": [
            "Built from SEC submissions JSON and filing HTML.",
            "Raw SEC artifacts are cached locally and reused by default; pass --refresh to re-download.",
            "Wikipedia constituents list was used unless a local snapshot was provided.",
            f"Resolved constituents were frozen to {DEFAULT_CONSTITUENTS_SNAPSHOT_NAME}.",
            "Provide --constituents-snapshot for a stricter FY2025-era constituent universe.",
        ],
    }
    write_json(coverage_path, coverage)

    manifest_path = output_dir / "manifest.json"
    if manifest_path.exists():
        manifest = load_json(manifest_path)
        manifest["datasetVersion"] = datetime.now(timezone.utc).strftime("%Y.%m.%d.%H%M")
        manifest["description"] = f"Generated section-aligned chunk corpus with {coverage['indexedChunks']} filing chunks."
        write_json(manifest_path, manifest)

    print()
    print("Dataset build complete.")
    print(f"  Output directory: {output_dir}")
    print(f"  Indexed companies: {coverage['indexedCompanies']}/{coverage['targetCompanies']}")
    print(f"  Indexed chunks: {coverage['indexedChunks']}")
    print(f"  Skipped companies: {coverage['skippedCompanies']}")
    print(f"  Estimated compressed bytes: {coverage['estimatedCompressedBytes']}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build the FY2025 S&P 500 10-K retrieval dataset.")
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument(
        "--cache-dir",
        type=Path,
        default=DEFAULT_CACHE_DIR,
        help="Directory for cached raw SEC submissions JSON and filing HTML.",
    )
    parser.add_argument("--filing-year", type=int, default=2025)
    parser.add_argument("--max-companies", type=int, default=0)
    parser.add_argument(
        "--refresh",
        action="store_true",
        help="Bypass the local SEC cache and re-download filings during this build.",
    )
    parser.add_argument("--sleep-seconds", type=float, default=0.2)
    parser.add_argument("--chunk-chars", type=int, default=DEFAULT_CHUNK_CHARS)
    parser.add_argument("--chunk-overlap-chars", type=int, default=DEFAULT_CHUNK_OVERLAP_CHARS)
    parser.add_argument("--user-agent", default=None)
    parser.add_argument("--constituents-snapshot", type=Path, default=None)
    return parser.parse_args()


if __name__ == "__main__":
    build_dataset(parse_args())
