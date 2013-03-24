package org.jenkinsci.plugins.curfew_dispatcher;

import hudson.Extension;
import hudson.model.Project;
import hudson.model.Queue;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;
import org.joda.time.*;

@Extension
public class CurfewDispatcher extends QueueTaskDispatcher {

    /*package internal*/ static CalendarProvider CALENDAR_PROVIDER = new CalendarProvider() {
        public MutableDateTime getCalendar() {
            return new MutableDateTime();
        }
    };

    @Override
    public CauseOfBlockage canRun(Queue.Item item) {
        CurfewDispatcherConfigurationBuildWrapper configuration = null;
        if (item.task instanceof Project) {
            Project task = (Project) item.task;
            for (Object buildWrapper : task.getBuildWrappers().values()) {
                if (buildWrapper instanceof CurfewDispatcherConfigurationBuildWrapper)
                    configuration = (CurfewDispatcherConfigurationBuildWrapper) buildWrapper;
            }
        }

        if (configuration == null) {
            return super.canRun(item);
        } else {

            final MutableDateTime startOfCurfew = CALENDAR_PROVIDER.getCalendar();
            startOfCurfew.setHourOfDay(configuration.getStartHour());
            startOfCurfew.setMinuteOfHour(configuration.getStartMinutes());


            final MutableInterval curfew = new MutableInterval(startOfCurfew, new Duration(configuration.getDuration() * DateTimeConstants.MILLIS_PER_MINUTE));


            final Duration estimatedRunDuration = new Duration(item.task.getEstimatedDuration());
            final Interval run = new Interval(CALENDAR_PROVIDER.getCalendar(), estimatedRunDuration);

            Duration buffer = new Duration(0);
            if (configuration.getBufferType() != null) {
                switch (configuration.getBufferType()) {
                    case MINUTES:
                        buffer = new Duration(configuration.getBufferAmount() * DateTimeConstants.MILLIS_PER_MINUTE);
                        break;
                    case PERCENTAGE:
                        buffer = new Duration((estimatedRunDuration.getMillis() * configuration.getBufferAmount()) / 100);
                        break;
                }
                curfew.setStart(curfew.getStart().minus(buffer));
            }

            boolean plusOneIteration = false;
            do {
                if (run.overlaps(curfew)) {
                    return new CauseOfBlockage() {
                        @Override
                        public String getShortDescription() {
                            return Messages.curfewCause();
                        }
                    };
                }
                curfew.setEnd(curfew.getEnd().plusDays(1));
                curfew.setStart(curfew.getStart().plusDays(1));
            } while (plusOneIteration ^ (plusOneIteration |= !curfew.getStart().isBefore(CALENDAR_PROVIDER.getCalendar())));

            return null;
        }

    }

    interface CalendarProvider {
        MutableDateTime getCalendar();
    }
}
