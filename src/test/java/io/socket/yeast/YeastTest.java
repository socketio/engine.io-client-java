package io.socket.yeast;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.Date;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

;

@RunWith(JUnit4.class)
public class YeastTest {

    private void waitUntilNextMillisecond() {
        long now = new Date().getTime();
        while (new Date().getTime() == now) { /* do nothing */ }
    }

    @Test
    public void prependsIteratedSeedWhenSamePreviousId() {
        waitUntilNextMillisecond();

        String[] ids = new String[] { Yeast.yeast(), Yeast.yeast(), Yeast.yeast() };
        assertThat(ids[0], not(containsString(".")));
        assertThat(ids[1], containsString(".0"));
        assertThat(ids[2], containsString(".1"));
    }

    @Test
    public void resetsTheSeed() {
        waitUntilNextMillisecond();

        String[] ids = new String[] { Yeast.yeast(), Yeast.yeast(), Yeast.yeast() };
        assertThat(ids[0], not(containsString(".")));
        assertThat(ids[1], containsString(".0"));
        assertThat(ids[2], containsString(".1"));

        waitUntilNextMillisecond();

        ids = new String[] { Yeast.yeast(), Yeast.yeast(), Yeast.yeast() };
        assertThat(ids[0], not(containsString(".")));
        assertThat(ids[1], containsString(".0"));
        assertThat(ids[2], containsString(".1"));
    }

    @Test
    public void doesNotCollide() {
        int length = 30000;
        String[] ids = new String[length];

        for (int i = 0; i < length; i++) ids[i] = Yeast.yeast();

        Arrays.sort(ids);

        for (int i = 0; i < length - 1; i++) {
            assertThat(ids[i], not(equalTo(ids[i + 1])));
        }
    }

    @Test
    public void canConvertIdToTimestamp() {
        waitUntilNextMillisecond();

        long now = new Date().getTime();
        String id = Yeast.yeast();

        assertThat(Yeast.encode(now), equalTo(id));
        assertThat(Yeast.decode(id), equalTo(now));
    }
}
