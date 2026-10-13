package bestsellers;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookQueriesTest {
    private final List<Book> books = List.of(
            new Book("1984", "George Orwell", 4.7, 10, 6, 2017, "Fiction"),
            new Book("Animal Farm", "George Orwell", 4.5, 8, 7, 2016, "Fiction"),
            new Book("Cleanse", "JJ Smith", 4.7, 4, 8, 2016, "Non Fiction")
    );

    @Test
    void countsAuthorsWithoutCaseSensitivity() {
        assertEquals(2, BookQueries.countBooksByAuthor(books, "george orwell"));
        assertEquals(0, BookQueries.countBooksByAuthor(books, null));
        assertEquals(0, BookQueries.countBooksByAuthor(null, "George Orwell"));
    }

    @Test
    void listsAuthorsTitlesRatingsAndPrices() {
        assertEquals(List.of("George Orwell", "JJ Smith"), List.copyOf(BookQueries.getAllAuthors(books)));
        assertEquals(List.of("1984", "Animal Farm"), BookQueries.getBookTitlesByAuthor(books, "George Orwell"));
        assertEquals(2, BookQueries.getBooksByUserRating(books, 4.7).size());
        assertEquals(List.of("1984 - $6", "Animal Farm - $7"), BookQueries.getBookTitlesAndPricesByAuthor(books, "George Orwell"));
        assertTrue(BookQueries.getBookTitlesByAuthor(null, "George Orwell").isEmpty());
    }
}
