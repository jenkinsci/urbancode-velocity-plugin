/**
 * (c) Copyright IBM Corporation 2018.
 * This is licensed under the following license.
 * The Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */

package com.ibm.devops.connect.CRPipeline;

import hudson.AbortException;
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

import java.util.Map;

import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;

import com.ibm.devops.connect.CloudPublisher;
import com.ibm.devops.connect.DevOpsGlobalConfiguration;

public class UploadJUnitTestResult extends Builder implements SimpleBuildStep {

    private Map<String, String> properties;

    @DataBoundConstructor
    public UploadJUnitTestResult(Map<String, String> properties) {
        this.properties = properties;
    }

    public Map<String, String> getProperties() {
        return this.properties;
    }

    @Override
    public void perform(final Run<?, ?> build, FilePath workspace, Launcher launcher, final TaskListener listener)
    throws AbortException, InterruptedException, IOException {
        if (!Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).isConfigured()) {
            listener.getLogger().println("Could not upload junit tests to Velocity as there is no configuration specified.");
            return;
        }

        Object fatalFailure = this.properties.get("fatal");
        String buildUrl = Jenkins.getInstance().getRootUrl() + build.getUrl();
        String userAccessKey = Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getApiToken();

        boolean success = workspace.act(new FileUploader(this.properties, listener, buildUrl, CloudPublisher.getQualityDataUrl(), userAccessKey));
        if (!success) {
            if (fatalFailure != null && fatalFailure.toString().equals("true")) {
                build.setResult(Result.FAILURE);
            } else {
                build.setResult(Result.UNSTABLE);
            }
        }
    }

    @Extension
    public static class UploadJUnitTestResultDescriptor extends BuildStepDescriptor<Builder> {

        public UploadJUnitTestResultDescriptor() {
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
            return "UCV - Upload JUnit Results to UrbanCode Velocity";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }

    private static final class FileUploader implements FileCallable<Boolean> {
        private Map<String, String> properties;
        private TaskListener listener;
        private String buildUrl;
        private String postUrl;
        private String userAccessKey;

        public FileUploader(Map<String, String> properties, TaskListener listener, String buildUrl, String postUrl, String userAccessKey) {
            this.properties = properties;
            this.listener = listener;
            this.buildUrl = buildUrl;
            this.postUrl = postUrl;
            this.userAccessKey = userAccessKey;
        }

        @Override public Boolean invoke(File f, VirtualChannel channel) {
            listener.getLogger().println("Uploading JUnit File");

            String filePath = properties.get("filePath");
            String tenantId = properties.get("tenant_id");
            String name = properties.get("name");
            String testSetName = properties.get("testSetName");
            String appId = properties.get("appId");
            String appExtId = properties.get("appExtId");
            String appName = properties.get("appName");
            String environment = properties.get("environment");
            Object combineTestSuites = properties.get("combineTestSuites");
            String metricDefinitionId = properties.get("metricDefinitionId");
            String buildId = properties.get("buildId");

            JSONObject payload = new JSONObject();
            JSONObject application = new JSONObject();
            JSONObject record = new JSONObject();
            JSONObject options = new JSONObject();

            application.put("id", appId);
            application.put("name", appName);
            application.put("externalId", appExtId);

            record.put("pluginType", "junitXML");
            record.put("dataFormat", "xml");
            record.put("recordName", name);
            record.put("metricDefinitionId", metricDefinitionId);

            if (combineTestSuites != null) {
                options.put("combineTestSuites", combineTestSuites.toString());
            }

            payload.put("dataSet", testSetName);
            payload.put("environment", environment);
            payload.put("tenantId", tenantId);

            payload.put("application", application);
            payload.put("record", record);
            payload.put("options", options);

            JSONObject build = new JSONObject();
            if (buildId != null) {
                build.put("id", buildId);
            }
            build.put("url", this.buildUrl);
            payload.put("build", build);

            System.out.println("TEST payload: " + payload.toString(2));

            HttpEntity entity = MultipartEntityBuilder
                .create()
                .addTextBody("payload", payload.toString())
                .addBinaryBody("file", new File(f, filePath), ContentType.create("application/octet-stream"), "filename")
                .build();

            boolean success = false;
            try {
                success = CloudPublisher.uploadQualityData(entity, postUrl, userAccessKey);
            } catch (Exception ex) {
                listener.error("Error uploading quality data: " + ex.getClass() + " - " + ex.getMessage());
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
