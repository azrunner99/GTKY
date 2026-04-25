"""
Quiz session builder.

Port of Android GTKYRepository.buildQuizSession + buildQuizSessionFromPools (Fix 11).
Each subject's per-attempt history weights how often they show up: a subject the player
has been quizzed on N times gets weight 1 / (1 + N). Fresh subjects come up most often,
well-quizzed subjects come up less but never zero.
"""

import random
from typing import Iterable

QUIZ_LENGTH = 30


def build_quiz_session(
    subject_pools: dict[int, list[dict]],
    times_quizzed: dict[int, int],
    count: int = QUIZ_LENGTH,
    rng: random.Random | None = None,
) -> list[dict]:
    """
    subject_pools: subject_id -> list of question dicts (already shuffled within subject).
    times_quizzed: subject_id -> historical quiz_results count for this guesser+subject.
    Returns a flat list of question dicts in weighted-random subject order.

    Each question dict carries enough context to be rendered ('subject_id', plus the
    fields the caller assembled per-question).
    """
    rng = rng or random.Random()
    pools = {k: list(v) for k, v in subject_pools.items() if v}  # mutable copies, drop empty
    if not pools:
        return []

    picked: list[dict] = []
    while len(picked) < count and pools:
        # Least-quizzed first so their weight occupies the low end of the cumulative range.
        subject_ids = sorted(pools.keys(), key=lambda sid: times_quizzed.get(sid, 0))
        weights = [1.0 / (1.0 + times_quizzed.get(sid, 0)) for sid in subject_ids]
        total_weight = sum(weights)
        r = rng.random() * total_weight
        chosen_idx = 0
        cum = 0.0
        for i, w in enumerate(weights):
            cum += w
            if r <= cum:
                chosen_idx = i
                break
        subject_id = subject_ids[chosen_idx]

        q = pools[subject_id].pop(0)
        q["subject_id"] = subject_id
        picked.append(q)

        if not pools[subject_id]:
            del pools[subject_id]

    return picked
