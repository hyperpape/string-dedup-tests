#!/usr/bin/python

# acts on the results of egrep '(Parameters)|(TotalSeen)|(VM options)'

import sys
import re


def extract_data(triplets):
    for triplet in triplets:
        values = {}
        values["manual_dedup"] = "dedup = true" in triplet[1]
        values["g1_dedup"] = "+UseStringDeduplication" in triplet[0]
        values["string_count"] = extract(r"stringCount = (\d+)", triplet[1])
        values["string_length"] = extract(r"stringLength = (\d+)", triplet[1])
        values["gc_freq"] = extract(r"gcFreq = (\d+)", triplet[1])
        values["churn_gc"] = "churnGc = true" in triplet[1]
        values["system_gc"] = "systemGc = true" in triplet[1]
        values["total_seen"] = extract(r"TotalSeen=(\d+)", triplet[2])
        yield values


def to_table_lines(triplets):
    lines = list(extract_data(triplets))
    keys = [
        "manual_dedup",
        "g1_dedup",
        "churn_gc",
        "string_count",
        "string_length",
        "gc_freq",
        "total_seen",
    ]
    if any("system_gc" in line for line in lines):
        keys.append("system_gc")
    yield "|" + "|".join(keys) + "|"
    for line in lines:
        yield "|" + "|".join([str(line[key]) for key in keys]) + "|"


def extract(needle, haystack):
    pattern = re.compile(needle)
    match = pattern.search(haystack)
    if match:
        return match.group(1)
    return None


def group_by_threes(lines):
    for i in range(0, len(lines), 3):
        if i + 3 > len(lines):
            breakpoint()
        yield lines[i : i + 3]


if __name__ == "__main__":
    lines = sys.stdin.readlines()
    sys.stdin = open("/dev/tty")
    for line in to_table_lines(group_by_threes(lines)):
        print(line)
