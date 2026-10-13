package bestsellers;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasetReaderTest {
    @Test
    void readsQuotedFieldsAndSkipsInvalidRows() throws Exception {
        Path csv = Files.createTempFile("books", ".csv");
        Files.writeString(csv, """
                Name,Author,User Rating,Reviews,Price,Year,Genre
                "Hello, World",Ada Lovelace,4.5,10,12,2020,Fiction
                too,short
                Bad,Numbers,nope,1,2,2020,Fiction
                Example,Ada Lovelace,4.7,3,8,2021,Non Fiction
                """);

        DatasetReader.LoadResult loaded = DatasetReader.load(csv.toString());

        assertEquals(2, loaded.books().size());
        assertEquals(2, loaded.skippedRows());
        assertEquals("Hello, World", loaded.books().get(0).getTitle());
        assertEquals(2, BookQueries.countBooksByAuthor(loaded.books(), "Ada Lovelace"));
    }

    @Test
    void bundledCatalogLoads() throws Exception {
        try (InputStream in = DatasetReader.class.getResourceAsStream("/data.csv")) {
            assertNotNull(in);
            Path csv = Files.createTempFile("catalog", ".csv");
            Files.copy(in, csv, StandardCopyOption.REPLACE_EXISTING);
            DatasetReader.LoadResult loaded = DatasetReader.load(csv.toString());
            assertTrue(loaded.books().size() > 100);
            assertTrue(BookQueries.countBooksByAuthor(loaded.books(), "George Orwell") > 0);
        }
    }
}
