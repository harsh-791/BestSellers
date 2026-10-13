package bestsellers;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class BookQueries {
    private BookQueries() {}

    public static long countBooksByAuthor(List<Book> books, String authorName) {
        if (books == null || authorName == null) {
            return 0L;
        }
        String normalizedAuthor = normalize(authorName);
        return books.stream()
                .filter(Objects::nonNull)
                .filter(book -> normalize(book.getAuthor()).equals(normalizedAuthor))
                .count();
    }

    public static Set<String> getAllAuthors(List<Book> books) {
        LinkedHashSet<String> authors = new LinkedHashSet<>();
        if (books == null) {
            return authors;
        }
        for (Book book : books) {
            if (book == null) {
                continue;
            }
            String author = book.getAuthor();
            if (author != null && !author.trim().isEmpty()) {
                authors.add(author.trim());
            }
        }
        return authors;
    }

    public static List<String> getBookTitlesByAuthor(List<Book> books, String authorName) {
        if (books == null || authorName == null) {
            return new ArrayList<>();
        }
        String normalizedAuthor = normalize(authorName);
        return books.stream()
                .filter(Objects::nonNull)
                .filter(book -> normalize(book.getAuthor()).equals(normalizedAuthor))
                .map(Book::getTitle)
                .collect(Collectors.toList());
    }

    public static List<Book> getBooksByUserRating(List<Book> books, double rating) {
        if (books == null) {
            return new ArrayList<>();
        }
        return books.stream()
                .filter(Objects::nonNull)
                .filter(book -> Double.compare(book.getUserRating(), rating) == 0)
                .collect(Collectors.toList());
    }

    public static List<String> getBookTitlesAndPricesByAuthor(List<Book> books, String authorName) {
        if (books == null || authorName == null) {
            return new ArrayList<>();
        }
        String normalizedAuthor = normalize(authorName);
        return books.stream()
                .filter(Objects::nonNull)
                .filter(book -> normalize(book.getAuthor()).equals(normalizedAuthor))
                .map(book -> book.getTitle() + " - $" + book.getPrice())
                .collect(Collectors.toList());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
