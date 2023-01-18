package com.ibm.devops.connect;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import hudson.util.FormValidation;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.util.List;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

public final class Entry implements Describable<Entry> {

    /**
     * Destination bucket for the copy. Can contain macros.
     */
    private String syncId;
    private String syncToken;
    private String baseUrl;
    private String rabbitMQPort;
    private String rabbitMQHost;
    private String apiToken;
    private Boolean checkDuplicate;

    @DataBoundConstructor
    public Entry(String syncId, String syncToken, String baseUrl, String rabbitMQPort, String rabbitMQHost,
            String apiToken, Boolean checkDuplicate) {
        this.syncId = syncId;
        this.syncToken = syncToken;
        this.baseUrl = baseUrl;
        this.rabbitMQPort = rabbitMQPort;
        this.rabbitMQHost = rabbitMQHost;
        this.apiToken = apiToken;
        this.checkDuplicate = checkDuplicate;
    }

    public Entry(Object object) {
    }

    public String getSyncId() {
        return syncId;
    }

    public String getSyncToken() {
        return syncToken;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getRabbitMQPort() {
        return rabbitMQPort;
    }

    public String getRabbitMQHost() {
        return rabbitMQHost;
    }

    public String getApiToken() {
        return apiToken;
    }

    public Boolean getCheckDuplicate() {
        return checkDuplicate;
    }

    public boolean isConfigured() {
        return StringUtils.isNotEmpty(this.syncId) &&
                StringUtils.isNotEmpty(this.syncToken) &&
                StringUtils.isNotEmpty(this.baseUrl) &&
                StringUtils.isNotEmpty(this.apiToken);
    }

    @Override
    public Descriptor<Entry> getDescriptor() {
        return DESCRIPOR;
    }

    @Extension
    public static final DescriptorImpl DESCRIPOR = new DescriptorImpl();

    public static class DescriptorImpl extends Descriptor<Entry> {
        @Override
        public String getDisplayName() {
            return "Connect to UrbanCode Velocity Instance";
        }

        @SuppressWarnings("unused")
        @RequirePOST
        public FormValidation doTestConnections(@QueryParameter("syncId") String syncId,
                @QueryParameter("syncToken") String syncToken,
                @QueryParameter("baseUrl") String baseUrl,
                @QueryParameter("apiToken") String apiToken) {
            try {
                String connected = CloudPublisher.testConnection(syncId, syncToken, baseUrl, apiToken);
                List<Entry> entries = Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class)
                        .getEntries();
                Entry finalEntry = null;
                for (Entry entry : entries) {
                    if (baseUrl.equals(entry.getBaseUrl()) && syncId.equals(entry.getSyncId())) {
                        finalEntry = entry;
                    }
                }
                if (connected.equals("successfull connection")) {
                    boolean amqpConnected = ConnectComputerListener.isRabbitConnected(finalEntry);

                    String rabbitMessage = "Not connected to RabbitMQ. Unable to run Jenkins jobs from UCV.\n WARNING:Check the port number correctly.\n If you use Kubernetes, the default port that is exposed is 31672."
                            + finalEntry.getBaseUrl();
                    if (amqpConnected && connected.equals("successfull connection")) {
                        if (StringUtils.isNotEmpty(finalEntry.getRabbitMQHost())) {
                            rabbitMessage = "Connected to RabbitMQ ( " + finalEntry.getRabbitMQHost()
                                    + " ) successfully. Ready to run Jenkins jobs from " + finalEntry.getBaseUrl();
                        } else {
                            rabbitMessage = "Connected to RabbitMQ successfully. Using default 'localhost' for RabbitMQ host. Ready to run Jenkins jobs from "
                                    + finalEntry.getBaseUrl();
                        }
                    }

                    return FormValidation.ok("Successful connection to Velocity Services.\n" + rabbitMessage);
                } else {
                    return FormValidation.error(connected);
                }
            } catch (Exception e) {
                if (e.getMessage().contains("Index") && e.getMessage().contains("out of bounds for length")) {
                    return FormValidation.error("Could not connect to Velocity : " + e.getMessage()
                            + "\nPlease try to Apply/Save Configration and Restart Jenkins");
                } else {
                    return FormValidation.error("Could not connect to Velocity : " + e.getMessage());
                }
            }
        }

        public FormValidation doCheckSyncId(@QueryParameter("syncId") String syncId) {
            int count = 0;
            List<Entry> entries = Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class)
                    .getEntries();
            for (Entry entry : entries) {
                if (entry.getSyncId().equals(syncId)) {
                    count = count + 1;
                }
            }
            if (count > 1) {
                return FormValidation.error("Duplicates not Allowed.");
            }
            return FormValidation.validateRequired(syncId);
        }

        public FormValidation doCheckSyncToken(@QueryParameter("syncToken") String syncToken) {
            List<Entry> entries = Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class)
                    .getEntries();
            int count = 0;
            for (Entry entry : entries) {
                if (entry.getSyncToken().equals(syncToken)) {
                    count = count + 1;
                }
            }
            if (count > 1) {
                return FormValidation.error("Duplicates not Allowed.");
            }
            return FormValidation.validateRequired(syncToken);
        }

        public FormValidation doCheckBaseUrl(@QueryParameter("baseUrl") String baseUrl) {
            List<Entry> entries = Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class)
                    .getEntries();
            int count = 0;
            for (Entry entry : entries) {
                if (entry.getBaseUrl().equals(baseUrl)) {
                    count = count + 1;
                }
            }
            if (count > 1) {
                return FormValidation.error("Duplicates not Allowed.");
            }
            return FormValidation.validateRequired(baseUrl);
        }

        public FormValidation doCheckApiToken(@QueryParameter("apiToken") String apiToken) {
            List<Entry> entries = Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class)
                    .getEntries();
            int count = 0;
            for (Entry entry : entries) {
                if (entry.getApiToken().equals(apiToken)) {
                    count = count + 1;
                }
            }
            if (count > 1) {
                return FormValidation.error("Duplicates not Allowed.");
            }
            return FormValidation.validateRequired(apiToken);
        }

        public FormValidation doCheckRabbitMQHost(@QueryParameter("rabbitMQHost") String rabbitMQHost) {
            List<Entry> entries = Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class)
                    .getEntries();
            int count = 0;
            for (Entry entry : entries) {
                if (entry.getRabbitMQHost().equals(rabbitMQHost)) {
                    count = count + 1;
                }
            }
            if (count > 1) {
                return FormValidation.error("Duplicates not Allowed.");
            }
            return FormValidation.ok();
        }
    }
}
