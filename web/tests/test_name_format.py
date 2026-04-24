from routers.auth import normalize_name


def test_lowercase():
    assert normalize_name("alex smith") == "Alex Smith"


def test_uppercase():
    assert normalize_name("ALEX SMITH") == "Alex Smith"


def test_mixed():
    assert normalize_name("aLeX sMiTh") == "Alex Smith"


def test_single_initial():
    assert normalize_name("alex s") == "Alex S"


def test_extra_whitespace():
    assert normalize_name("  alex   s  ") == "Alex S"


def test_hyphenated():
    assert normalize_name("mary-jane LEE") == "Mary-Jane Lee"


def test_apostrophe():
    assert normalize_name("o'brien") == "O'brien"


def test_empty():
    assert normalize_name("") == ""


def test_whitespace_only():
    assert normalize_name("   ") == ""


def test_single_letter():
    assert normalize_name("a") == "A"


def test_three_words():
    assert normalize_name("mary ann lee") == "Mary Ann Lee"
