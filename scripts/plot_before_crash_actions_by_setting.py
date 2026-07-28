import argparse
import json
from pathlib import Path
from random import Random

import matplotlib.lines as mlines
import matplotlib.pyplot as plt


SETTING_LABELS = {
    ("EgoVehicle", "base"): "MinimalTrajectory/base",
    ("EgoVehicle", "adversarial"): "MinimalTrajectory/adversarial",
    ("ExploreFutureEgo", "adversarial"): "ExploreFuture/adversarial",
    ("ExploreFutureSlowerVehicle", "adversarial"): "ExploreFutureSlower/adversarial",
}

ACTION_COLORS = {
    "LANE_LEFT": "#1f77b4",
    "LANE_RIGHT": "#ff7f0e",
    "IDLE": "#7f7f7f",
    "FASTER": "#2ca02c",
    "SLOWER": "#d62728",
}

ACTION_ORDER = ["LANE_LEFT", "LANE_RIGHT", "IDLE", "FASTER", "SLOWER"]
ACTION_ROWS = ["a_1", "a_2", "a_3"]


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


def before_crash_action_points(log_dir):
    crash_path = log_dir / "crash_details.json"
    if not crash_path.exists():
        raise FileNotFoundError(f"Missing crash_details.json: {crash_path}")

    points = []
    skipped = 0
    for crash in read_json(crash_path):
        simulation_index = crash.get("simulationIndex")
        actions = crash.get("beforeCrashActions") or []
        if not actions:
            skipped += 1
            continue

        selected_actions = actions[-3:]
        for action_index, action_log in enumerate(selected_actions):
            performed_action = action_log.get("performedAction")
            shield_safe = action_log.get("shieldSafe")
            if performed_action is None or shield_safe is None:
                skipped += 1
                continue
            points.append(
                {
                    "simulationIndex": simulation_index,
                    "actionIndex": action_index,
                    "performedAction": performed_action,
                    "shieldSafe": bool(shield_safe),
                    "step": action_log.get("step"),
                    "time": action_log.get("time"),
                }
            )
    return points, skipped


def default_output_path():
    return Path(__file__).resolve().parent / "before_crash_actions_by_setting.png"


def plot(log_dirs, output_path):
    labels = [setting_label(log_dir) for log_dir in log_dirs]
    points_by_setting = []
    skipped_by_setting = []
    for log_dir in log_dirs:
        points, skipped = before_crash_action_points(log_dir)
        points_by_setting.append(points)
        skipped_by_setting.append(skipped)

    rng = Random(20260722)
    fig_width = max(10.5, 2.1 * len(log_dirs) + 3.0)
    fig, ax = plt.subplots(figsize=(fig_width, 6.2))

    setting_width = 1.0
    action_offsets = [-0.27, 0.0, 0.27]
    y_by_action = {"a_1": 2, "a_2": 1, "a_3": 0}

    for setting_index, points in enumerate(points_by_setting):
        for point in points:
            action_index = point["actionIndex"]
            row_name = ACTION_ROWS[action_index]
            x = setting_index * setting_width + action_offsets[action_index]
            x += rng.uniform(-0.035, 0.035)
            y = y_by_action[row_name] + rng.uniform(-0.055, 0.055)
            marker = "o" if point["shieldSafe"] else "D"
            color = ACTION_COLORS.get(point["performedAction"], "#9467bd")
            ax.scatter(
                x,
                y,
                s=58,
                marker=marker,
                color=color,
                alpha=0.85,
                edgecolors="black",
                linewidths=0.45,
            )

    for setting_index in range(len(labels) + 1):
        ax.axvline(setting_index - 0.5, color="#e0e0e0", linewidth=0.9)

    for setting_index in range(len(labels)):
        for offset, action_name in zip(action_offsets, ACTION_ROWS):
            ax.text(
                setting_index * setting_width + offset,
                -0.42,
                action_name,
                ha="center",
                va="top",
                fontsize=9,
                color="#555555",
            )

    labels_with_counts = labels
    ax.set_xticks([i * setting_width for i in range(len(labels))])
    ax.set_xticklabels(labels_with_counts)
    ax.set_yticks([2, 1, 0])
    ax.set_yticklabels(ACTION_ROWS)
    ax.set_xlim(-0.55, len(labels) - 0.45)
    ax.set_ylim(-0.72, 2.55)
    ax.set_xlabel("")
    ax.set_ylabel("Before-crash action slot")
    ax.set_title("Before-Crash Actions by Setting")
    ax.grid(axis="y", color="#d0d0d0", linewidth=0.8)

    action_handles = [
        mlines.Line2D(
            [],
            [],
            color=ACTION_COLORS[action],
            marker="o",
            linestyle="None",
            markersize=8,
            label=action,
        )
        for action in ACTION_ORDER
    ]
    safety_handles = [
        mlines.Line2D(
            [],
            [],
            color="white",
            marker="o",
            markeredgecolor="black",
            linestyle="None",
            markersize=8,
            label="safe",
        ),
        mlines.Line2D(
            [],
            [],
            color="white",
            marker="D",
            markeredgecolor="black",
            linestyle="None",
            markersize=8,
            label="unsafe",
        ),
    ]
    first_legend = ax.legend(
        handles=action_handles,
        title="Performed action",
        loc="upper center",
        bbox_to_anchor=(0.5, -0.22),
        ncol=len(action_handles),
        frameon=False,
    )
    ax.add_artist(first_legend)
    ax.legend(
        handles=safety_handles,
        title="Shield verdict",
        loc="upper center",
        bbox_to_anchor=(0.5, -0.38),
        ncol=len(safety_handles),
        frameon=False,
    )

    fig.tight_layout()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path, dpi=220, bbox_inches="tight", pad_inches=0.25)
    plt.close(fig)

    return labels, points_by_setting, skipped_by_setting


def main():
    parser = argparse.ArgumentParser(
        description="Plot the last three before-crash actions by setting."
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
        help="Output PNG path. Defaults to scripts/before_crash_actions_by_setting.png.",
    )
    args = parser.parse_args()

    log_dirs = [path.resolve() for path in args.log_dirs]
    output_path = args.output.resolve() if args.output else default_output_path()
    labels, points_by_setting, skipped_by_setting = plot(log_dirs, output_path)

    print(f"Wrote {output_path}")
    for label, points, skipped in zip(labels, points_by_setting, skipped_by_setting):
        print(f"{label}: plotted={len(points)}, skipped={skipped}")


if __name__ == "__main__":
    main()
