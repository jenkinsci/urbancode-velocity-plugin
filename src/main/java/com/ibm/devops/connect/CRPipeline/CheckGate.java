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
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;

import java.io.IOException;
import java.util.Iterator;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import com.ibm.devops.connect.CloudPublisher;
import com.ibm.devops.connect.DevOpsGlobalConfiguration;
import com.ibm.devops.connect.Endpoints.EndpointManager;
import com.ibm.devops.connect.Entry;
import java.util.List;
import org.apache.commons.lang.StringUtils;

public class CheckGate extends Builder implements SimpleBuildStep {

    private String pipelineId;
    private String stageName;
    private String versionId;
    private String fatal;

    @DataBoundConstructor
    public CheckGate(
        String pipelineId,
        String stageName,
        String versionId,
        String fatal
    ) {
        this.pipelineId = pipelineId;
        this.stageName = stageName;
        this.versionId = versionId;
        this.fatal = fatal;
    }

    public String getPipelineId() { return this.pipelineId; }
    public String getStageName() { return this.stageName; }
    public String getVersionId() { return this.versionId; }
    public String getFatal() { return this.fatal; }

    private static String getPipelinesUrl(int instanceNum) {
        EndpointManager em = new EndpointManager();
        return em.getPipelinesEndpoint(instanceNum);
    }

    @Override
    public void perform(final Run<?, ?> build, FilePath workspace, Launcher launcher, final TaskListener listener)
    throws AbortException, InterruptedException, IOException, RuntimeException {
        EnvVars envVars = build.getEnvironment(listener);

        String pipelineId = envVars.expand(this.pipelineId);
        String stageName = envVars.expand(this.stageName);
        String versionId = envVars.expand(this.versionId);
        String fatal = envVars.expand(this.fatal);

        List<Entry> entries = Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getEntries();
        int instanceNum = -1;
        int count = 0;
        for (Entry entry : entries) {
            try {
                if(CloudPublisher.isPipeline(count, pipelineId)){
                    instanceNum = count;
                    break;
                }  
            } catch (Exception e) {
                listener.getLogger().println("Count: " + count);
                listener.error("Error checking validity of pipelineId: " + e.getClass() + " - " + e.getMessage());
                throw new RuntimeException("Not able to validate pipelineId"); 
            }
            count = count + 1;
        }
        if(instanceNum != -1){
            listener.getLogger().println("Check gates on pipeline: " + CheckGate.getPipelinesUrl(instanceNum) + pipelineId);
            listener.getLogger().println("Checking gate on stage \"" + stageName + "\" for version \"" + versionId + "\" in UrbanCode Velocity (" + entries.get(instanceNum).getBaseUrl() + ").");
            Boolean throwException = false;
            try {
                String result = CloudPublisher.checkGate(instanceNum, pipelineId, stageName, versionId);
                JSONObject resultObj = JSONObject.fromObject(result);
                if (resultObj.has("errors")) {
                    throw new RuntimeException(resultObj.get("errors").toString());
                }
                Iterator<?> keys = resultObj.keys();
                Boolean anyGateFailed = false;
                while(keys.hasNext()) {
                    String key = keys.next().toString();
                    String value = resultObj.get(key).toString();
                    if (value.equals("true")) {
                        listener.getLogger().println("Gate \"" + key + "\" passed (" + entries.get(instanceNum).getBaseUrl() + ").");
                    } else if (value.equals("false")) {
                        listener.getLogger().println("Gate \"" + key + "\" failed (" + entries.get(instanceNum).getBaseUrl() + ").");
                        anyGateFailed = true;
                    }
                }
                if (anyGateFailed) {
                    if (fatal != null && fatal.equals("true")) {
                        throwException = true;
                    }
                    build.setResult(Result.FAILURE);
                } else {
                    listener.getLogger().println("No gate failures, gates pass (" + entries.get(instanceNum).getBaseUrl() + ").");
                }
            } catch (Exception ex) {
                listener.error("Error checking gate (" + entries.get(instanceNum).getBaseUrl() + "): " + ex.getClass() + " - " + ex.getMessage());
                listener.error("Stack trace (" + entries.get(instanceNum).getBaseUrl() + "): ");
                StackTraceElement[] elements = ex.getStackTrace();
                for (int i = 0; i < elements.length; i++) {
                    StackTraceElement s = elements[i];
                    listener.error("\tat " + s.getClassName() + "." + s.getMethodName() + "(" + s.getFileName() + ":" + s.getLineNumber() + ")");
                }
                build.setResult(Result.FAILURE);
            }
            if (throwException) {
                throw new RuntimeException("Gate failure and fatal set to \"true\", exception thrown to stop build (" + entries.get(instanceNum).getBaseUrl() + ").");
            }
        }else{
            throw new RuntimeException("Invalid pipelineId");
        }
    }

    @Extension
    public static class CheckGateDescriptor extends BuildStepDescriptor<Builder> {

        public CheckGateDescriptor() {
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
            return "UCV - Check Gate in UrbanCode Velocity";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
