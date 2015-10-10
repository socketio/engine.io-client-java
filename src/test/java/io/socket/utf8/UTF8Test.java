package io.socket.utf8;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(JUnit4.class)
public class UTF8Test {
    private static final Data[] DATA = new Data[] {
        // 1-byte
        new Data(0x0000, "\u0000", "\u0000"),
        new Data(0x005c, "\u005C\u005C", "\u005C\u005C"), // = backslash
        new Data(0x007f, "\u007F", "\u007F"),
        // 2-byte
        new Data(0x0080, "\u0080", "\u00C2\u0080"),
        new Data(0x05CA, "\u05CA", "\u00D7\u008A"),
        new Data(0x07FF, "\u07FF", "\u00DF\u00BF"),
        // 3-byte
        new Data(0x0800, "\u0800", "\u00E0\u00A0\u0080"),
        new Data(0x2C3C, "\u2C3C", "\u00E2\u00B0\u00BC"),
        new Data(0x07FF, "\uFFFF", "\u00EF\u00BF\u00BF"),
        // unmatched surrogate halves
        // high surrogates: 0xD800 to 0xDBFF
        new Data(0xD800, "\uD800", "\u00ED\u00A0\u0080", true),
        new Data("High surrogate followed by another high surrogate",
                "\uD800\uD800", "\u00ED\u00A0\u0080\u00ED\u00A0\u0080", true),
        new Data("High surrogate followed by a symbol that is not a surrogate",
                "\uD800A", "\u00ED\u00A0\u0080A", true),
        new Data("Unmatched high surrogate, followed by a surrogate pair, followed by an unmatched high surrogate",
                "\uD800\uD834\uDF06\uD800", "\u00ED\u00A0\u0080\u00F0\u009D\u008C\u0086\u00ED\u00A0\u0080", true),
        new Data(0xD9AF, "\uD9AF", "\u00ED\u00A6\u00AF", true),
        new Data(0xDBFF, "\uDBFF", "\u00ED\u00AF\u00BF", true),
        // low surrogates: 0xDC00 to 0xDFFF
        new Data(0xDC00, "\uDC00", "\u00ED\u00B0\u0080", true),
        new Data("Low surrogate followed by another low surrogate",
                "\uDC00\uDC00", "\u00ED\u00B0\u0080\u00ED\u00B0\u0080", true),
        new Data("Low surrogate followed by a symbol that is not a surrogate",
                "\uDC00A", "\u00ED\u00B0\u0080A", true),
        new Data("Unmatched low surrogate, followed by a surrogate pair, followed by an unmatched low surrogate",
                "\uDC00\uD834\uDF06\uDC00", "\u00ED\u00B0\u0080\u00F0\u009D\u008C\u0086\u00ED\u00B0\u0080", true),
        new Data(0xDEEE, "\uDEEE", "\u00ED\u00BB\u00AE", true),
        new Data(0xDFFF, "\uDFFF", "\u00ED\u00BF\u00BF", true),
        // 4-byte
        new Data(0x010000, "\uD800\uDC00", "\u00F0\u0090\u0080\u0080"),
        new Data(0x01D306, "\uD834\uDF06", "\u00F0\u009D\u008C\u0086"),
        new Data(0x010FFF, "\uDBFF\uDFFF", "\u00F4\u008F\u00BF\u00BF"),
    };

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void encodeAndDecode() throws UTF8Exception {
        for (Data data : DATA) {
            String reason = data.description != null? data.description : "U+" + Integer.toHexString(data.codePoint).toUpperCase();
            if (data.error) {
                exception.expect(UTF8Exception.class);
                UTF8.decode(data.encoded);
                exception.expect(UTF8Exception.class);
                UTF8.encode(data.decoded);
            } else {
                assertThat("Encoding: " + reason, data.encoded, is(UTF8.encode(data.decoded)));
                assertThat("Decoding: " + reason, data.decoded, is(UTF8.decode(data.encoded)));
            }
        }

        exception.expect(UTF8Exception.class);
        UTF8.decode("\uFFFF");

        exception.expect(UTF8Exception.class);
        UTF8.decode("\u00E9\u0000\u0000");

        exception.expect(UTF8Exception.class);
        UTF8.decode("\u00C2\uFFFF");

        exception.expect(UTF8Exception.class);
        UTF8.decode("\u00F0\u009D");
    }

    private static class Data {
        public int codePoint = -1;
        public String description;
        public String decoded;
        public String encoded;
        public boolean error;

        public Data(int codePoint, String decoded, String encoded) {
            this(codePoint, decoded, encoded, false);
        }

        public Data(int codePoint, String decoded, String encoded, boolean error) {
            this.codePoint = codePoint;
            this.decoded = decoded;
            this.encoded = encoded;
            this.error = error;
        }

        public Data(String description, String decoded, String encoded) {
            this(description, decoded, encoded, false);
        }

        public Data(String description, String decoded, String encoded, boolean error) {
            this.description = description;
            this.decoded = decoded;
            this.encoded = encoded;
            this.error = error;
        }
    }
}
