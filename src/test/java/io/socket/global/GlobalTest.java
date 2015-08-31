package io.socket.global;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(JUnit4.class)
public class GlobalTest {

    @Test
    public void encodeURIComponent() {
        assertThat(Global.encodeURIComponent(" ~'()! "), is("%20~'()!%20"));
        assertThat(Global.encodeURIComponent("+:;"), is("%2B%3A%3B"));
    }

    @Test
    public void decodeURIComponent() {
        assertThat(Global.decodeURIComponent("%20%7E%27%28%29%21%20"), is(" ~'()! "));
        assertThat(Global.decodeURIComponent("%2B%3A%3B"), is("+:;"));
    }
}
