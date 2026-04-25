from routers.auth import classify_name_match, MatchKind


def test_exact():
    assert classify_name_match("Alex", "Smith", "Alex", "Smith") == MatchKind.EXACT


def test_exact_case_insensitive():
    assert classify_name_match("alex", "smith", "Alex", "Smith") == MatchKind.EXACT


def test_prefix_longer():
    assert classify_name_match("Alex", "S", "Alex", "Smith") == MatchKind.PREFIX_LONGER


def test_prefix_shorter():
    assert classify_name_match("Alex", "Smith", "Alex", "S") == MatchKind.PREFIX_SHORTER


def test_same_initial():
    assert classify_name_match("Alex", "Smith", "Alex", "Smyth") == MatchKind.SAME_INITIAL


def test_different_first():
    assert classify_name_match("Alex", "S", "Alan", "Smith") is None


def test_different_initial():
    assert classify_name_match("Alex", "Jones", "Alex", "Smith") is None


def test_both_last_empty():
    assert classify_name_match("Alex", "", "Alex", "") == MatchKind.EXACT


def test_typed_last_empty_existing_has_last():
    assert classify_name_match("Alex", "", "Alex", "Smith") is None


def test_typed_has_last_existing_empty():
    assert classify_name_match("Alex", "Smith", "Alex", "") is None
