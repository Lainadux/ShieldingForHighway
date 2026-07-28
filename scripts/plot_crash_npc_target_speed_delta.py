import argparse
import json
import re
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


def read_meta(log_dir):
    meta_path = log_dir / "meta.json"
    return read_json(meta_path) if meta_path.exists() else {}


def setting_label(log_dir):
    meta = read_meta(log_dir)
    ego_type = meta.get("egoType")
    ai_profile = meta.get("aiProfile")
    mapped = SETTING_LABELS.get((ego_type, ai_profile))
    if mapped:
        return mapped
    if ego_type and ai_profile:
        return f"{ego_type}/{ai_profile}"
    return log_dir.name


def find_initial_state_path(log_dir, simulation_index):
    pattern = re.compile(rf"^crashed_.*_{re.escape(str(simulation_index))}\.json$")
    matches = [path for path in log_dir.glob("crashed_*.json") if pattern.match(path.name)]
    if len(matches) == 1:
        return matches[0]
    if not matches:
        return None
    return sorted(matches, key=lambda path: path.stat().st_mtime)[-1]


def read_initial_vehicles(log_dir, simulation_index):
    initial_state_path = find_initial_state_path(log_dir, simulation_index)
    if initial_state_path is None:
        return {}

    data = read_json(initial_state_path)
    vehicles = data.get("value", data) if isinstance(data, dict) else data
    if not isinstance(vehicles, list):
        return {}
    return {str(vehicle.get("id")): vehicle for vehicle in vehicles}


def collision_npc_participants(collision):
    participants = []
    for prefix in ("first", "second"):
        if collision.get(f"{prefix}VehicleRole") == "NPC":
            participants.append(
                {
                    "id": str(collision.get(f"{prefix}VehicleId")),
                    "speed": collision.get(f"{prefix}Speed"),
                    "targetSpeed": collision.get(f"{prefix}TargetSpeed"),
                }
            )
    return participants


def crash_delta_points(log_dir):
    crash_path = log_dir / "crash_details.json"
    if not crash_path.exists():
        raise FileNotFoundError(f"Missing crash_details.json: {crash_path}")

    points = []
    skipped = 0
    for crash in read_json(crash_path):
        collision = crash.get("collision") or {}
        simulation_index = crash.get("simulationIndex")
        initial_vehicles = read_initial_vehicles(log_dir, simulation_index)

        for npc in collision_npc_participants(collision):
            speed = npc.get("speed")
            target_speed = npc.get("targetSpeed")
            if target_speed is None:
                initial_vehicle = initial_vehicles.get(npc["id"])
                if initial_vehicle is not None:
                    target_speed = initial_vehicle.get("targetSpeed")

            if speed is None or target_speed is None:
                skipped += 1
                continue

            points.append(
                {
                    "simulationIndex": simulation_index,
                    "npcId": npc["id"],
                    "delta": float(target_speed) - float(speed),
                    "targetSpeed": float(target_speed),
                    "speed": float(speed),
                }
            )
    return points, skipped


def default_output_path():
    return Path(__file__).resolve().parent / "crash_npc_target_speed_delta_by_setting.png"


def plot(log_dirs, output_path):
    labels = [setting_label(log_dir) for log_dir in log_dirs]
    points_by_setting = []
    skipped_by_setting = []
    for log_dir in log_dirs:
        points, skipped = crash_delta_points(log_dir)
        points_by_setting.append(points)
        skipped_by_setting.append(skipped)

    fig_height = max(4.0, 0.8 * len(log_dirs) + 2.2)
    fig, ax = plt.subplots(figsize=(11.0, fig_height))
    rng = Random(20260722)

    for x, points in enumerate(points_by_setting):
        jittered_x = [x + rng.uniform(-0.08, 0.08) for _ in points]
        deltas = [point["delta"] for point in points]
        ax.scatter(
            jittered_x,
            deltas,
            s=58,
            alpha=0.85,
            edgecolors="black",
            linewidths=0.45,
        )

    labels_with_counts = labels
    ax.set_xticks(range(len(labels_with_counts)))
    ax.set_xticklabels(labels_with_counts)
    ax.axhline(0.0, color="#666666", linewidth=1.0)
    ax.set_ylabel("Crashed NPC targetSpeed - speed")
    ax.set_xlabel("")
    ax.set_title("Crashed NPC targetSpeed and speed gap")
    ax.grid(axis="y", color="#d0d0d0", linewidth=0.8)
    ax.grid(axis="x", color="#ededed", linewidth=0.7)

    fig.tight_layout()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path, dpi=220)
    plt.close(fig)

    return labels, points_by_setting, skipped_by_setting


def main():
    parser = argparse.ArgumentParser(
        description="Plot targetSpeed-speed for NPCs involved in crashes."
    )
    parser.add_argument(
        "log_dirs",
        nargs="+",
        type=Path,
        help="Directories containing crash_details.json, meta.json, and crashed initial-state logs.",
    )
    parser.add_argument(
        "-o",
        "--output",
        type=Path,
        default=None,
        help="Output PNG path. Defaults to scripts/crash_npc_target_speed_delta_by_setting.png.",
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
