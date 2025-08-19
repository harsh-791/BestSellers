import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DatasetReader {
    public static List<Book> readBooksFromCsv(String csvPath) throws IOException {
        List<Book> books = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {
            String line;
            boolean isFirstLine = true;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                if (isFirstLine) {
                    isFirstLine = false;
                    if (line.toLowerCase().contains("name") && line.toLowerCase().contains("author")) {
                        continue;
                    }
                }
                String[] fields = parseCsv(line);
                if (fields.length < 7) {
                    continue;
                }
                try {
                    String title = fields[0];
                    String author = fields[1];
                    double userRating = Double.parseDouble(fields[2]);
                    int reviews = Integer.parseInt(fields[3]);
                    int price = Integer.parseInt(fields[4]);
                    int year = Integer.parseInt(fields[5]);
                    String genre = fields[6];
                    books.add(new Book(title, author, userRating, reviews, price, year, genre));
                } catch (NumberFormatException ex) {}
            }
        }
        return books;
    }

    private static String[] parseCsv(String line) {
        String[] fields = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
        for (int i = 0; i < fields.length; i++) {
            String value = fields[i].trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1).replace("\"\"", "\"");
            }
            fields[i] = value;
        }
        return fields;
    }
} 