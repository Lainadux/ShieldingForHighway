import argparse
import json
from pathlib import Path
from random import Random

import matplotlib.pyplot as plt


SETTING_LABELS = {
    ("EgoVehicle", "base"): "MinimalTrajectory/base",
    ("EgoVehicle", "adversarial"): "MinimalTrajectory/adversarial",
    ("ExploreFutureEgo", "adversarial"): "ExploreFuture/adversarial",
    ("ExploreFutureSlowerVehicle", "adversarial"): "ExploreFutureSlower/adversarial",
}


def read_json(path):
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def setting_label(log_dir):
    meta_path = log_dir / "meta.json"
    if not meta_path.exists():
        return log_dir.name

    meta = read_json(meta_path)
    ego_type = meta.get("egoType")
    ai_profile = meta.get("aiProfile")
    mapped = SETTING_LABELS.get((ego_type, ai_profile))
    if mapped:
        return mapped
    if ego_type and ai_profile:
        return f"{ego_type}/{ai_profile}"
    return log_dir.name


def ego_collision_delta_v(crash_detail, use_absolute):
    collision = crash_detail.get("collision") or {}
    first_role = collision.get("firstVehicleRole")
    second_role = collision.get("secondVehicleRole")

    if first_role == "EGO" and second_role is not None:
        ego_speed = collision.get("firstSpeed")
        other_speed = collision.get("secondSpeed")
    elif second_role == "EGO" and first_role is not None:
        ego_speed = collision.get("secondSpeed")
        other_speed = collision.get("firstSpeed")
    else:
        return None

    if ego_speed is None or other_speed is None:
        return None

    delta_v = float(other_speed) - float(ego_speed)
    return abs(delta_v) if use_absolute else delta_v


def read_delta_v_points(log_dir, use_absolute):
    crash_path = log_dir / "crash_details.json"
    if not crash_path.exists():
        raise FileNotFoundError(f"Missing crash_details.json: {crash_path}")

    points = []
    skipped = 0
    for crash in read_json(crash_path):
        delta_v = ego_collision_delta_v(crash, use_absolute)
        if delta_v is None:
            skipped += 1
            continue
        points.append(
            {
                "simulationIndex": crash.get("simulationIndex"),
                "deltaV": delta_v,
            }
        )
    return points, skipped


def default_output_path(use_absolute):
    name = "crash_delta_v_by_setting.png" if use_absolute else "crash_signed_delta_v_by_setting.png"
    return Path(__file__).resolve().parent / name


def plot(log_dirs, output_path, use_absolute):
    labels = [setting_label(log_dir) for log_dir in log_dirs]
    points_by_setting = []
    skipped_by_setting = []
    for log_dir in log_dirs:
        points, skipped = read_delta_v_points(log_dir, use_absolute)
        points_by_setting.append(points)
        skipped_by_setting.append(skipped)

    rng = Random(20260722)
    fig_width = max(10.5, 2.1 * len(log_dirs) + 3.0)
    fig, ax = plt.subplots(figsize=(fig_width, 5.6))

    for setting_index, points in enumerate(points_by_setting):
        x_values = [setting_index + rng.uniform(-0.08, 0.08) for _ in points]
        y_values = [point["deltaV"] for point in points]
        ax.scatter(
            x_values,
            y_values,
            s=58,
            alpha=0.85,
            edgecolors="black",
            linewidths=0.45,
        )

    for setting_index in range(len(labels) + 1):
        ax.axvline(setting_index - 0.5, color="#e0e0e0", linewidth=0.9)

    ax.axhline(0.0, color="#666666", linewidth=1.0)
    ax.set_xticks(range(len(labels)))
    ax.set_xticklabels(labels)
    ax.set_xlim(-0.55, len(labels) - 0.45)
    ax.set_ylabel(
        "|other vehicle speed - ego speed|" if use_absolute else "other vehicle speed - ego speed"
    )
    ax.set_xlabel("")
    ax.set_title("Crash DeltaV by Setting")
    ax.grid(axis="y", color="#d0d0d0", linewidth=0.8)

    fig.tight_layout()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path, dpi=220, bbox_inches="tight", pad_inches=0.18)
    plt.close(fig)

    return labels, points_by_setting, skipped_by_setting


def main():
    parser = argparse.ArgumentParser(
        description="Plot speed difference for vehicles colliding with EGO."
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
        help="Output PNG path. Defaults to scripts/crash_delta_v_by_setting.png.",
    )
    parser.add_argument(
        "--signed",
        action="store_true",
        help="Plot signed otherSpeed-egoSpeed instead of the default absolute speed difference.",
    )
    args = parser.parse_args()

    log_dirs = [path.resolve() for path in args.log_dirs]
    use_absolute = not args.signed
    output_path = (
        args.output.resolve()
        if args.output
        else default_output_path(use_absolute).resolve()
    )
    labels, points_by_setting, skipped_by_setting = plot(log_dirs, output_path, use_absolute)

    print(f"Wrote {output_path}")
    for label, points, skipped in zip(labels, points_by_setting, skipped_by_setting):
        print(f"{label}: plotted={len(points)}, skipped={skipped}")


if __name__ == "__main__":
    main()
