import json
import sys
from pathlib import Path

import matplotlib.pyplot as plt


def main() -> int:
    if len(sys.argv) not in (2, 3):
        print("Usage: python scripts/plot_acceptance_sequences.py <episode_acceptance_sequences.json> [output.png]")
        return 2

    input_file = Path(sys.argv[1])
    output_file = Path(sys.argv[2]) if len(sys.argv) == 3 else input_file.with_name("episode_acceptance_sequence_lines.png")
    log_dir = input_file.parent
    meta_file = log_dir / "meta.json"

    if not input_file.exists():
        print(f"Missing acceptance sequence file: {input_file}")
        return 1

    sequences = json.loads(input_file.read_text(encoding="utf-8"))
    meta = {}
    if meta_file.exists():
        meta = json.loads(meta_file.read_text(encoding="utf-8"))

    max_decision = 0
    for sequence in sequences:
        for decision in sequence.get("decisions", []):
            max_decision = max(max_decision, int(decision.get("decisionIndex", 0)))

    episode_count = max(1, len(sequences))
    fig_height = max(6.0, min(32.0, 1.2 + episode_count * 0.055))
    fig, ax = plt.subplots(figsize=(14, fig_height), dpi=200)

    normal_color = "#7aa6d8"
    crashed_color = "#d56f6f"
    band_height = 0.72

    for row, sequence in enumerate(sorted(sequences, key=lambda item: item["simulationIndex"])):
        decisions = sorted(sequence.get("decisions", []), key=lambda item: item.get("decisionIndex", 0))
        if not decisions:
            continue

        xs = []
        ys = []
        for decision in decisions:
            decision_index = int(decision.get("decisionIndex", 0))
            value = float(decision.get("acceptanceValue", 0.0))
            value = min(1.0, max(0.0, value))
            xs.append(decision_index)
            ys.append(row + value * band_height)
        xs.append(max_decision + 1)
        ys.append(ys[-1])

        color = crashed_color if sequence.get("crashed", False) else normal_color
        ax.step(xs, ys, where="post", color=color, linewidth=0.75, alpha=0.48)

    title = "Per-episode AI action acceptance sequences"
    subtitle_parts = [
        meta.get("egoType"),
        meta.get("aiProfile"),
        f"shield={meta.get('shieldType')}" if meta.get("shieldType") else None,
    ]
    subtitle = " / ".join(str(part) for part in subtitle_parts if part)
    ax.set_title(title if not subtitle else f"{title}\n{subtitle}", fontsize=13)
    ax.set_xlabel("Decision step")
    ax.set_ylabel("Episode")
    ax.set_xlim(0, max_decision + 1)
    ax.set_ylim(-0.5, episode_count - 1 + band_height + 0.5)
    ax.grid(True, linestyle="--", linewidth=0.4, alpha=0.35)

    y_tick_step = max(1, episode_count // 8)
    y_ticks = list(range(0, episode_count, y_tick_step))
    ax.set_yticks(y_ticks)
    ax.set_yticklabels([f"e{tick}" for tick in y_ticks])

    ax.text(
        1.01,
        0.5,
        "upper=1 accepted\nmiddle=0.5 cache assist\nlower=0 rejected\nred=crashed run",
        transform=ax.transAxes,
        va="center",
        fontsize=9,
    )

    fig.tight_layout(rect=(0, 0, 0.86, 1))
    fig.savefig(output_file)
    plt.close(fig)
    print(f"Wrote {output_file}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
