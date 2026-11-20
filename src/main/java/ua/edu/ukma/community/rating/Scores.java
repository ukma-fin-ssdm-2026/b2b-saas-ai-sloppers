package ua.edu.ukma.community.rating;

/** Шкала оцінок 1–5 (FR-061, FR-063); те саме перевіряє {@code CHECK} у V9. */
final class Scores {

    static final int MIN = 1;
    static final int MAX = 5;

    private Scores() {
    }

    static void requireValid(int score) {
        if (score < MIN || score > MAX) {
            throw new IllegalArgumentException("score must be between " + MIN + " and " + MAX + ": " + score);
        }
    }
}
