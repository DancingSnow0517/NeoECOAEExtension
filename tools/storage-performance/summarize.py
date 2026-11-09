"""Summarize matched forks without treating subsystem batches as game tick measurements."""
import argparse
import json
import math
import statistics
from pathlib import Path


def load_forks(root, label):
    records = {}
    files = sorted(path for path in root.glob(f"{label}-*/results.json")
        if path.parent.name.removeprefix(label + "-").isdigit())
    if not files:
        raise ValueError(f"No completed forks: {label}")
    for path in files:
        payload = json.loads(path.read_text(encoding="utf-8"))
        for record in payload["results"]:
            identity = (record["name"], record["n"], record.get("k", 0))
            record = dict(record, java=payload["java"], source=str(path))
            records.setdefault(identity, []).append(record)
    return records


def percentile(values, percent):
    return sorted(values)[max(0, math.ceil(len(values) * percent / 100) - 1)]


def summarize(records):
    cycles = [r.get("cyclesPerSample", 1) for r in records]
    medians = [r.get("medianNsPerCycle", statistics.median(r["elapsedNs"])) for r in records]
    allocations = [r.get("meanBytesPerCycle", statistics.mean(r["allocatedBytes"])) for r in records]
    operations = {r.get("ioOperationsPerCycle", 1) for r in records}
    if len(operations) != 1:
        raise ValueError("Mismatched operations per cycle")
    operations = operations.pop()
    samples = [value / cycles[i] for i, r in enumerate(records) for value in r["elapsedNs"]]
    return {
        "forks": len(records),
        "java": sorted({r["java"] for r in records}),
        "nsPerCycle": statistics.median(medians),
        "forkMedianMinNs": min(medians),
        "forkMedianMaxNs": max(medians),
        "bytesPerCycle": statistics.median(allocations),
        "forkBytesMin": min(allocations),
        "forkBytesMax": max(allocations),
        "cyclesPerSecond": 1e9 / statistics.median(medians),
        "operationsPerCycle": operations,
        "nsPerOperation": statistics.median(medians) / operations,
        "bytesPerOperation": statistics.median(allocations) / operations,
        "operationsPerSecond": operations * 1e9 / statistics.median(medians),
        "sampleP95NsPerCycle": percentile(samples, 95),
        "sampleMaxNsPerCycle": max(samples),
        "gcCounts": [r.get("gcCount") for r in records],
        "gcMillis": [r.get("gcMillis") for r in records],
        "sources": [r["source"] for r in records],
    }


def compare(root, baseline, optimized):
    before, after = load_forks(root, baseline), load_forks(root, optimized)
    if before.keys() != after.keys():
        raise ValueError(f"Mismatched case matrix: {baseline}, {optimized}")
    rows = []
    for (name, n, k), samples in before.items():
        a, b = summarize(samples), summarize(after[name, n, k])
        if a["java"] != b["java"]:
            raise ValueError(f"Mismatched JVM: {name}")
        rows.append({"name": name, "n": n, "k": k, "baseline": a, "optimized": b,
                     "timeChangePercent": 100 * (b["nsPerCycle"] / a["nsPerCycle"] - 1),
                     "allocationChangeBytes": b["bytesPerCycle"] - a["bytesPerCycle"]})
    return {"baselineLabel": baseline, "optimizedLabel": optimized, "results": rows}


def markdown(pair):
    lines = [f"## {pair['baselineLabel']} / {pair['optimizedLabel']}", "",
             "ns/cycle includes all work in the named batch. Fork ranges describe the three fork medians; they are not confidence intervals or tick percentiles.", "",
             "| Workload | n / k | Baseline ns/cycle (range) | Optimized ns/cycle (range) | Time change | Baseline B/cycle | Optimized B/cycle |",
             "| --- | --- | ---: | ---: | ---: | ---: | ---: |"]
    for row in pair["results"]:
        a, b = row["baseline"], row["optimized"]
        time = lambda r: f"{r['nsPerCycle']:.2f} ({r['forkMedianMinNs']:.2f}–{r['forkMedianMaxNs']:.2f})"
        lines.append(f"| {row['name']} | {row['n']} / {row['k']} | {time(a)} | {time(b)} | {row['timeChangePercent']:+.1f}% | {a['bytesPerCycle']:.2f} | {b['bytesPerCycle']:.2f} |")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--results", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--pair", nargs=2, action="append", required=True)
    args = parser.parse_args()
    pairs = [compare(args.results, *pair) for pair in args.pair]
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "comparison.json").write_text(json.dumps(pairs, indent=2), encoding="utf-8")
    (args.output / "comparison-all.md").write_text("\n\n".join(map(markdown, pairs)) + "\n", encoding="utf-8")
    print(f"Compared {sum(len(pair['results']) for pair in pairs)} matched cases")


if __name__ == "__main__":
    main()
