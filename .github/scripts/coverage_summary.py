#!/usr/bin/env python3
"""Prints the JaCoCo coverage as a Markdown table (used for the GitHub Actions job summary)."""
import csv
import sys
from collections import defaultdict
from pathlib import Path

report = Path("target/site/jacoco/jacoco.csv")
if not report.exists():
    print("## Coverage\n\nNo JaCoCo report was generated.")
    sys.exit(0)

lines = defaultdict(lambda: [0, 0])
branches = defaultdict(lambda: [0, 0])
for row in csv.DictReader(report.open()):
    package = row["PACKAGE"].removeprefix("com.thiago.spacebooking").lstrip(".") or "(root)"
    lines[package][0] += int(row["LINE_COVERED"])
    lines[package][1] += int(row["LINE_MISSED"])
    branches[package][0] += int(row["BRANCH_COVERED"])
    branches[package][1] += int(row["BRANCH_MISSED"])


def pct(covered, missed):
    total = covered + missed
    return "n/a" if total == 0 else f"{covered / total * 100:.1f}%"


print("## Coverage (JaCoCo)\n")
print("| Package | Lines | Branches |")
print("|---|---|---|")
for package in sorted(lines):
    print(f"| `{package}` | {pct(*lines[package])} | {pct(*branches[package])} |")
total_lines = [sum(v[i] for v in lines.values()) for i in (0, 1)]
total_branches = [sum(v[i] for v in branches.values()) for i in (0, 1)]
print(f"| **Total** | **{pct(*total_lines)}** | **{pct(*total_branches)}** |")
print("\nThe build fails below 85% line coverage overall or 90% in the booking package.")
