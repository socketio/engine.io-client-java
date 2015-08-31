package io.socket.parseqs;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(JUnit4.class)
public class ParseQSTest {

    @Test
    public void decode() {
        Map<String, String> queryObject = ParseQS.decode("foo=bar");
        assertThat(queryObject.get("foo"), is("bar"));

        queryObject = ParseQS.decode("france=paris&germany=berlin");
        assertThat(queryObject.get("france"), is("paris"));
        assertThat(queryObject.get("germany"), is("berlin"));

        queryObject = ParseQS.decode("india=new%20delhi");
        assertThat(queryObject.get("india"), is("new delhi"));

        queryObject = ParseQS.decode("woot=");
        assertThat(queryObject.get("woot"), is(""));

        queryObject = ParseQS.decode("woot");
        assertThat(queryObject.get("woot"), is(""));
    }

    @Test
    public void encode() {
        Map<String, String> obj;

        obj = new HashMap<String, String>() {{
            put("a", "b");
        }};
        assertThat(ParseQS.encode(obj), is("a=b"));

        obj = new LinkedHashMap<String, String>() {{
            put("a", "b");
            put("c", "d");
        }};
        assertThat(ParseQS.encode(obj), is("a=b&c=d"));

        obj = new LinkedHashMap<String, String>() {{
            put("a", "b");
            put("c", "tobi rocks");
        }};
        assertThat(ParseQS.encode(obj), is("a=b&c=tobi%20rocks"));
    }
}
