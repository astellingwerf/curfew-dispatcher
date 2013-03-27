package org.jenkinsci.plugins.curfew_dispatcher;

import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.Project;
import hudson.model.Queue;
import hudson.model.queue.CauseOfBlockage;
import hudson.tasks.BuildWrapper;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.MutableDateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(Parameterized.class)
public class CurfewDispatcherTest {
    //Instants
    private static final DateTime _1AM = createDateTime(1, 0);
    private static final DateTime _7PM = createDateTime(19, 0);
    private static final DateTime _7_30PM = createDateTime(19, 30);
    private static final DateTime _7_40PM = createDateTime(19, 40);
    private static final DateTime _7_50PM = createDateTime(19, 50);
    private static final DateTime _8PM = createDateTime(20, 0);
    private static final DateTime _9PM = createDateTime(21, 0);
    private static final DateTime _10PM = createDateTime(22, 0);
    private static final DateTime _11_30PM = createDateTime(23, 30);
    //Durations
    private static final Duration _MINUS1MILLIS = new Duration(-1);
    private static final Duration _0MIN = createDuration(0);
    private static final Duration _20MIN = createDuration(20);
    private static final Duration _1HR = createDuration(1, 0);
    private static final Duration _2HRS = createDuration(2, 0);
    private static final Duration _4HRS = createDuration(4, 0);
    //Others
    private final ParamsBuilder params;

    public CurfewDispatcherTest(ParamsBuilder params) {
        this.params = params;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        Object[][] data = new Object[][]{
                {verifyCurfew(_8PM, _2HRS).expectRun(_7_30PM, _20MIN).describingChallenge("all before curfew")},
                {verifyCurfew(_8PM, _2HRS).expectRun(_7_40PM, _20MIN).describingChallenge("end of run == start of curfew")},
                {verifyCurfew(_8PM, _2HRS).expectRun(_10PM, _20MIN).describingChallenge("start of run == end of curfew")},
                {verifyCurfew(_1AM, _2HRS).expectRun(_11_30PM, _1HR).describingChallenge("run starts before midnight")},
                {verifyCurfew(_8PM, _2HRS).expectBlockedRun(_9PM, _20MIN).describingChallenge("all within curfew")},
                {verifyCurfew(_8PM, _2HRS).expectBlockedRun(_9PM, _2HRS).describingChallenge("overlap with end of curfew")},
                {verifyCurfew(_8PM, _2HRS).expectBlockedRun(_7PM, _4HRS).describingChallenge("overlap with entire curfew")},
                {verifyCurfew(_8PM, _2HRS).expectBlockedRun(_7_50PM, _20MIN).describingChallenge("overlap with start of curfew")},
                {verifyCurfew(_1AM, _2HRS).expectBlockedRun(_11_30PM, _2HRS).describingChallenge("run starts before midnight, overlap with start of curfew")},
                {verifyCurfew(_1AM, _2HRS).expectBlockedRun(_11_30PM, _4HRS).describingChallenge("run starts before midnight, overlap with entire curfew")},
                //Negative/zero duration
                {verifyCurfew(_8PM, _2HRS).expectRun(_7_30PM, _MINUS1MILLIS).describingChallenge("estimated duration negative")},
                {verifyCurfew(_8PM, _2HRS).expectRun(_8PM, _MINUS1MILLIS).describingChallenge("estimated duration negative")},
                {verifyCurfew(_8PM, _2HRS).expectBlockedRun(_8PM.plus(1/* ms */), _MINUS1MILLIS).describingChallenge("estimated duration negative")},
                {verifyCurfew(_8PM, _2HRS).expectBlockedRun(_9PM, _MINUS1MILLIS).describingChallenge("estimated duration negative")},
                {verifyCurfew(_8PM, _2HRS).expectRun(_7_30PM, _0MIN).describingChallenge("estimated duration zero")},
                //Buffers
                {verifyCurfew(_8PM, _2HRS).expectRun(_7_40PM, _20MIN).withBufferMinutes(0).describingChallenge("end of run == start of curfew")},
                {verifyCurfew(_8PM, _2HRS).expectBlockedRun(_7_40PM, _20MIN).withBufferMinutes(1).describingChallenge("end of run == start of curfew")},
                {verifyCurfew(_8PM, _2HRS).expectRun(_7_40PM, _20MIN).withBufferPercentage(0).describingChallenge("end of run == start of curfew")},
                {verifyCurfew(_8PM, _2HRS).expectBlockedRun(_7_40PM, _20MIN).withBufferPercentage(1).describingChallenge("end of run == start of curfew")},
                {verifyCurfew(_8PM, _2HRS).expectRun(_7_30PM, _20MIN).withBufferPercentage(50).describingChallenge("all before curfew, with buffer on the edge")},
                {verifyCurfew(_1AM, _2HRS).expectBlockedRun(_11_30PM, _1HR).withBufferMinutes(31).describingChallenge("run starts before midnight, overlap with start of curfew")},
                {verifyCurfew(_1AM, _2HRS).expectBlockedRun(_11_30PM, _1HR).withBufferPercentage(51).describingChallenge("run starts before midnight, overlap with start of curfew")},
                {verifyCurfew(_1AM, _2HRS).expectBlockedRun(_11_30PM, _1HR).withBufferPercentage(300).describingChallenge("run starts before midnight, overlap with entire curfew")},
        };
        return Arrays.asList(data);
    }

    private static ParamsBuilder verifyCurfew(DateTime start, Duration duration) {
        return new ParamsBuilder(start, duration);
    }

    private static DateTime createDateTime(int hourOfDay, int minuteOfHour) {
        return new DateTime(2012, 4, 14, hourOfDay, minuteOfHour);
    }

    private static Duration createDuration(int minutes) {
        return createDuration(0, minutes);
    }

    private static Duration createDuration(int hours, int minutes) {
        return new Duration(((hours * 60) + minutes) * 60 * 1000);
    }

    @Test
    public void testCanRun() throws Exception {
        //Setup
        Project t = Mockito.mock(Project.class);
        Mockito.when(t.getEstimatedDuration()).thenReturn(params.duration.getMillis());

        Queue.Item i = new Queue.Item(t, new ArrayList<Action>(), 0, null) {
            @Override
            public CauseOfBlockage getCauseOfBlockage() {
                return null;
            }
        };

        Map<Descriptor<BuildWrapper>, BuildWrapper> map = new HashMap<Descriptor<BuildWrapper>, BuildWrapper>();
        map.put(new CurfewDispatcherConfigurationBuildWrapper.DescriptorImpl(), new CurfewDispatcherConfigurationBuildWrapper(
                params.curfewStart.getHourOfDay() * 100 + params.curfewStart.getMinuteOfHour(),
                (int) params.curfewDuration.getStandardMinutes(),
                params.bufferAmount,
                params.bufferType));
        Mockito.when(t.getBuildWrappers()).thenReturn(map);

        CurfewDispatcher.CALENDAR_PROVIDER = new CurfewDispatcher.CalendarProvider() {
            public MutableDateTime getCalendar() {
                return params.start.toMutableDateTime();
            }
        };

        //Action under test
        final CurfewDispatcher dispatcher = new CurfewDispatcher();
        final CauseOfBlockage causeOfBlockage = dispatcher.canRun(i);

        //Assertion
        if (params.expectRun) {
            assertNull((causeOfBlockage == null ? "" : causeOfBlockage.getShortDescription()), causeOfBlockage);
        }
        if (!params.expectRun) {
            assertNotNull(causeOfBlockage);
        }

    }

    static class ParamsBuilder {

        private final DateTime curfewStart;
        private final Duration curfewDuration;
        private boolean expectRun = false;
        private DateTime start;
        private Duration duration;
        private String challenge;
        private int bufferAmount;
        private CurfewDispatcherConfigurationBuildWrapper.BufferType bufferType;

        public ParamsBuilder(DateTime start, Duration duration) {
            this.curfewStart = start;
            this.curfewDuration = duration;
        }

        public ParamsBuilder expectRun(DateTime start, Duration duration) {
            return setExpectationForRun(start, duration, true);
        }

        public ParamsBuilder expectBlockedRun(DateTime start, Duration duration) {
            return setExpectationForRun(start, duration, false);
        }

        private ParamsBuilder setExpectationForRun(DateTime start, Duration duration, boolean expectRun) {
            this.start = start;
            this.duration = duration;
            this.expectRun = expectRun;

            return this;
        }

        private ParamsBuilder describingChallenge(String challenge) {
            this.challenge = challenge;

            return this;
        }

        private ParamsBuilder withBufferPercentage(int percentage) {
            return setBuffer(percentage, CurfewDispatcherConfigurationBuildWrapper.BufferType.PERCENTAGE);

        }

        private ParamsBuilder withBufferMinutes(int minutes) {
            return setBuffer(minutes, CurfewDispatcherConfigurationBuildWrapper.BufferType.MINUTES);

        }

        private ParamsBuilder setBuffer(int percentage, CurfewDispatcherConfigurationBuildWrapper.BufferType type) {
            this.bufferAmount = percentage;
            this.bufferType = type;

            return this;
        }

        @Override
        public String toString() {
            return "Curfew from " +
                    curfewStart.toString("HHmm") +
                    " till " + curfewStart.plus(curfewDuration).toString("HHmm") +
                    (bufferType == null ? "" : " + " + bufferAmount + (bufferType == CurfewDispatcherConfigurationBuildWrapper.BufferType.MINUTES ? " minutes" : "%")) +
                    ", expect " + (expectRun ? "a" : "NO") +
                    " run from " + start.toString("HHmm") +
                    " till " + start.plus(duration).toString("HHmm") +
                    (challenge == null ? "" : " (Challenge: " + challenge + ")");
        }
    }
}
