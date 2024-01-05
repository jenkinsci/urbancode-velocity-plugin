package com.ibm.devops.connect.Status;

import hudson.model.Run;
import hudson.model.AbstractItem;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.model.Action;
import hudson.model.Describable;
import hudson.EnvVars;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.git.util.Build;
import hudson.tasks.BuildStep;
import hudson.FilePath;
import hudson.model.Result;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jenkins.model.Jenkins;
import org.jenkinsci.plugins.uniqueid.IdStore;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import com.ibm.devops.connect.DevOpsGlobalConfiguration;
import com.ibm.devops.connect.CloudCause.JobStatus;
import com.ibm.devops.connect.CloudCause;

import com.ibm.devops.dra.EvaluateGate;
import com.ibm.devops.dra.GatePublisherAction;

import net.sf.json.JSONObject;

import java.io.IOException;
import java.lang.InterruptedException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.security.MessageDigest;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Cipher;
import net.sf.json.*;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import org.apache.http.client.utils.URIBuilder;

public abstract class AbstractJenkinsStatus {
    public static final Logger log = LoggerFactory.getLogger(AbstractJenkinsStatus.class);
    // Run
    protected Run run;

    protected CloudCause cloudCause;

    protected BuildStep buildStep;
    protected FlowNode node;

    protected Boolean newStep;
    protected Boolean isFatal;

    protected TaskListener taskListener;

    protected EnvVars envVars;
    protected CrAction crAction;

    protected Boolean isPipeline;
    protected Boolean isPaused;
    protected Boolean isRunStatus;

    protected void getOrCreateCrAction() {

        if ( run != null) {
            List<Action> actions = run.getActions();
            for(Action action : actions) {
                if (action instanceof CrAction) {
                    crAction = (CrAction)action;
                }
            }

            // If not, create crAction
            if (crAction == null) {
                crAction = new CrAction();
                run.addAction(crAction);
            }
        }
    }

    protected void getEnvVars() {
        try {
            if( run != null && taskListener != null) {
                this.envVars = run.getEnvironment(taskListener);
                Set<String> keys = this.envVars.keySet();
                List<String> keysToRemove = new ArrayList<String>();
                Iterator<String> iterator = keys.iterator();
                while (iterator.hasNext()) {
                    String key = iterator.next();
                    if (key.contains(".")) {
                        keysToRemove.add(key);
                    }
                }
                for (String key : keysToRemove) {
                    this.envVars.remove(key);
                }
            }
        } catch (IOException ioEx) {
            log.warn("IOException thrown while trying to retrieve EnvVars in constructor: " + ioEx);
        } catch (InterruptedException intEx) {
            log.warn("InterruptedException thrown while trying to retrieve EnvVars in constructor: " + intEx);
        }
    }

    public JSONObject generateErrorStatus(String errorMessage) {
        JSONObject result = new JSONObject();

        cloudCause.addStep("Error: " + errorMessage, JobStatus.failure.toString(), "Failed due to error", true);

        result.put("status", JobStatus.failure.toString());
        result.put("timestamp", System.currentTimeMillis());
        result.put("syncId", Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncId());
        result.put("steps", cloudCause.getStepsArray());
        result.put("returnProps", cloudCause.getReturnProps());

        if(run != null) {
            result.put("url", Jenkins.getInstance().getRootUrl() + run.getUrl());
            result.put("jobExternalId", getJobUniqueIdFromBuild());
            result.put("name", run.getDisplayName());
            result.put("startTime", run.getStartTimeInMillis());
        } else {
            result.put("url", Jenkins.getInstance().getRootUrl());
            result.put("name", "Job Error");
        }

        return result;
    }

    private String getJobUniqueIdFromBuild() {
        AbstractItem project = run.getParent();

        String projectId;

        if (IdStore.getId(project) != null) {
            projectId = IdStore.getId(project);
        } else {
            IdStore.makeId(project);
            projectId = IdStore.getId(project);
        }

        return projectId;
    }

    protected void evaluateSourceData() {
        List<Action> actions = run.getActions();

        // Try to get from the crAction
        SourceData sd = crAction.getSourceData();
        if (sd != null) {
            cloudCause.setSourceData(sd);
        }

        if (envVars != null) {
            for(Action action : actions) {
                // If using Hudson Git Plugin
                if (action instanceof BuildData) {
                    Map<String,Build> branchMap = ((BuildData)action).getBuildsByBranchName();

                    for(Map.Entry<String, Build> branchEntry : branchMap.entrySet()) {
                        Build gitBuild = branchEntry.getValue();

                        if (gitBuild.getBuildNumber() == run.getNumber()) {
                            SourceData sourceData = new SourceData(branchEntry.getKey(), gitBuild.getSHA1().getName(), "GIT");
                            sourceData.populateCommitMessage(taskListener, envVars, getWorkspaceFilePath(), gitBuild);

                            cloudCause.setSourceData(sourceData);
                            crAction.setSourceData(sourceData);
                        }
                    }
                }
            }
        }
    }

    protected void evaluateDRAData() {
        DRAData data = cloudCause.getDRAData();

        List<Action> actions = run.getActions();
        if (data == null) {
            data = crAction.getDRAData();
            cloudCause.setDRAData(data);
        }

        if (data == null) {
            data = new DRAData();
        }

        if (Jenkins.getInstance().getPlugin("ibm-cloud-devops") != null) {

            //This block if for non-pipeline jobs to set additional data that we have access to
            if (this.buildStep != null && this.buildStep instanceof EvaluateGate) {

                EvaluateGate egs = (EvaluateGate)buildStep;

                String environment = egs.getEnvName();
                String applicationName = egs.getApplicationName();
                String orgName = egs.getOrgName();
                String toolchainName = egs.getToolchainName();

                data.setApplicationName(applicationName);
                data.setOrgName(orgName);
                data.setToolchainName(toolchainName);
                data.setEnvironment(environment);
            }

            for(Action action : actions) {
                if (action instanceof GatePublisherAction) {
                    GatePublisherAction gpa = (GatePublisherAction)action;

                    String gateText = gpa.getText();
                    String riskDashboardLink = gpa.getRiskDashboardLink();
                    String decision = gpa.getDecision();
                    String policy = gpa.getPolicyName();

                    data.setGateText(gateText);
                    data.setDecision(decision);
                    data.setRiskDahboardLink(riskDashboardLink);
                    data.setPolicy(policy);
                    data.setBuildNumber(Integer.toString(run.getNumber()));

                    crAction.setDRAData(data);
                    cloudCause.setDRAData(data);
                }
            }
        }
    }

    private void evaluateEnvironment() {
        if( envVars != null ) {
            crAction.updateEnvProperties(envVars);
        }
    }

    public JSONObject generate(boolean completed) {
        JSONObject result = new JSONObject();

        evaluateSourceData();
        evaluateDRAData();
        evaluateEnvironment();

        if(isPipeline) {
            evaluatePipelineStep();
        } else {
            evaluateBuildStep();
        }

        String status = null;
        if (run.getResult() == null) {
            status = run.isBuilding() ? JobStatus.started.toString() : JobStatus.unstarted.toString();
        } else if (run.getResult() != Result.SUCCESS) {
            status = JobStatus.failure.toString();
        } else { // status is success
            status = completed ? JobStatus.success.toString() : JobStatus.started.toString();
        }

        result.put("status", status);
        result.put("timestamp", System.currentTimeMillis());
        result.put("startTime", run.getStartTimeInMillis());
        result.put("syncId", Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncId());
        result.put("name", run.getDisplayName());
        result.put("steps", cloudCause.getStepsArray());
        result.put("url", Jenkins.getInstance().getRootUrl() + run.getUrl());
        result.put("returnProps", cloudCause.getReturnProps());
        result.put("isPipeline", isPipeline);
        result.put("isPaused", isPaused);
        result.put("isRunStatus", isRunStatus);
        result.put("jobName", run.getParent().getName());
        result.put("jobExternalId", getJobUniqueIdFromBuild());
        result.put("sourceData", cloudCause.getSourceDataJson());
        result.put("draData", cloudCause.getDRADataJson());
        result.put("crProperties", crAction.getCrProperties());
        result.put("envProperties", crAction.getEnvProperties());
        StandardUsernamePasswordCredentials credentials = Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getCredentialsObj();          
        String plainCredentials = credentials.getUsername() + ":" + credentials.getPassword().getPlainText();
        String encodedString = getEncodedString(plainCredentials);
        String authorizationHeader = "Basic " + encodedString;
        String baseUrl = Jenkins.getInstance().getRootUrl() + run.getUrl();
        String path = "api/json";
        String finalUrl = null;
        String apiResponse = null;
        String requestor = null;
        try {
            URIBuilder builder = new URIBuilder(baseUrl);
            builder.setPath(builder.getPath()+path); 
            finalUrl = builder.toString();
        } catch (Exception e) {
            log.error("Caught error while building url to get requestor name: ", e);
        }
        try {
            HttpResponse<String> response = Unirest.get(finalUrl)
                .header("Authorization", authorizationHeader)
                .asString();
            apiResponse = response.getBody().toString();
            JSONArray apiResponseArray = JSONArray.fromObject("[" + apiResponse + "]");
            JSONObject apiResponseObject = apiResponseArray.getJSONObject(0);
            if(apiResponseObject.has("actions")){
                JSONArray actionsArray = JSONArray.fromObject(apiResponseObject.getString("actions"));
                for(int i=0;i<actionsArray.size();i++){
                    JSONObject actionsObject = actionsArray.getJSONObject(i);
                    if(actionsObject.has("causes")){
                        JSONArray causesArray = JSONArray.fromObject(actionsObject.getString("causes"));
                        JSONObject causesObject = causesArray.getJSONObject(0);
                        if(causesObject.has("userName")){
                            requestor = causesObject.getString("userName");
                            result.put("requestor", requestor);
                        }else if(causesObject.has("shortDescription")){
                            requestor = causesObject.getString("shortDescription");
                            result.put("requestor", requestor);
                        }
                    }
                }
            }
        } catch (UnirestException e) {
            log.warn("UnirestException: Failed to get details of requestor");
        }
        // log.info(result.toString());
        return result;
    }

	public void setRunStatus(Boolean isRunStatus) {
		this.isRunStatus = isRunStatus;
	}

	abstract protected FilePath getWorkspaceFilePath();

    abstract protected void evaluatePipelineStep();

    abstract protected void evaluateBuildStep();

    private static byte[] toByte(String hexString) {
        int len = hexString.length()/2;
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++) {
            result[i] = Integer.valueOf(hexString.substring(2*i, 2*i+2), 16).byteValue();
        }
        return result;
    }

    private static String decrypt(String seed, String encrypted) throws Exception {
        byte[] keyb = seed.getBytes("UTF-8");
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] thedigest = md.digest(keyb);
        SecretKeySpec skey = new SecretKeySpec(thedigest, "AES");
        Cipher dcipher = Cipher.getInstance("AES");
        dcipher.init(Cipher.DECRYPT_MODE, skey);
        byte[] clearbyte = dcipher.doFinal(toByte(encrypted));
        return new String(clearbyte, "UTF-8");
    }

    private static String getEncodedString(String credentials){  
        return Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));   
    }
}