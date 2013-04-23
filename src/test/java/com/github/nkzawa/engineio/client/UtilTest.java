package com.github.nkzawa.engineio.client;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(JUnit4.class)
public class UtilTest {

    @Test
    public void qs() {
        Map<String, String> obj;

        obj = new HashMap<String, String>() {{
            put("a", "b");
        }};
        assertThat(Util.qs(obj), is("a=b"));

        obj = new LinkedHashMap<String, String>() {{
            put("a", "b");
            put("c", "d");
        }};
        assertThat(Util.qs(obj), is("a=b&c=d"));

        obj = new LinkedHashMap<String, String>() {{
            put("a", "b");
            put("c", "tobi rocks");
        }};
        assertThat(Util.qs(obj), is("a=b&c=tobi%20rocks"));
    }

    @Test
    public void encodeURIComponent() {
        assertThat(Util.encodeURIComponent(" ~'()! "), is("%20~'()!%20"));
        assertThat(Util.encodeURIComponent("+:;"), is("%2B%3A%3B"));
    }

    @Test
    public void decodeURIComponent() {
        assertThat(Util.decodeURIComponent("%20%7E%27%28%29%21%20"), is(" ~'()! "));
        assertThat(Util.decodeURIComponent("%2B%3A%3B"), is("+:;"));
    }
}
