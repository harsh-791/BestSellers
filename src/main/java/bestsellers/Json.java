package bestsellers;

import java.util.List;

public final class Json {
    private Json() {}

    public static String quote(String value) {
        return "\"" + escape(value) + "\"";
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    public static String book(Book book) {
        return "{"
                + "\"title\":" + quote(book.getTitle()) + ","
                + "\"author\":" + quote(book.getAuthor()) + ","
                + "\"userRating\":" + book.getUserRating() + ","
                + "\"reviews\":" + book.getReviews() + ","
                + "\"price\":" + book.getPrice() + ","
                + "\"year\":" + book.getYear() + ","
                + "\"genre\":" + quote(book.getGenre())
                + "}";
    }

    public static String books(List<Book> books) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < books.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(book(books.get(i)));
        }
        out.append(']');
        return out.toString();
    }

    public static String strings(Iterable<String> values) {
        StringBuilder out = new StringBuilder("[");
        boolean first = true;
        for (String value : values) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(quote(value));
        }
        out.append(']');
        return out.toString();
    }
}
