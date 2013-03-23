package org.jenkinsci.plugins.curfew_dispatcher;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

public class CurfewDispatcherConfigurationBuildWrapper extends BuildWrapper {

    private final String startTime;
    private final String duration;
    private transient Integer startMinutes;
    private transient Integer startHour;
    private transient Integer durationInt;

    @DataBoundConstructor
    public CurfewDispatcherConfigurationBuildWrapper(String startTime, String duration) {
        this.startTime = startTime;
        this.duration = duration;
    }

    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        return new Environment() {
        };
    }

    public String getStartTime() {
        return startTime;
    }

    public String getDuration() {
        return duration;
    }

    int getStartMinutes() {
        if (startMinutes == null) {
            startMinutes = Integer.parseInt(startTime.substring(startTime.length() - 2));
        }
        return startMinutes;
    }

    int getStartHour() {
        if (startHour == null) {
            startHour = Integer.parseInt(startTime.substring(0, startTime.length() - 2));
        }
        return startHour;
    }

    int getDurationInt() {
        if (durationInt == null) {
            durationInt = Integer.parseInt(duration);
        }
        return durationInt;
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.displayName();
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }
    }

}
