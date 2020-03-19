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
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.Cause.RemoteCause;
import hudson.model.Cause.UpstreamCause;
import hudson.model.Cause.UserIdCause;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;

import java.io.IOException;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import com.ibm.devops.connect.CloudPublisher;
import com.ibm.devops.connect.DevOpsGlobalConfiguration;

public class UploadDeployment extends Builder implements SimpleBuildStep {

    private String id;
    private String tenantId;
    private String name;
    private String result;
    private String initiator;
    private String versionName;
    private String versionExtId;
    private String type;
    private String environmentId;
    private String environmentName;
    private String description;
    private String startTime;
    private String endTime;
    private String appName;
    private String appId;
    private String appExtId;
    private Boolean debug;
    private Boolean fatal;

    @DataBoundConstructor
    public UploadDeployment(
        String id,
        String tenantId,
        String name,
        String result,
        String initiator,
        String versionName,
        String versionExtId,
        String type,
        String environmentId,
        String environmentName,
        String description,
        String startTime,
        String endTime,
        String appName,
        String appId,
        String appExtId,
        Boolean debug,
        Boolean fatal
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.result = result;
        this.initiator = initiator;
        this.versionName = versionName;
        this.versionExtId = versionExtId;
        this.type = type;
        this.environmentId = environmentId;
        this.environmentName = environmentName;
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.appName = appName;
        this.appId = appId;
        this.appExtId = appExtId;
        this.debug = debug;
        this.fatal = fatal;
    }

    public String getId() { return this.id; }
    public String getTenantId() { return this.tenantId; }
    public String getName() { return this.name; }
    public String getResult() { return this.result; }
    public String getInitiator() { return this.initiator; }
    public String getVersionName() { return this.versionName; }
    public String getVersionExtId() { return this.versionExtId; }
    public String getType() { return this.type; }
    public String getEnvironmentId() { return this.environmentId; }
    public String getEnvironmentName() { return this.environmentName; }
    public String getDescription() { return this.description; }
    public String getStartTime() { return this.startTime; }
    public String getEndTime() { return this.endTime; }
    public String getAppName() { return this.appName; }
    public String getAppId() { return this.appId; }
    public String getAppExtId() { return this.appExtId; }
    public Boolean getDebug() { return this.debug; }
    public Boolean getFatal() { return this.fatal; }

    @Override
    public void perform(final Run<?, ?> build, FilePath workspace, Launcher launcher, final TaskListener listener)
    throws AbortException, InterruptedException, IOException {
        if (!Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).isConfigured()) {
            listener.getLogger().println("Could not upload deployments to Velocity as there is no configuration specified.");
            return;
        }

        EnvVars envVars = build.getEnvironment(listener);

        String id = envVars.expand(this.id);
        String tenantId = envVars.expand(this.tenantId);
        String versionName = envVars.expand(this.versionName);
        String versionExtId = envVars.expand(this.versionExtId);
        String type = envVars.expand(this.type);
        String environmentId = envVars.expand(this.environmentId);
        String environmentName = envVars.expand(this.environmentName);
        String description = envVars.expand(this.description);
        String appId = envVars.expand(this.appId);
        String appName = envVars.expand(this.appName);
        String appExtId = envVars.expand(this.appExtId);
        String name = envVars.expand(this.name);
        String initiator = envVars.expand(this.initiator);
        String result = envVars.expand(this.result);
        String startTime = envVars.expand(this.startTime);
        String endTime = envVars.expand(this.endTime);
        String debug = envVars.expand(this.debug == null ? "" : this.debug.toString());
        String fatal = envVars.expand(this.fatal == null ? "" : this.fatal.toString());

        JSONObject payload = new JSONObject();

        // user-provided inputs
        payload.put("tenant_id", tenantId);
        if (id != null && !id.equals("")) {
            payload.put("id_external", id);
        } else {
            payload.put("id_external", versionExtId);
        }
        if (versionName != null && !versionName.equals("")) {
            payload.put("version_name", versionName);
        }
        if (versionExtId != null && !versionExtId.equals("")) {
            payload.put("version_id_external", versionExtId);
        }
        payload.put("type", type);
        payload.put("environment_id", environmentId);
        payload.put("environment_name", environmentName);
        if (description != null && !description.equals("")) {
            payload.put("description", description);
        }
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

        // user-provided inputs with fallbacks
        if (name != null && !name.equals("")) {
            payload.put("name", name);
        } else {
            payload.put("name", build.getParent().getName());
        }
        if (initiator != null && !initiator.equals("")) {
            payload.put("by_user", initiator);
        } else {
            for (Cause cause : build.getCauses()) {
                if (cause instanceof UserIdCause) {
                    UserIdCause userCause = (UserIdCause)cause;
                    payload.put("by_user", userCause.getUserName());
                } else if (cause instanceof UpstreamCause) {
                    UpstreamCause upstreamCause = (UpstreamCause)cause;
                    payload.put("by_user", "Upstream job \"" + upstreamCause.getUpstreamProject() + "\", build \"" + upstreamCause.getUpstreamBuild() + "\"");
                } else if (cause instanceof RemoteCause) {
                    RemoteCause remoteCause = (RemoteCause)cause;
                    payload.put("requestor", remoteCause.getAddr());
                } else {
                    payload.put("requestor", cause.getShortDescription());
                }
            }
        }
        if (result != null && !result.equals("")) {
            payload.put("result", result);
        } else {
            String computedStatus = "failure";
            Result buildResult = build.getResult();
            if (buildResult == null || buildResult.equals(Result.SUCCESS)) {
                computedStatus = "success";
            }
            payload.put("result", computedStatus);
        }
        if (startTime != null && !startTime.equals("")) {
            payload.put("start_time", Long.valueOf(startTime));
        } else {
            payload.put("start_time", build.getStartTimeInMillis());
        }
        if (endTime != null && !endTime.equals("")) {
            payload.put("end_time", Long.valueOf(endTime));
        } else {
            payload.put("end_time", System.currentTimeMillis());
        }


        System.out.println("TEST payload: " + payload.toString(2));

        if (debug.equals("true")) {
            listener.getLogger().println("payload: " + payload.toString());
        }

        listener.getLogger().println("Uploading deployment \"" + payload.get("version_name") + "\" of \"" + payload.get("name") + "\" to UrbanCode Velocity...");
        try {
            String response = CloudPublisher.uploadDeployment(payload.toString());
            System.out.println("TEST response: " + response);
            JSONObject json = JSONObject.fromObject(response);
            if (json.isEmpty() || !json.has("_id") || json.get("_id").equals("")) {
                throw new RuntimeException("Did not receive successful response: " + response);
            }
            listener.getLogger().println("Successfully uploaded deployment to UrbanCode Velocity.");
        } catch (Exception ex) {
            listener.error("Error uploading deployment data: " + ex.getClass() + " - " + ex.getMessage());
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
        }
    }

    @Extension
    public static class UploadDeploymentDescriptor extends BuildStepDescriptor<Builder> {

        public UploadDeploymentDescriptor() {
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
            return "UCV - Upload Deployment to UrbanCode Velocity";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
