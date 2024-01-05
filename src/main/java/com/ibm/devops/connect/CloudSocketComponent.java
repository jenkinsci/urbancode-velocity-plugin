/*******************************************************************************
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2017. All Rights Reserved.
 *
 * Note to U.S. Government Users Restricted Rights:  Use,
 * duplication or disclosure restricted by GSA ADP Schedule
 * Contract with IBM Corp.
 *******************************************************************************/
package com.ibm.devops.connect;

import java.net.MalformedURLException;
import java.net.URL;

import jenkins.model.Jenkins;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.devops.connect.SecuredActions.BuildJobsList;
import com.ibm.devops.connect.SecuredActions.BuildJobsList.BuildJobListParamObj;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.*;

import java.io.IOException;
import com.ibm.devops.connect.Endpoints.EndpointManager;


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


public class CloudSocketComponent {

    public static final Logger log = LoggerFactory.getLogger(CloudSocketComponent.class);
    private String logPrefix= "[UrbanCode Velocity] CloudSocketComponent#";

    final private IWorkListener workListener;
    final private String cloudUrl;

    private static Connection conn;

    private static boolean queueIsAvailable = false;
    private static boolean otherIntegrationExists = false;

    // private static void setOtherIntegrationsExists(boolean exists) {
    //     otherIntegrationExists = exists;
    // }

    public CloudSocketComponent(IWorkListener workListener, String cloudUrl) {
        this.workListener = workListener;
        this.cloudUrl = cloudUrl;
    }

    public boolean isRegistered() {
        return StringUtils.isNotBlank(getSyncToken());
    }

    public String getSyncId() {
        return Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncId();
    }

    public String getSyncToken() {
        return Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncToken();
    }

    public void connectToCloudServices() throws Exception {
        if (Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).isConfigured()) {
            logPrefix= logPrefix + "connectToCloudServices ";

            connectToAMQP();

            log.info(logPrefix + "Assembling list of Jenkins Jobs...");

            BuildJobsList buildJobList = new BuildJobsList();
            BuildJobListParamObj paramObj = buildJobList.new BuildJobListParamObj();
            buildJobList.runAsJenkinsUser(paramObj);
        }
    }

    public static boolean isAMQPConnected() {
        if (conn == null || queueIsAvailable == false) {
            return false;
        }
        return conn.isOpen();
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

    private static String getEncodedString(String credentials){  
        return Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));   
    }

    public void connectToAMQP() throws Exception {
        if (!Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).isConfigured()) {
            return;
        }

        String syncId = getSyncId();

        ConnectionFactory factory = new ConnectionFactory();
        factory.setAutomaticRecoveryEnabled(false);

        EndpointManager em = new EndpointManager();

        // Public Jenkins Client Credentials
        factory.setUsername("jenkins");
        factory.setPassword("jenkins");

        String host = em.getVelocityHostname();
        String rabbitHost = Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getRabbitMQHost();
        if (rabbitHost != null && !rabbitHost.equals("")) {
            try {
                if (rabbitHost.endsWith("/")) {
                    rabbitHost = rabbitHost.substring(0, rabbitHost.length() - 1);
                }
                URL urlObj = new URL(rabbitHost);
                host = urlObj.getHost();
            } catch (MalformedURLException e) {
                log.warn("Provided Rabbit MQ Host is not a valid hostname. Using default : " + host, e);
            }
        }
        factory.setHost(host);

        int port = 5672;
        String rabbitPort = Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getRabbitMQPort();

        if (rabbitPort != null && !rabbitPort.equals("")) {
            try {
                port = Integer.parseInt(rabbitPort);
            } catch (NumberFormatException nfe) {
                log.warn("Provided Rabbit MQ port is not an integer.  Using default 5672");
            }
        }
        factory.setPort(port);

        // Synchronized to protect manipulation of static variable
        synchronized (this) {

            if(this.conn != null && this.conn.isOpen()) {
                this.conn.abort();
            }

            conn = factory.newConnection();

            Channel channel = conn.createChannel();

            log.info("Connecting to RabbitMQ");

            // String EXCHANGE_NAME = "jenkins";
            String queueName = "jenkins.client." + syncId;

            Consumer consumer = new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope,
                                            AMQP.BasicProperties properties, byte[] body) throws IOException {

                    if (envelope.getRoutingKey().contains(".heartbeat")) {
                        String syncId = getSyncId();
                        String syncToken = getSyncToken();

                        String url = removeTrailingSlash(Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getBaseUrl());
                        CloudPublisher.testConnection(syncId, syncToken, url);
                    } else {
                        String message = new String(body, "UTF-8");
                        String payload = null;
                        String syncToken = getSyncToken();
                        try {
                            payload = decrypt(syncToken, message.toString());
                            log.info("payload: " + payload);
                        } catch (Exception e) {
                            System.out.println("Unable to decrypt");
                        }
                        JSONArray incomingJobs = JSONArray.fromObject("[" + payload + "]");
                        JSONObject incomingJob = incomingJobs.getJSONObject(0);
                        String workId = incomingJob.getString("id");
                        String jobName = incomingJob.getString("fullName");
                        StandardUsernamePasswordCredentials credentials = Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getCredentialsObj();          
                        String plainCredentials = credentials.getUsername() + ":" + credentials.getPassword().getPlainText();
                        String encodedString = getEncodedString(plainCredentials);
                        String authorizationHeader = "Basic " + encodedString;
                        String rootUrl = Jenkins.getInstance().getRootUrl();
                        log.info("Root Url: " + rootUrl);
                        String path = "job/"+jobName.replaceAll("/", "/job/")+"/api/json";
                        log.info("Path: " + path);
                        String finalUrl = null;
                        String buildDetails = null;
                        try {
                            URIBuilder builder = new URIBuilder(rootUrl);
                            builder.setPath(builder.getPath()+path); 
                            builder.setParameter("fetchAllbuildDetails", "True");
                            finalUrl = builder.toString();
                            log.info("Final Url: " + finalUrl);
                        } catch (Exception e) {
                            log.warn("Caught error while building url to get details of previous builds: ", e);
                        }
                        try {
                            HttpResponse<String> response = Unirest.get(finalUrl)
                                .header("Authorization", authorizationHeader)
                                .asString();
                            buildDetails = response.getBody().toString();
                            log.info("buildDetails Response: " + buildDetails);
                        } catch (UnirestException e) {
                            log.warn("UnirestException: Failed to get details of previous Builds. Skipping duplicate check.");
                            System.out.println(" [x] Received '" + message + "'");
                            CloudWorkListener2 cloudWorkListener = new CloudWorkListener2();
                            cloudWorkListener.call("startJob", message);
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
                                            log.error("Caught error while building console log url: ", e);
                                        }
                                        try {
                                            HttpResponse<String> buildResponse = Unirest.get(finalBuildUrl)
                                            .header("Authorization", authorizationHeader)
                                            .asString();
                                            String buildConsole = buildResponse.getBody().toString();
                                            str.append(buildConsole);
                                        } catch (UnirestException e) {
                                            log.error("UnirestException: Failed to get console Logs of previous builds", e);
                                        }
                                    }
                                }
                                String allConsoleLogs = str.toString();
                                boolean isFound = allConsoleLogs.contains("Started due to a request from UrbanCode Velocity. Work Id: "+workId);
                                if(isFound==true){
                                    log.info(" =========================== Found duplicate Jenkins Job and stopped it =========================== ");
                                }
                                else{
                                    System.out.println(" [x] Received '" + message + "'");
                                    CloudWorkListener2 cloudWorkListener = new CloudWorkListener2();
                                    cloudWorkListener.call("startJob", message);   
                                }    
                            }
                        }
                    }
                }
            };

            if (checkQueueAvailability(channel, queueName)) {
                channel.basicConsume(queueName, true, consumer);
            }else{
                log.info("Queue is not yet available, will attempt to reconect shortly...");
                queueIsAvailable = false;
            }
        }
    }

    public static boolean checkQueueAvailability(Channel channel, String queueName) throws IOException {
        try {
          channel.queueDeclarePassive(queueName);
          queueIsAvailable = true;
          return true;
        } catch (IOException e) {
            log.error("Checking Queue availability threw exception: ", e);
        }
        return false;
      }

    private String removeTrailingSlash(String url) {
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public String getCauseOfFailure() {
        if (otherIntegrationExists) {
            return "These credentials have been used by another Jenkins Instance.  Please generate another Sync Id and provide those credentials here.";
        }

        return null;
    }
}
