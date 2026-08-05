#!/usr/bin/env python3
"""Verify that the consolidated reset SQL contains every table/column used by module schemas and mapper DML."""

from __future__ import annotations

import re
import sys
from collections import defaultdict
from pathlib import Path

BACKEND_ROOT = Path(__file__).resolve().parents[1]
RESET_SQL = BACKEND_ROOT / "database" / "01_reset_and_create_all_tables.sql"


def parse_create_tables(sql: str) -> dict[str, set[str]]:
    tables: dict[str, set[str]] = {}
    pattern = re.compile(
        r"CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+`?(\w+)`?\s*\(",
        re.IGNORECASE,
    )
    for match in pattern.finditer(sql):
        table = match.group(1)
        cursor = match.end()
        depth = 1
        quote: str | None = None
        while cursor < len(sql) and depth:
            char = sql[cursor]
            if quote:
                if char == quote and sql[cursor - 1] != "\\":
                    quote = None
            elif char in {"'", '"', "`"}:
                quote = char
            elif char == "(":
                depth += 1
            elif char == ")":
                depth -= 1
            cursor += 1

        body = sql[match.end() : cursor - 1]
        columns: set[str] = set()
        for raw_line in body.splitlines():
            line = raw_line.strip().rstrip(",")
            token = re.match(r"`?(\w+)`?\s+", line)
            if not token:
                continue
            name = token.group(1)
            if name.upper() in {
                "PRIMARY",
                "UNIQUE",
                "KEY",
                "CONSTRAINT",
                "INDEX",
                "FULLTEXT",
                "SPATIAL",
                "FOREIGN",
                "CHECK",
            }:
                continue
            columns.add(name)
        tables[table] = columns
    return tables


def collect_expected_from_schema_files() -> dict[str, set[str]]:
    expected: dict[str, set[str]] = defaultdict(set)
    for path in BACKEND_ROOT.rglob("src/main/resources/db/**/*.sql"):
        sql = path.read_text(encoding="utf-8", errors="ignore")
        for table, columns in parse_create_tables(sql).items():
            expected[table].update(columns)
        for alter in re.finditer(
            r"ALTER\s+TABLE\s+`?(\w+)`?\s+(.*?);",
            sql,
            re.IGNORECASE | re.DOTALL,
        ):
            table = alter.group(1)
            for column in re.finditer(
                r"ADD\s+COLUMN\s+`?(\w+)`?",
                alter.group(2),
                re.IGNORECASE,
            ):
                expected[table].add(column.group(1))
    return expected


def collect_mapper_dml_columns() -> dict[str, set[str]]:
    expected: dict[str, set[str]] = defaultdict(set)
    for path in BACKEND_ROOT.rglob("src/main/java/**/*.java"):
        source = path.read_text(encoding="utf-8", errors="ignore")
        for insert in re.finditer(
            r"insert\s+into\s+`?(\w+)`?\s*\((.*?)\)\s*values",
            source,
            re.IGNORECASE | re.DOTALL,
        ):
            table = insert.group(1)
            for raw in insert.group(2).split(","):
                column = re.sub(r"\s+", "", raw).strip("`")
                if re.fullmatch(r"\w+", column):
                    expected[table].add(column)
        for update in re.finditer(
            r"update\s+`?(\w+)`?\s+set\s+(.*?)(?:\s+where\s+|\"\"\"|\Z)",
            source,
            re.IGNORECASE | re.DOTALL,
        ):
            table = update.group(1)
            for assignment in re.finditer(
                r"(?:^|,)\s*`?(\w+)`?\s*=",
                update.group(2),
                re.IGNORECASE | re.MULTILINE,
            ):
                expected[table].add(assignment.group(1))
    return expected


def main() -> int:
    if not RESET_SQL.exists():
        print(f"Missing reset SQL: {RESET_SQL}", file=sys.stderr)
        return 2

    actual = parse_create_tables(RESET_SQL.read_text(encoding="utf-8"))
    expected = collect_expected_from_schema_files()
    for table, columns in collect_mapper_dml_columns().items():
        expected[table].update(columns)

    errors: list[str] = []
    for table in sorted(expected):
        if table not in actual:
            errors.append(f"missing table: {table}")
            continue
        for column in sorted(expected[table] - actual[table]):
            errors.append(f"missing column: {table}.{column}")

    if errors:
        print("01_reset_and_create_all_tables.sql is incomplete:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1

    print(
        f"OK: consolidated schema covers {len(actual)} tables and all mapper/schema columns."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
