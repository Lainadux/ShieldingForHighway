import argparse
import json
from pathlib import Path
from random import Random

import matplotlib.pyplot as plt


def read_json(path):
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def setting_label(log_dir):
    meta_path = log_dir / "meta.json"
    if not meta_path.exists():
        return log_dir.name

    meta = read_json(meta_path)
    ego_type = meta.get("egoType", "UnknownEgo")
    ai_profile = meta.get("aiProfile", "unknownAI")
    shield_type = meta.get("shieldType")
    if shield_type:
        return f"{ego_type} / {ai_profile} / {shield_type}"
    return f"{ego_type} / {ai_profile}"


def crash_distance(crash_detail):
    collision = crash_detail.get("collision") or {}
    first_role = collision.get("firstVehicleRole")
    second_role = collision.get("secondVehicleRole")

    if first_role == "EGO":
        return float(collision["firstX"])
    if second_role == "EGO":
        return float(collision["secondX"])

    first_x = collision.get("firstX")
    second_x = collision.get("secondX")
    if first_x is not None and second_x is not None:
        return (float(first_x) + float(second_x)) / 2.0
    if first_x is not None:
        return float(first_x)
    if second_x is not None:
        return float(second_x)

    raise ValueError(f"Crash detail has no usable collision x: {crash_detail}")


def read_crash_distances(log_dir):
    crash_path = log_dir / "crash_details.json"
    if not crash_path.exists():
        raise FileNotFoundError(f"Missing crash_details.json: {crash_path}")

    crash_details = read_json(crash_path)
    return [crash_distance(detail) for detail in crash_details]


def default_output_path(log_dirs):
    return Path(__file__).resolve().parent / "crash_distances_by_setting.png"


def plot_crash_distances(log_dirs, output_path, x_min, x_max):
    labels = [setting_label(log_dir) for log_dir in log_dirs]
    distances_by_setting = [read_crash_distances(log_dir) for log_dir in log_dirs]

    rng = Random(20260722)
    fig_height = max(3.5, 1.05 * len(log_dirs) + 1.6)
    fig, ax = plt.subplots(figsize=(11.5, fig_height))

    for y, distances in enumerate(distances_by_setting):
        jittered_y = [y + rng.uniform(-0.08, 0.08) for _ in distances]
        ax.scatter(
            distances,
            jittered_y,
            s=58,
            alpha=0.85,
            edgecolors="black",
            linewidths=0.45,
        )

    labels_with_counts = [
        f"{label} (n={len(distances)})"
        for label, distances in zip(labels, distances_by_setting)
    ]
    ax.set_yticks(range(len(labels_with_counts)))
    ax.set_yticklabels(labels_with_counts)
    ax.set_xlim(x_min, x_max)
    ax.set_ylim(-0.55, len(log_dirs) - 0.45)
    ax.set_xlabel("Crash distance: ego x position")
    ax.set_ylabel("Vehicle + shield setting")
    ax.set_title("Crash Distance Distribution by Setting")
    ax.grid(axis="x", color="#d0d0d0", linewidth=0.8)
    ax.grid(axis="y", color="#ededed", linewidth=0.7)

    for y, distances in enumerate(distances_by_setting):
        visible = sum(x_min <= distance <= x_max for distance in distances)
        if visible != len(distances):
            ax.text(
                x_max,
                y + 0.26,
                f"{len(distances) - visible} outside range",
                ha="right",
                va="center",
                fontsize=9,
                color="#666666",
            )

    fig.tight_layout()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path, dpi=220)
    plt.close(fig)

    return labels, distances_by_setting


def main():
    parser = argparse.ArgumentParser(
        description="Plot crash distances from Java-Momentum log directories."
    )
    parser.add_argument(
        "log_dirs",
        nargs="+",
        type=Path,
        help="Directories containing crash_details.json and meta.json.",
    )
    parser.add_argument(
        "-o",
        "--output",
        type=Path,
        default=None,
        help="Output PNG path. Defaults to crash_distances_by_setting.png next to the first log directory.",
    )
    parser.add_argument("--xmin", type=float, default=0.0)
    parser.add_argument("--xmax", type=float, default=700.0)
    args = parser.parse_args()

    log_dirs = [path.resolve() for path in args.log_dirs]
    output_path = (
        args.output.resolve()
        if args.output is not None
        else default_output_path(log_dirs).resolve()
    )

    labels, distances_by_setting = plot_crash_distances(
        log_dirs,
        output_path,
        args.xmin,
        args.xmax,
    )

    print(f"Wrote {output_path}")
    for label, distances in zip(labels, distances_by_setting):
        visible = sum(args.xmin <= distance <= args.xmax for distance in distances)
        print(f"{label}: crashes={len(distances)}, visible={visible}")


if __name__ == "__main__":
    main()
