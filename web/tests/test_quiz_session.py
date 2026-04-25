import random
from services.quiz_session import build_quiz_session


def _qs(n):
    return [{"id": i} for i in range(n)]


def test_distribution_balanced():
    """5 subjects, 20 questions each, no prior quizzing → roughly even distribution."""
    pools = {i: _qs(20) for i in range(5)}
    times = {i: 0 for i in range(5)}
    rng = random.Random(42)
    picked = build_quiz_session(pools, times, count=30, rng=rng)
    assert len(picked) == 30
    counts = {i: 0 for i in range(5)}
    for p in picked:
        counts[p["subject_id"]] += 1
    # Each subject should appear at least 4 times in 30 picks (loose bound for randomness).
    assert all(c >= 4 for c in counts.values())


def test_underdog_weighting():
    """Subject A quizzed 10 times, subject B quizzed 0 → B should dominate."""
    pools = {1: _qs(20), 2: _qs(20)}
    times = {1: 10, 2: 0}
    rng = random.Random(42)
    picked = build_quiz_session(pools, times, count=20, rng=rng)
    counts = {1: 0, 2: 0}
    for p in picked:
        counts[p["subject_id"]] += 1
    # B has weight 1.0, A has weight ~0.09 → B should be at least 5x A.
    assert counts[2] >= counts[1] * 5


def test_pool_exhaustion():
    """2 subjects, 3 questions each → exactly 6 questions returned."""
    pools = {1: _qs(3), 2: _qs(3)}
    times = {1: 0, 2: 0}
    picked = build_quiz_session(pools, times, count=30, rng=random.Random(1))
    assert len(picked) == 6
    counts = {1: 0, 2: 0}
    for p in picked:
        counts[p["subject_id"]] += 1
    assert counts == {1: 3, 2: 3}


def test_empty_pools():
    assert build_quiz_session({}, {}) == []
    assert build_quiz_session({1: []}, {1: 0}) == []
