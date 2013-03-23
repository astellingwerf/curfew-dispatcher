package org.jenkinsci.plugins.curfew_dispatcher;

import hudson.Extension;
import hudson.model.Project;
import hudson.model.Queue;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;

import java.util.Calendar;

@Extension
public class CurfewDispatcher extends QueueTaskDispatcher {

    /*package internal*/ static CalendarProvider CALENDAR_PROVIDER = new CalendarProvider() {
        public Calendar getCalendar() {
            return Calendar.getInstance();
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
            final Calendar startOfCurfew = CALENDAR_PROVIDER.getCalendar();
            startOfCurfew.set(Calendar.HOUR_OF_DAY, configuration.getStartHour());
            startOfCurfew.set(Calendar.MINUTE, configuration.getStartMinutes());

            final Calendar endOfCurfew = (Calendar) startOfCurfew.clone();
            endOfCurfew.add(Calendar.MINUTE, configuration.getDurationInt());

            final Calendar estimatedEndOfItem = CALENDAR_PROVIDER.getCalendar();
            estimatedEndOfItem.add(Calendar.SECOND, (int) (item.task.getEstimatedDuration() / 1000L));

            while (startOfCurfew.before(estimatedEndOfItem)) {
                if (estimatedEndOfItem.after(startOfCurfew) && estimatedEndOfItem.before(endOfCurfew)) {
                    return new CauseOfBlockage() {
                        @Override
                        public String getShortDescription() {
                            return Messages.curfewCause();
                        }
                    };
                }
                estimatedEndOfItem.add(Calendar.DAY_OF_YEAR, -1);
            }

            return null;
        }

    }

    interface CalendarProvider {
        Calendar getCalendar();
    }
}
