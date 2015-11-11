package com.querydsl.webhooks;

import static org.junit.Assert.assertEquals;

import java.time.Duration;

import org.junit.Test;

public class MakeHumanReadableTest {

    @Test
    public void ShouldBeReadable() {
        testReadability("3 day", Duration.ofDays(3));

        testReadability("3 hour", Duration.ofHours(3));

        testReadability("3 minute", Duration.ofMinutes(3));

        testReadability("3 second", Duration.ofSeconds(3));
    }

    @Test
    public void ShouldBeReadableCompound() {
        testReadability("1 day 3 hour", Duration.ofDays(1).plusHours(3));

        testReadability("3 hour 10 minute", Duration.ofHours(3).plusMinutes(10));

        testReadability("5 day 30 minute", Duration.ofDays(5).plusMinutes(30));
    }

    private void testReadability(String expected, Duration duration) {
        assertEquals(expected, GithubReviewWindow.makeHumanReadable(duration));
    }
}
