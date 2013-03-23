package org.jenkinsci.plugins.curfew_dispatcher;

import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.Project;
import hudson.model.Queue;
import hudson.model.queue.CauseOfBlockage;
import hudson.tasks.BuildWrapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(Parameterized.class)
public class CurfewDispatcherTest {
    final ParamsBuilder params;

    public CurfewDispatcherTest(ParamsBuilder params) {
        this.params = params;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        Object[][] data = new Object[][]{
                {verifyCurfew(2000, 120).expectRun(1930, 20)},
                {verifyCurfew(2000, 120).expectBlockedRun(2100, 20)},
                {verifyCurfew(2000, 120).expectBlockedRun(2100, 120)},
                {verifyCurfew(2000, 120).expectBlockedRun(1950, 20)},
                {verifyCurfew(2000, 120).expectBlockedRun(1941, 20)},
                {verifyCurfew(2000, 120).expectBlockedRun(1945, 90)},
                {verifyCurfew(100, 120).expectBlockedRun(2330, 120)},
                {verifyCurfew(100, 120).expectBlockedRun(2330, 240)},
        };
        return Arrays.asList(data);
    }

    private static ParamsBuilder verifyCurfew(int start, int duration) {
        return new ParamsBuilder(start, duration);
    }

    @Test
    public void testCanRun() throws Exception {
        //Setup
        Project t = Mockito.mock(Project.class);
        Mockito.when(t.getEstimatedDuration()).thenReturn(params.duration * 60L * 1000L);

        Queue.Item i = new Queue.Item(t, new ArrayList<Action>(), 0, null) {
            @Override
            public CauseOfBlockage getCauseOfBlockage() {
                return null;
            }
        };

        Map<Descriptor<BuildWrapper>, BuildWrapper> map = new HashMap<Descriptor<BuildWrapper>, BuildWrapper>();
        map.put(new CurfewDispatcherConfigurationBuildWrapper.DescriptorImpl(), new CurfewDispatcherConfigurationBuildWrapper("" + params.curfewStart, "" + params.curfewDuration));
        Mockito.when(t.getBuildWrappers()).thenReturn(map);

        CurfewDispatcher.CALENDAR_PROVIDER = new CurfewDispatcher.CalendarProvider() {
            public Calendar getCalendar() {
                Calendar c = Calendar.getInstance();
                c.set(Calendar.HOUR_OF_DAY, params.start / 100);
                c.set(Calendar.MINUTE, params.start % 100);

                return c;
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

        private final int curfewStart;
        private final int curfewDuration;
        private boolean expectRun = false;
        private int start;
        private int duration;

        public ParamsBuilder(int start, int duration) {
            this.curfewStart = start;
            this.curfewDuration = duration;
        }

        public ParamsBuilder expectRun(int start, int duration) {
            return setExpectationForRun(start, duration, true);
        }

        public ParamsBuilder expectBlockedRun(int start, int duration) {
            return setExpectationForRun(start, duration, false);
        }

        private ParamsBuilder setExpectationForRun(int start, int duration, boolean expectRun) {
            this.start = start;
            this.duration = duration;
            this.expectRun = expectRun;

            return this;
        }

        @Override
        public String toString() {
            return "Curfew from " +
                    curfewStart +
                    " till " + ((curfewStart + (curfewDuration * 100 / 60)) % 2400) +
                    ", expect " + (expectRun ? "a" : "NO") +
                    " run from " + start +
                    " till " + ((start + (duration * 100 / 60)) % 2400);
        }
    }
}
