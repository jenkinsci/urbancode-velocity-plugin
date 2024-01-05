/*******************************************************************************
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2017. All Rights Reserved.
 *
 * Note to U.S. Government Users Restricted Rights:  Use,
 * duplication or disclosure restricted by GSA ADP Schedule
 * Contract with IBM Corp.
 *******************************************************************************/
package com.ibm.devops.connect;

import java.util.concurrent.TimeUnit;

// import org.json.JSONArray;
// import org.json.JSONException;
// import org.json.JSONObject;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ibm.cloud.urbancode.connect.client.ConnectSocket;

import net.sf.json.*;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import hudson.model.AbstractProject;
import hudson.model.AbstractItem;
import hudson.model.Action;
import hudson.model.ParametersAction;
import hudson.model.CauseAction;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import hudson.model.BooleanParameterValue;
import hudson.model.TextParameterValue;
import hudson.model.PasswordParameterValue;
import hudson.model.queue.QueueTaskFuture;
import hudson.model.Queue;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.JobProperty;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.lang.InterruptedException;
import java.nio.charset.StandardCharsets;

import org.jenkinsci.plugins.workflow.job.WorkflowJob;

import net.sf.json.JSONObject;

import com.ibm.devops.connect.CloudCause.JobStatus;
import com.ibm.devops.connect.SecuredActions.TriggerJob.TriggerJobParamObj;
import com.ibm.devops.connect.SecuredActions.TriggerJob;

import com.ibm.devops.connect.Status.JenkinsJobStatus;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

import java.security.MessageDigest;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Cipher;

import org.acegisecurity.userdetails.UsernameNotFoundException;
import com.ibm.devops.connect.SecuredActions.AbstractSecuredAction;

/*
 * When Spring is applying the @Transactional annotation, it creates a proxy class which wraps your class.
 * So when your bean is created in your application context, you are getting an object that is not of type
 * WorkListener but some proxy class that implements the IWorkListener interface. So anywhere you want WorkListener
 * injected, you must use IWorkListener.
 */
public class CloudWorkListener2 {
	public static final Logger log = LoggerFactory.getLogger(CloudWorkListener2.class);
    private String logPrefix= "[UrbanCode Velocity] CloudWorkListener2#";

    public CloudWorkListener2() {

    }

    public enum WorkStatus {
        success, failed, started
    }

    /* (non-Javadoc)
     * @see com.ibm.cloud.urbancode.sync.IWorkListener#call(com.ibm.cloud.urbancode.connect.client.ConnectSocket, java.lang.String, java.lang.Object)
     */
    public void call(String event, Object... args) {
        TriggerJob triggerJob = new TriggerJob();

        TriggerJobParamObj paramObj = triggerJob.new TriggerJobParamObj(null, event, args);
        triggerJob.runAsJenkinsUser(paramObj);
    }
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

    private static String getEncodedString (String credentials){  
        return Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));   
    }

    private Boolean isDuplicateJob (JSONObject incomingJob) {
        String workId = incomingJob.getString("id");
        String jobName = incomingJob.getString("fullName");
        log.info(logPrefix + "Checking for duplicate jobs for JOB Name: " + jobName);
        StandardUsernamePasswordCredentials credentials = Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getCredentialsObj();          
        String plainCredentials = credentials.getUsername() + ":" + credentials.getPassword().getPlainText();
        String encodedString = getEncodedString(plainCredentials);
        String authorizationHeader = "Basic " + encodedString;
        String rootUrl = Jenkins.getInstance().getRootUrl();
        log.debug(logPrefix + "Root Url: " + rootUrl);
        String path = "job/"+jobName.replaceAll("/", "/job/")+"/api/json";
        log.debug(logPrefix + "Path: " + path);
        String finalUrl = null;
        String buildDetails = null;
        try {
            URIBuilder builder = new URIBuilder(rootUrl);
            builder.setPath(builder.getPath()+path); 
            builder.setParameter("fetchAllbuildDetails", "True");
            finalUrl = builder.toString();
            log.debug(logPrefix + "Final Url: " + finalUrl);
        } catch (Exception e) {
            log.warn(logPrefix + "Caught error while building url to get details of previous builds: ", e);
            return false;
        }
        try {
            HttpResponse<String> response = Unirest.get(finalUrl)
                .header("Authorization", authorizationHeader)
                .asString();
            buildDetails = response.getBody().toString();
            log.debug(logPrefix + "buildDetails Response: " + buildDetails);
        } catch (UnirestException e) {
            log.warn(logPrefix + "UnirestException: Failed to get details of previous Builds. Skipping duplicate check.");
            return false;
        }
        if (buildDetails != null) {
            JSONArray buildDetailsArray = JSONArray.fromObject("[" + buildDetails + "]");
            JSONObject buildDetailsObject = buildDetailsArray.getJSONObject(0);
            if(buildDetailsObject.has("builds")){
                JSONArray builds = JSONArray.fromObject(buildDetailsObject.getString("builds"));
                int buildsCount = 0;
                if(builds.size()<50){
                    buildsCount=builds.size();
                }
                else{
                    buildsCount=50;
                }
                StringBuilder str = new StringBuilder();
                for(int i=0;i<buildsCount;i++){
                    JSONObject build = builds.getJSONObject(i);
                    if(build.has("url")){
                        String buildUrl = build.getString("url")+"consoleText";
                        String finalBuildUrl = null;
                        try {
                            URIBuilder builder = new URIBuilder(buildUrl);
                            finalBuildUrl = builder.toString();
                        } catch (Exception e) {
                            log.error(logPrefix + "Caught error while building console log url: ", e);
                        }
                        try {
                            HttpResponse<String> buildResponse = Unirest.get(finalBuildUrl)
                            .header("Authorization", authorizationHeader)
                            .asString();
                            String buildConsole = buildResponse.getBody().toString();
                            str.append(buildConsole);
                        } catch (UnirestException e) {
                            log.error(logPrefix + "UnirestException: Failed to get console Logs of previous builds", e);
                        }
                    }
                }
                String allConsoleLogs = str.toString();
                boolean isFound = allConsoleLogs.contains("Started due to a request from UrbanCode Velocity. Work Id: "+workId);
                if(isFound==true){
                    log.info(logPrefix + " =========================== Found duplicate Jenkins Job and stopped it =========================== ");
                    return true;
                }
            }
        }
        return false;
    }

    public void callSecured(ConnectSocket socket, String event, String securityError, Object... args) {
        log.info(logPrefix + " Received event from Connect Socket");

        String payload = args[0].toString();
        String token = Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncToken();

        try {
            payload = decrypt(token, payload);
        } catch (Exception e) {
            log.error("Unable to decrypt");
        }

        JSONArray incomingJobs = JSONArray.fromObject("[" + payload + "]");
        log.info(logPrefix + "incomingJobs: " + incomingJobs.toString());

        for(int i=0; i < incomingJobs.size(); i++) {
            JSONObject incomingJob = incomingJobs.getJSONObject(i);
            Boolean isDuplicate = false;
            if (Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getCheckDuplicate() == true) {
                isDuplicate = isDuplicateJob(incomingJob);
                log.info(logPrefix + "isDuplicate: " + isDuplicate.toString());
            }
            if (isDuplicate == false) {
                 // sample job creation request from a toolchain
                if (incomingJob.has("jobType") && "new".equalsIgnoreCase(incomingJob.get("jobType").toString())) {
                    log.info(logPrefix + "Job creation request received.");
                    // delegating job creation to the Jenkins server
                    JenkinsServer.createJob(incomingJob);
                }

                if (incomingJob.has("fullName")) {
                    String fullName = incomingJob.get("fullName").toString();

                    Jenkins myJenkins = Jenkins.getInstance();

                    // Get item by name
                    Item item = myJenkins.getItem(fullName);

                    log.info("Item Found (1): " + item);

                    // If item is not retrieved, get by full name
                    if(item == null) {
                        item = myJenkins.getItemByFullName(fullName);
                        log.info("Item Found (2): " + item);
                    }

                    // If item is not retrieved, get by full name with escaped characters
                    if(item == null) {
                        item = myJenkins.getItemByFullName(escapeItemName(fullName));
                        log.info("Item Found (3): " + item);
                    }

                    List<ParameterValue> parametersList = generateParamList(incomingJob, getParameterTypeMap(item));

                    JSONObject returnProps = new JSONObject();
                    if(incomingJob.has("returnProps")) {
                        returnProps = incomingJob.getJSONObject("returnProps");
                    }

                    CloudCause cloudCause = new CloudCause(incomingJob.get("id").toString(), returnProps);
                    Queue.Item queuedItem = null;
                    String errorMessage = null;

                    if(item instanceof AbstractProject) {
                        AbstractProject abstractProject = (AbstractProject)item;

                        queuedItem = ParameterizedJobMixIn.scheduleBuild2(abstractProject, 0, new ParametersAction(parametersList), new CauseAction(cloudCause));

                        if (queuedItem == null) {
                            errorMessage = "Could not start parameterized build.";
                        }
                    } else if (item instanceof WorkflowJob) {
                        WorkflowJob workflowJob = (WorkflowJob)item;

                        QueueTaskFuture queuedTask = workflowJob.scheduleBuild2(0, new ParametersAction(parametersList), new CauseAction(cloudCause));

                        if (queuedTask == null) {
                            errorMessage = "Could not start pipeline build.";
                        }
                    } else if (item == null) {
                        if(securityError != null) {
                            if(securityError.equals(AbstractSecuredAction.NO_CREDENTIALS_PROVIDED)) {
                                errorMessage = "No Item Found. No Jenkins credentials were provided in Velocity config on Jenkins 'Configure System' page.  Credentials may be required.";
                            } else {
                                errorMessage = securityError;
                            }
                        } else {
                            errorMessage = "No Item Found";
                        }
                        log.warn(errorMessage);
                    } else {
                        errorMessage = "Unhandled job type found: " + item.getClass();
                        log.warn(errorMessage);
                    }

                    if( errorMessage != null ) {
                        JenkinsJobStatus erroredJobStatus = new JenkinsJobStatus(null, cloudCause, null, null, true, true);
                        JSONObject statusUpdate = erroredJobStatus.generateErrorStatus(errorMessage);
                        CloudPublisher.uploadJobStatus(statusUpdate);
                    }
                }
                //sendResult(socket, incomingJobs.getJSONObject(i).get("id").toString(), WorkStatus.started, "This work has been started");
            }
        }

    }

    private List<ParameterValue> generateParamList (JSONObject incomingJob, Map<String, String> typeMap) {
        ArrayList<ParameterValue> result = new ArrayList<ParameterValue>();

        if(incomingJob.has("props")) {
            JSONObject props = incomingJob.getJSONObject("props");
            Iterator<String> keys = props.keys();
            while( keys.hasNext() ) {
                String key = (String)keys.next();
                Object value = props.get(key);
                String type = typeMap.get(key);

                ParameterValue paramValue;

                System.out.println("->\t\t" + key);
                System.out.println("->\t\t" + value);
                System.out.println("->\t\t" + type);

                if(type == null) {

                } else if(type.equalsIgnoreCase("BooleanParameterDefinition")) {
                    if(props.get(key).getClass().equals(String.class)) {
                        Boolean p = Boolean.parseBoolean((String)props.get(key));
                        result.add(new BooleanParameterValue(key, p));
                    } else {
                        result.add(new BooleanParameterValue(key, (boolean)props.get(key)));
                    }
                } else if(type.equalsIgnoreCase("PasswordParameterDefinition")) {
                    result.add(new PasswordParameterValue(key, props.get(key).toString()));
                } else if(type.equalsIgnoreCase("TextParameterDefinition")) {
                    result.add(new TextParameterValue(key, props.get(key).toString()));
                } else {
                    result.add(new StringParameterValue(key, props.get(key).toString()));
                }
            }
        }

        return result;
    }

    private Map<String, String> getParameterTypeMap(Item item) {
        Map<String, String> result = new HashMap<String, String>();

        if(item instanceof WorkflowJob) {
            List<JobProperty<? super WorkflowJob>> properties = ((WorkflowJob)item).getAllProperties();

            for(JobProperty property : properties) {
                if (property instanceof ParametersDefinitionProperty) {
                    List<ParameterDefinition> paraDefs = ((ParametersDefinitionProperty)property).getParameterDefinitions();
                    for (ParameterDefinition paramDef : paraDefs) {
                        result.put(paramDef.getName(), paramDef.getType());
                    }
                }
            }
        } else if(item instanceof AbstractItem) {
            List<Action> actions = ((AbstractItem)item).getActions();

            for(Action action : actions) {
                if (action instanceof ParametersDefinitionProperty) {
                    List<ParameterDefinition> paraDefs = ((ParametersDefinitionProperty)action).getParameterDefinitions();
                    for (ParameterDefinition paramDef : paraDefs) {
                        result.put(paramDef.getName(), paramDef.getType());
                    }
                }
            }
        }

        return result;
    }

    private String escapeItemName(String itemName) {
        String result = itemName.replace("\'", "&apos;");
        return result;
    }
}
