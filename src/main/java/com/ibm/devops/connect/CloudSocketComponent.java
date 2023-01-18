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
import java.net.URISyntaxException;
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
import java.util.List;

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

    final private IWorkListener workListener;
    final private String cloudUrl;

    private static Connection conn[] = new Connection[5];

    private static boolean queueIsAvailable = false;
    private static boolean otherIntegrationExists = false;

    private static void setOtherIntegrationsExists(boolean exists) {
        otherIntegrationExists = exists;
    }

    public CloudSocketComponent(IWorkListener workListener, String cloudUrl) {
        this.workListener = workListener;
        this.cloudUrl = cloudUrl;
    }

    public void connectToCloudServices(Entry entry) throws Exception {
        if (entry.isConfigured()) {
            connectToAMQP(entry);
            log.info("[UrbanCode Velocity " + entry.getBaseUrl() + "] CloudSocketComponent#connectToCloudServices "
                    + "Assembling list of Jenkins Jobs...");

            BuildJobsList buildJobList = new BuildJobsList();
            BuildJobListParamObj paramObj = buildJobList.new BuildJobListParamObj();
            buildJobList.runAsJenkinsUser(paramObj);
        }
    }

    public static int getInstanceNum(Entry entry) {
        List<Entry> entries = Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getEntries();
        int i = 0;
        int instanceNum = -1;
        for (Entry entry2 : entries) {
            if (entry.equals(entry2)) {
                instanceNum = i;
                break;
            }
            i++;
        }
        return instanceNum;
    }

    public static boolean isAMQPConnected(Entry entry) {
        int instanceNum = getInstanceNum(entry);
        if (conn[instanceNum] == null || queueIsAvailable == false) {
            return false;
        }
        return conn[instanceNum].isOpen();
    }

    private static byte[] toByte(String hexString) {
        int len = hexString.length() / 2;
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++) {
            result[i] = Integer.valueOf(hexString.substring(2 * i, 2 * i + 2), 16).byteValue();
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

    private static String getEncodedString(String credentials) {
        return Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    public void connectToAMQP(Entry entry) throws Exception {
        if (!entry.isConfigured()) {
            return;
        }
        String logPrefix = "[UrbanCode Velocity " + entry.getBaseUrl() + "] ";
        String syncId = entry.getSyncId();

        ConnectionFactory factory = new ConnectionFactory();
        factory.setAutomaticRecoveryEnabled(false);

        EndpointManager em = new EndpointManager();

        // Public Jenkins Client Credentials
        factory.setUsername("jenkins");
        factory.setPassword("jenkins");

        String host = em.getVelocityHostname(entry);
        String rabbitHost = entry.getRabbitMQHost();
        if (rabbitHost != null && !rabbitHost.equals("")) {
            try {
                if (rabbitHost.endsWith("/")) {
                    rabbitHost = rabbitHost.substring(0, rabbitHost.length() - 1);
                }
                URL urlObj = new URL(rabbitHost);
                host = urlObj.getHost();
            } catch (MalformedURLException e) {
                log.warn(logPrefix + "Provided Rabbit MQ Host is not a valid hostname. Using default : " + host, e);
            }
        }
        factory.setHost(host);

        int port = 5672;
        String rabbitPort = entry.getRabbitMQPort();

        if (rabbitPort != null && !rabbitPort.equals("")) {
            try {
                port = Integer.parseInt(rabbitPort);
            } catch (NumberFormatException nfe) {
                log.warn(logPrefix + "Provided Rabbit MQ port is not an integer.  Using default 5672");
            }
        }
        factory.setPort(port);
        // Synchronized to protect manipulation of static variable
        synchronized (this) {
            if (this.conn[getInstanceNum(entry)] != null && this.conn[getInstanceNum(entry)].isOpen()) {
                this.conn[getInstanceNum(entry)].abort();
            }

            conn[getInstanceNum(entry)] = factory.newConnection();

            Channel channel = conn[getInstanceNum(entry)].createChannel();

            log.info(logPrefix + "Connecting to RabbitMQ");

            // String EXCHANGE_NAME = "jenkins";
            String queueName = "jenkins.client." + syncId;

            Consumer consumer = new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope,
                        AMQP.BasicProperties properties, byte[] body) throws IOException {

                    if (envelope.getRoutingKey().contains(".heartbeat")) {
                        String syncId = entry.getSyncId();
                        String syncToken = entry.getSyncToken();
                        String apiToken = entry.getApiToken();
                        String url = CloudPublisher.removeTrailingSlash(entry.getBaseUrl());
                        try {
                            String connected = CloudPublisher.testConnection(syncId, syncToken, url, apiToken);
                        } catch (URISyntaxException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                        
                    } else {
                        String message = new String(body, "UTF-8");
                        if (entry.getCheckDuplicate() == true) {
                            log.info("Checking for duplicate jobs enabled...");
                            String payload = null;
                            String syncToken = entry.getSyncToken();
                            try {
                                payload = decrypt(syncToken, message.toString());
                            } catch (Exception e) {
                                System.out.println("Unable to decrypt");
                            }
                            JSONArray incomingJobs = JSONArray.fromObject("[" + payload + "]");
                            JSONObject incomingJob = incomingJobs.getJSONObject(0);
                            String workId = incomingJob.getString("id");
                            String jobName = incomingJob.getString("fullName");
                            StandardUsernamePasswordCredentials credentials = Jenkins.getInstance()
                                    .getDescriptorByType(DevOpsGlobalConfiguration.class).getCredentialsObj();
                            String plainCredentials = credentials.getUsername() + ":"
                                    + credentials.getPassword().getPlainText();
                            String encodedString = getEncodedString(plainCredentials);
                            String authorizationHeader = "Basic " + encodedString;
                            String rootUrl = Jenkins.getInstance().getRootUrl();
                            String path = "job/" + jobName.replaceAll("/", "/job/") + "/api/json";
                            String finalUrl = null;
                            String buildDetails = null;
                            try {
                                URIBuilder builder = new URIBuilder(rootUrl);
                                builder.setPath(builder.getPath() + path);
                                builder.setQuery("fetchAllbuildDetails=True");
                                finalUrl = builder.toString();
                            } catch (Exception e) {
                                log.error("Caught error while building url to get details of previous builds: ", e);
                            }
                            try {
                                HttpResponse<String> response = Unirest.get(finalUrl)
                                        .header("Authorization", authorizationHeader)
                                        .asString();
                                buildDetails = response.getBody().toString();
                            } catch (UnirestException e) {
                                log.error("UnirestException: Failed to get details of previous Builds", e);
                            }
                            JSONArray buildDetailsArray = JSONArray.fromObject("[" + buildDetails + "]");
                            JSONObject buildDetailsObject = buildDetailsArray.getJSONObject(0);
                            if (buildDetailsObject.has("builds")) {
                                JSONArray builds = JSONArray.fromObject(buildDetailsObject.getString("builds"));
                                int buildsCount = 0;
                                if (builds.size() < 50) {
                                    buildsCount = builds.size();
                                } else {
                                    buildsCount = 50;
                                }
                                StringBuilder str = new StringBuilder();
                                for (int i = 0; i < buildsCount; i++) {
                                    JSONObject build = builds.getJSONObject(i);
                                    if (build.has("url")) {
                                        String buildUrl = build.getString("url") + "consoleText";
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
                                boolean isFound = allConsoleLogs
                                        .contains("Started due to a request from UrbanCode Velocity. Work Id: " + workId);
                                if (isFound == true) {
                                    log.info(
                                            " =========================== Found duplicate Jenkins Job and stopped it =========================== ");
                                } else {
                                    System.out.println(logPrefix + "[x] Received '" + message + "'");
                                    CloudWorkListener2 cloudWorkListener = new CloudWorkListener2();
                                    cloudWorkListener.call("startJob", message, entry);
                                }
                            }
                        } else {
                            System.out.println(logPrefix + "[x] Received '" + message + "'");
                            CloudWorkListener2 cloudWorkListener = new CloudWorkListener2();
                            cloudWorkListener.call("startJob", message, entry);
                        }  
                    }
                }
            };

            if (checkQueueAvailability(channel, queueName, entry)) {
                channel.basicConsume(queueName, true, consumer);
            } else {
                log.info(logPrefix + "Queue is not yet available, will attempt to reconnect shortly...");
                queueIsAvailable = false;
            }
        }
    }

    public static boolean checkQueueAvailability(Channel channel, String queueName, Entry entry) throws IOException {
        try {
            channel.queueDeclarePassive(queueName);
            queueIsAvailable = true;
            return true;
        } catch (IOException e) {
            log.error("[UrbanCode Velocity " + entry.getBaseUrl() + "] Checking Queue availability threw exception: ",
                    e);
        }
        return false;
    }

    public String getCauseOfFailure() {
        if (otherIntegrationExists) {
            return "These credentials have been used by another Jenkins Instance.  Please generate another Sync Id and provide those credentials here.";
        }

        return null;
    }
}