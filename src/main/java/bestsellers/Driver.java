package bestsellers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Driver {
    public static void main(String[] args) {
        String csvPath = "data.csv";
        if (args.length > 0 && args[0] != null && !args[0].trim().isEmpty()) {
            csvPath = args[0];
        } else if (!Files.exists(Path.of(csvPath)) && Files.exists(Path.of("src/main/resources/data.csv"))) {
            csvPath = "src/main/resources/data.csv";
        }
        try {
            List<Book> books = DatasetReader.readBooksFromCsv(csvPath);
            long count = BookQueries.countBooksByAuthor(books, "George R. R. Martin");
            System.out.println("Count: " + count);
            System.out.println(BookQueries.getAllAuthors(books));
            System.out.println(BookQueries.getBookTitlesByAuthor(books, "JJ Smith"));
            System.out.println(BookQueries.getBooksByUserRating(books, 4.7));
            System.out.println(BookQueries.getBookTitlesAndPricesByAuthor(books, "George Orwell"));

            for (Book book : books) {
                book.printDetails();
            }
            System.out.println("Total books: " + books.size());
        } catch (IOException e) {
            System.err.println("Failed to read CSV: " + e.getMessage());
            System.exit(1);
        }
    }
}
