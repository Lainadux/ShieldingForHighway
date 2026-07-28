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
    / "threshold_acceptance_20260727_225745"
)


PANEL_ORDER = [
    ("adversarial", 2),
    ("adversarial", 3),
    ("adversarial", 4),
    ("base", 2),
    ("base", 3),
    ("base", 4),
]


PANEL_COLORS = {
    ("adversarial", 2): "#8fb7df",
    ("adversarial", 3): "#f2b173",
    ("adversarial", 4): "#99c78f",
    ("base", 2): "#e99c9c",
    ("base", 3): "#9bcfd0",
    ("base", 4): "#d7a7cf",
}


def read_json(path):
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def parse_group_dir(group_dir):
    match = re.fullmatch(r"(base|adversarial)_threshold_(\d+)", group_dir.name)
    if not match:
        return None
    return match.group(1), int(match.group(2))


def discover_groups(log_root):
    groups = {}
    for child in log_root.iterdir():
        if not child.is_dir():
            continue
        key = parse_group_dir(child)
        if key is None:
            continue
        acceptance_path = child / "episode_acceptance_sequences.json"
        if acceptance_path.exists():
            groups[key] = acceptance_path
    return groups


def decision_value(decision):
    return 1.0 if bool(decision.get("shieldSafe", False)) else 0.0


def max_decision_index(group_sequences):
    max_decision = 0
    for sequences in group_sequences.values():
        for sequence in sequences:
            for decision in sequence.get("decisions", []):
                max_decision = max(max_decision, int(decision.get("decisionIndex", 0)))
    return max_decision


def plot_panel(ax, sequences, title, color, max_decision):
    sequences = sorted(sequences, key=lambda item: item.get("simulationIndex", 0))
    episode_count = max(1, len(sequences))
    band_height = 0.72

    for row, sequence in enumerate(sequences):
        decisions = sorted(sequence.get("decisions", []), key=lambda item: item.get("decisionIndex", 0))
        if not decisions:
            continue

        xs = []
        ys = []
        for decision in decisions:
            xs.append(int(decision.get("decisionIndex", 0)))
            ys.append(row + decision_value(decision) * band_height)
        xs.append(max_decision + 1)
        ys.append(ys[-1])

        ax.step(xs, ys, where="post", color=color, linewidth=0.72, alpha=0.52)

    ax.set_title(title, fontsize=10)
    ax.set_xlim(0, max_decision + 1)
    ax.set_ylim(-0.5, episode_count - 1 + band_height + 0.5)
    ax.grid(True, linestyle="--", linewidth=0.35, alpha=0.3)

    y_tick_step = max(1, episode_count // 5)
    y_ticks = list(range(0, episode_count, y_tick_step))
    ax.set_yticks(y_ticks)
    ax.set_yticklabels([f"e{tick}" for tick in y_ticks], fontsize=8)
    ax.tick_params(axis="x", labelsize=8)


def plot(log_root, output_path):
    groups = discover_groups(log_root)
    missing = [key for key in PANEL_ORDER if key not in groups]
    if missing:
        missing_text = ", ".join(f"{profile}_threshold_{threshold}" for profile, threshold in missing)
        raise FileNotFoundError(f"Missing acceptance sequence groups under {log_root}: {missing_text}")

    group_sequences = {key: read_json(path) for key, path in groups.items() if key in PANEL_ORDER}
    max_decision = max_decision_index(group_sequences)

    fig, axes = plt.subplots(2, 3, figsize=(14.5, 8.2), dpi=220, sharex=True)
    fig.suptitle("Per-episode AI action acceptance sequences", fontsize=13)

    for ax, key in zip(axes.flat, PANEL_ORDER):
        profile, threshold = key
        title = f"{profile}_threshold_{threshold}"
        plot_panel(ax, group_sequences[key], title, PANEL_COLORS[key], max_decision)

    for ax in axes[1, :]:
        ax.set_xlabel("Decision step")
    axes[0, 0].set_ylabel("adversarial\nEpisode")
    axes[1, 0].set_ylabel("base\nEpisode")

    fig.text(
        0.5,
        0.025,
        "upper = 1 accepted, lower = 0 rejected",
        ha="center",
        va="center",
        fontsize=9,
    )

    fig.tight_layout(rect=(0, 0.055, 1, 0.96))
    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path)
    plt.close(fig)


def main():
    parser = argparse.ArgumentParser(
        description="Plot 2x3 acceptance sequence panels for threshold acceptance logs."
    )
    parser.add_argument(
        "log_root",
        nargs="?",
        type=Path,
        default=DEFAULT_LOG_ROOT,
        help="threshold_acceptance_* log root directory.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(__file__).resolve().parent / "threshold_acceptance_sequence_lines.png",
        help="Output image path.",
    )
    args = parser.parse_args()

    plot(args.log_root, args.output)
    print(f"Wrote {args.output}")


if __name__ == "__main__":
    main()
