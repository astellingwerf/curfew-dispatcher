package org.jenkinsci.plugins.curfew_dispatcher;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;


public class CurfewDispatcherConfigurationBuildWrapper extends BuildWrapper {


    private final int startMinutes;
    private final int startHour;
    private final int duration;
    private final int bufferAmount;
    private final BufferType bufferType;

    @DataBoundConstructor
    public CurfewDispatcherConfigurationBuildWrapper(int startTime, int duration, int bufferAmount, BufferType bufferType) {
        this.startHour = startTime / 100;
        this.startMinutes = startTime % 100;
        this.duration = duration;
        this.bufferAmount = bufferAmount;
        this.bufferType = bufferType;
    }

    //@DataBoundConstructor
    public CurfewDispatcherConfigurationBuildWrapper(int startTime, int duration) {
        this(startTime, duration, 0, null);
    }

    public int getBufferAmount() {
        return bufferAmount;
    }

    public BufferType getBufferType() {
        return bufferType;
    }

    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        return new Environment() {
        };
    }

    public String getStartTime() {
        return String.format("%02d%02d", startHour, startMinutes);
    }

    public int getDuration() {
        return duration;
    }

    int getStartMinutes() {
        return startMinutes;
    }

    int getStartHour() {
        return startHour;
    }

    enum BufferType {
        PERCENTAGE,
        MINUTES;

        private static BufferType fromString(String s) {
            if (s.equals("minutes")) {
                return MINUTES;
            }
            if (s.equals("percentage")) {
                return PERCENTAGE;
            }

            throw new IllegalArgumentException("s=" + s);
        }
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

        @Override
        public BuildWrapper newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            if (formData.containsKey("buffer")) {
                return new CurfewDispatcherConfigurationBuildWrapper(
                        formData.getInt("startTime"),
                        formData.getInt("duration"),
                        formData.getJSONObject("buffer").getInt("bufferAmount"),
                        BufferType.fromString(
                                formData.getJSONObject("buffer").getString("bufferType"))
                );

            } else {
                // This should work, but doesn't compile:
                //return super.newInstance(req, formData);

                return new CurfewDispatcherConfigurationBuildWrapper(
                        formData.getInt("startTime"),
                        formData.getInt("duration"));
            }
        }
    }

}


