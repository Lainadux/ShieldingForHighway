import argparse
import json
import re
from pathlib import Path

import matplotlib.pyplot as plt


DEFAULT_LOG_ROOT = (
    Path(__file__).resolve().parents[1]
    / "src"
    / "main"
    / "java"
    / "RefractoredVersion"
    / "logs"
)


def read_json(path):
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def default_log_dirs():
    return sorted(
        DEFAULT_LOG_ROOT.glob("*/EgoRandomEnableShield_adversarial_LOGS(*)"),
        key=read_k,
    )


def read_k(log_dir):
    meta_path = log_dir / "meta.json"
    if meta_path.exists():
        meta = read_json(meta_path)
        k = meta.get("randomEnableShieldPercent")
        if k is not None:
            return int(k)

    match = re.search(r"\((\d+)\)$", log_dir.name)
    if match:
        return int(match.group(1))

    raise ValueError(f"Cannot infer k from {log_dir}")


def read_collision_point(log_dir):
    collision_path = log_dir / "collision_stats.json"
    if not collision_path.exists():
        raise FileNotFoundError(f"Missing collision_stats.json: {collision_path}")

    stats = read_json(collision_path)
    requested = int(stats.get("requestedRuns", 0))
    completed = int(stats.get("completedRuns", requested))
    crashed = int(stats.get("crashedRuns", 0))
    crash_percent = float(stats.get("crashPercent", 0.0))
    return {
        "k": read_k(log_dir),
        "requested": requested,
        "completed": completed,
        "crashed": crashed,
        "crash_percent": crash_percent,
        "log_dir": str(log_dir),
    }


def plot(points, output_path):
    points = sorted(points, key=lambda point: point["k"])
    ks = [point["k"] for point in points]
    crash_percents = [point["crash_percent"] for point in points]

    fig, ax = plt.subplots(figsize=(8.2, 5.0))
    ax.plot(
        ks,
        crash_percents,
        marker="o",
        linewidth=2.2,
        markersize=7,
        color="#2f6fbd",
    )

    for point in points:
        ax.annotate(
            f'{point["crashed"]}/{point["completed"]}',
            (point["k"], point["crash_percent"]),
            textcoords="offset points",
            xytext=(0, 9),
            ha="center",
            fontsize=9,
        )

    ax.set_title("Collision rate when rejected AI actions bypass the shield")
    ax.set_xlabel("Shield bypass probability k (%)")
    ax.set_ylabel("Collision rate (%)")
    ax.set_xticks(ks)
    ax.set_ylim(0, max(100, max(crash_percents) + 8))
    ax.grid(True, axis="both", alpha=0.28)

    fig.tight_layout()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path, dpi=220)
    plt.close(fig)


def main():
    parser = argparse.ArgumentParser(
        description="Plot collision rate for EgoRandomEnableShield adversarial k-ablation logs."
    )
    parser.add_argument(
        "log_dirs",
        nargs="*",
        type=Path,
        help="Optional EgoRandomEnableShield_adversarial_LOGS(k) directories. Defaults to all matching logs.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(__file__).resolve().parent / "random_enable_shield_collision_rate.png",
        help="Output image path.",
    )
    args = parser.parse_args()

    log_dirs = args.log_dirs or default_log_dirs()
    if not log_dirs:
        raise SystemExit("No EgoRandomEnableShield adversarial log directories found.")

    points = [read_collision_point(log_dir) for log_dir in log_dirs]
    plot(points, args.output)

    print(f"Wrote {args.output}")
    for point in sorted(points, key=lambda item: item["k"]):
        print(
            f'k={point["k"]:>3}%  '
            f'crashes={point["crashed"]}/{point["completed"]}  '
            f'collision_rate={point["crash_percent"]:.2f}%'
        )


if __name__ == "__main__":
    main()
