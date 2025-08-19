import java.io.IOException;
import java.util.List;

public class Driver {
    public static void main(String[] args) {
        String csvPath = "data.csv";
        try {
            List<Book> books = DatasetReader.readBooksFromCsv(csvPath);
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