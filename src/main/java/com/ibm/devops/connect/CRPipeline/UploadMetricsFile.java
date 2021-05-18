/**
 * (c) Copyright IBM Corporation 2018.
 * This is licensed under the following license.
 * The Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */

package com.ibm.devops.connect.CRPipeline;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.remoting.VirtualChannel;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;

import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;

import java.io.File;
import java.io.IOException;
import net.sf.json.JSONObject;

import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;

import com.ibm.devops.connect.CloudPublisher;
import com.ibm.devops.connect.DevOpsGlobalConfiguration;

public class UploadMetricsFile extends Builder implements SimpleBuildStep {

    private String tenantId;
    private String name;
    private String filePath;
    private String testSetName;
    private String environment;
    private Boolean combineTestSuites;
    private Boolean fatal;
    private Boolean debug;
    private String pluginType;
    private String dataFormat;
    private String recordName;
    private String metricDefinitionId;
    private String metricsRecordUrl;
    private String description;
    private String executionDate;
    private String buildId;
    private String buildUrl;
    private String appId;
    private String appName;
    private String appExtId;

    @DataBoundConstructor
    public UploadMetricsFile(
        String tenantId,
        String name,
        String filePath,
        String testSetName,
        String environment,
        Boolean combineTestSuites,
        Boolean fatal,
        Boolean debug,
        String pluginType,
        String dataFormat,
        String recordName,
        String metricDefinitionId,
        String metricsRecordUrl,
        String description,
        String executionDate,
        String buildId,
        String buildUrl,
        String appId,
        String appName,
        String appExtId
    ) {
        this.tenantId = tenantId;
        this.name = name;
        this.filePath = filePath;
        this.testSetName = testSetName;
        this.environment = environment;
        this.combineTestSuites = combineTestSuites;
        this.fatal = fatal;
        this.debug = debug;
        this.pluginType = pluginType;
        this.dataFormat = dataFormat;
        this.recordName = recordName;
        this.metricDefinitionId = metricDefinitionId;
        this.metricsRecordUrl = metricsRecordUrl;
        this.description = description;
        this.executionDate = executionDate;
        this.buildId = buildId;
        this.buildUrl = buildUrl;
        this.appId = appId;
        this.appName = appName;
        this.appExtId = appExtId;
    }

    public String getTenantId() { return this.tenantId; }
    public String getName() { return this.name; }
    public String getFilePath() { return this.filePath; }
    public String getTestSetName() { return this.testSetName; }
    public String getEnvironment() { return this.environment; }
    public Boolean getCombineTestSuites() { return this.combineTestSuites; }
    public Boolean getFatal() { return this.fatal; }
    public Boolean getDebug() { return this.debug; }
    public String getPluginType() { return this.pluginType; }
    public String getDataFormat() { return this.dataFormat; }
    public String getRecordName() { return this.recordName; }
    public String getMetricDefinitionId() { return this.metricDefinitionId; }
    public String getMetricsRecordUrl() { return this.metricsRecordUrl; }
    public String getDescription() { return this.description; }
    public String getExecutionDate() { return this.executionDate; }
    public String getBuildId() { return this.buildId; }
    public String getBuildUrl() { return this.buildUrl; }
    public String getAppId() { return this.appId; }
    public String getAppName() { return this.appName; }
    public String getAppExtId() { return this.appExtId; }

    @Override
    public void perform(final Run<?, ?> build, FilePath workspace, Launcher launcher, final TaskListener listener)
    throws AbortException, InterruptedException, IOException {
        if (!Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).isConfigured()) {
            listener.getLogger().println("Could not metrics file to Velocity as there is no configuration specified.");
            return;
        }

        EnvVars envVars = build.getEnvironment(listener);

        String testSetName = envVars.expand(this.testSetName);
        String filePath = envVars.expand(this.filePath);
        String environment = envVars.expand(this.environment);
        String tenantId = envVars.expand(this.tenantId);
        String appId = envVars.expand(this.appId);
        String appName = envVars.expand(this.appName);
        String appExtId = envVars.expand(this.appExtId);
        String pluginType = envVars.expand(this.pluginType);
        String dataFormat = envVars.expand(this.dataFormat);
        String name = envVars.expand(this.name);
        String metricDefinitionId = envVars.expand(this.metricDefinitionId);
        String metricsRecordUrl = envVars.expand(this.metricsRecordUrl);
        String description = envVars.expand(this.description);
        String combineTestSuites = envVars.expand(this.combineTestSuites == null ? "" : this.combineTestSuites.toString());
        String fatal = envVars.expand(this.fatal == null ? "" : this.fatal.toString());
        String debug = envVars.expand(this.debug == null ? "" : this.debug.toString());
        String executionDate = envVars.expand(this.executionDate);
        String buildId = envVars.expand(this.buildId);
        String buildUrl = envVars.expand(this.buildUrl);

        JSONObject payload = new JSONObject();

        payload.put("dataSet", testSetName);
        if (environment != null && !environment.equals("")) {
            payload.put("environment", environment);
        }
        payload.put("tenantId", tenantId);

        JSONObject application = new JSONObject();
        if (appId != null && !appId.equals("")) {
            application.put("id", appId);
        }
        if (appName != null && !appName.equals("")) {
            application.put("name", appName);
        }
        if (appExtId != null && !appExtId.equals("")) {
            application.put("externalId", appExtId);
        }
        if (application.isEmpty()) {
            throw new RuntimeException("Must specify at least one of: 'appId', 'appName', 'appExtId'");
        }
        payload.put("application", application);

        JSONObject record = new JSONObject();
        record.put("pluginType", pluginType);
        record.put("dataFormat", dataFormat);
        if (name != null && !name.equals("")) {
            record.put("recordName", name);
        }
        if (metricDefinitionId != null && !metricDefinitionId.equals("")) {
            record.put("metricDefinitionId", metricDefinitionId);
        }
        if (metricsRecordUrl != null && !metricsRecordUrl.equals("")) {
            record.put("metricsRecordUrl", metricsRecordUrl);
        }
        if (description != null && !description.equals("")) {
            record.put("description", description);
        }
        if (executionDate != null && !executionDate.equals("")) {
            record.put("executionDate", executionDate);
        }
        payload.put("record", record);

        JSONObject options = new JSONObject();
        options.put("combineTestSuites", combineTestSuites != null && !combineTestSuites.equals("") ? combineTestSuites.toString() : "true");
        payload.put("options", options);

        JSONObject buildObj = new JSONObject();
        if (buildId != null && !buildId.equals("")) {
            buildObj.put("buildId", buildId);
        }
        if (buildUrl != null && !buildUrl.equals("")) {
            buildObj.put("url", buildUrl);
        } else {
            buildObj.put("url", Jenkins.getInstance().getRootUrl() + build.getUrl());
        }
        payload.put("build", buildObj);

        if (debug.equals("true")) {
            listener.getLogger().println("payload: " + payload.toString());
        }
        System.out.println("TEST payload: " + payload.toString(2));

        listener.getLogger().println("Uploading metric \"" + name + "\" to UrbanCode Velocity...");

        String userAccessKey = Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getApiToken();

        boolean success = workspace.act(new FileUploader(filePath, payload.toString(), listener, CloudPublisher.getQualityDataUrl(), userAccessKey));
        if (!success) {
            listener.getLogger().println("Problem uploading metrics file to UrbanCode Velocity.");
            if (fatal.equals("true")) {
                if (debug.equals("true")) {
                    listener.getLogger().println("Failing build due to fatal=true.");
                }
                build.setResult(Result.FAILURE);
            } else if (fatal.equals("false")) {
                if (debug.equals("true")) {
                    listener.getLogger().println("Not changing build result due to fatal=false.");
                }
            } else {
                if (debug.equals("true")) {
                    listener.getLogger().println("Marking build as unstable due to fatal flag not set.");
                }
                build.setResult(Result.UNSTABLE);
            }
        } else {
            listener.getLogger().println("Successfully uploaded metric file to UrbanCode Velocity.");
        }
    }

    @Extension
    public static class UploadMetricsFileDescriptor extends BuildStepDescriptor<Builder> {

        public UploadMetricsFileDescriptor() {
            load();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            req.bindJSON(this, formData);
            save();
            return super.configure(req, formData);
        }

        /**
         * {@inheritDoc}
         *
         * @return {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "UCV - Upload Metrics File to UrbanCode Velocity";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }

    private static final class FileUploader implements FileCallable<Boolean> {
        private static final long serialVersionUID = 1L;
        private String filePath;
        private String payload;
        private String postUrl;
        private String userAccessKey;
        private TaskListener listener;

        public FileUploader(String filePath, String payload, TaskListener listener, String postUrl, String userAccessKey) {
            this.filePath = filePath;
            this.payload = payload;
            this.listener = listener;
            this.postUrl = postUrl;
            this.userAccessKey = userAccessKey;
        }

        @Override public Boolean invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {

            File file = new File(this.filePath);
            if (!file.isAbsolute()) {
                file = new File(f, this.filePath);
            }
            if (!file.exists()) {
                throw new RuntimeException("File " + file.getAbsolutePath() + " does not exist");
            }

            HttpEntity entity = MultipartEntityBuilder
                .create()
                .addTextBody("payload", this.payload)
                .addBinaryBody("file", file, ContentType.create("application/octet-stream"), "filename")
                .build();

            boolean success = false;
            try {
                success = CloudPublisher.uploadQualityData(entity, postUrl, userAccessKey);
            } catch (Exception ex) {
                listener.error("Error uploading metric file: " + ex.getClass() + " - " + ex.getMessage());
                listener.error("Stack trace:");
                StackTraceElement[] elements = ex.getStackTrace();
                for (int i = 0; i < elements.length; i++) {
                    StackTraceElement s = elements[i];
                    listener.error("\tat " + s.getClassName() + "." + s.getMethodName() + "(" + s.getFileName() + ":" + s.getLineNumber() + ")");
                }
            }
            return success;
        }
        /**
         * Check the role of the executing node to follow jenkins new file access rules
         */
        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            // no-op
        }
    }
}
