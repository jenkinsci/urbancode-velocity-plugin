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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.lang.InterruptedException;

import org.jenkinsci.plugins.workflow.job.WorkflowJob;

import net.sf.json.JSONObject;

import com.ibm.devops.connect.CloudCause.JobStatus;
import com.ibm.devops.connect.SecuredActions.TriggerJob.TriggerJobParamObj;
import com.ibm.devops.connect.SecuredActions.TriggerJob;

import com.ibm.devops.connect.Status.JenkinsJobStatus;

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

    public void callSecured(ConnectSocket socket, String event, String securityError, Object... args) {
        List<Entry> entries = Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getEntries();
        String payload = args[0].toString();
        int instanceNum = Integer.parseInt(args[1].toString());
        log.info("[UrbanCode Velocity "+ entries.get(instanceNum).getBaseUrl() + "] CloudWorkListener2#callSecured - Received event from Connect Socket");
        String token = entries.get(instanceNum).getSyncToken();

        try {
            payload = decrypt(token, payload);
        } catch (Exception e) {
            //TODO handle decryption error
            System.out.println("Unable to decrypt");
        }

        //TODO Don't make this an array in the silly way that I have.  I just want this to work
        JSONArray incomingJobs = JSONArray.fromObject("[" + payload + "]");

        for(int i=0; i < incomingJobs.size(); i++) {
            JSONObject incomingJob = incomingJobs.getJSONObject(i);
            // sample job creation request from a toolchain
            if (incomingJob.has("jobType") && "new".equalsIgnoreCase(incomingJob.get("jobType").toString())) {
                log.info("[UrbanCode Velocity "+ entries.get(instanceNum).getBaseUrl() + "] CloudWorkListener2#callSecured - Job creation request received.");
                // delegating job creation to the Jenkins server
                JenkinsServer.createJob(incomingJob, instanceNum);
            }

            if (incomingJob.has("fullName")) {
                String fullName = incomingJob.get("fullName").toString();

                Jenkins myJenkins = Jenkins.getInstance();

                // Get item by name
                Item item = myJenkins.getItem(fullName);

                log.info("[UrbanCode Velocity "+ entries.get(instanceNum).getBaseUrl() + "] CloudWorkListener2#callSecured - Item Found (1): " + item);

                // If item is not retrieved, get by full name
                if(item == null) {
                    item = myJenkins.getItemByFullName(fullName);
                    log.info("[UrbanCode Velocity "+ entries.get(instanceNum).getBaseUrl() + "] CloudWorkListener2#callSecured - Item Found (2): " + item);
                }

                // If item is not retrieved, get by full name with escaped characters
                if(item == null) {
                    item = myJenkins.getItemByFullName(escapeItemName(fullName));
                    log.info("[UrbanCode Velocity "+ entries.get(instanceNum).getBaseUrl() + "] CloudWorkListener2#callSecured - Item Found (3): " + item);
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
                    log.warn("[UrbanCode Velocity "+ entries.get(instanceNum).getBaseUrl() + "] CloudWorkListener2#callSecured - " + errorMessage);
                } else {
                    errorMessage = "Unhandled job type found: " + item.getClass();
                    log.warn("[UrbanCode Velocity "+ entries.get(instanceNum).getBaseUrl() + "] CloudWorkListener2#callSecured - " + errorMessage);
                }

                if( errorMessage != null ) {
                    JenkinsJobStatus erroredJobStatus = new JenkinsJobStatus(null, cloudCause, null, null, true, true);
                    for (int instanceNum2 = 0; instanceNum2 < entries.size(); instanceNum++) {
                        JSONObject statusUpdate = erroredJobStatus.generateErrorStatus(errorMessage, instanceNum);
                        CloudPublisher.uploadJobStatus(statusUpdate, instanceNum2);
                    }
                }

            }

            //sendResult(socket, incomingJobs.getJSONObject(i).get("id").toString(), WorkStatus.started, "This work has been started");
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
