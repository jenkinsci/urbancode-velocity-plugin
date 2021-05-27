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
import com.ibm.devops.connect.Endpoints.EndpointManager2;


public class CloudSocketComponent2 {

    public static final Logger log = LoggerFactory.getLogger(CloudSocketComponent2.class);
    private String logPrefix= "[UrbanCode Velocity2] CloudSocketComponent#";

    final private IWorkListener workListener;
    final private String cloudUrl;

    private static Connection conn;

    private static boolean queueIsAvailable = false;
    private static boolean otherIntegrationExists = false;

    private static void setOtherIntegrationsExists(boolean exists) {
        otherIntegrationExists = exists;
    }

    public CloudSocketComponent2(IWorkListener workListener, String cloudUrl) {
        this.workListener = workListener;
        this.cloudUrl = cloudUrl;
    }

    public boolean isRegistered() {
        return StringUtils.isNotBlank(getSyncToken());
    }

    public String getSyncId() {
        return Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncId2();
    }

    public String getSyncToken() {
        return Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncToken2();
    }

    public void connectToCloudServices() throws Exception {
        if (Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).isConfigured2()) {
            logPrefix= logPrefix + "connectToCloudServices ";

            connectToAMQP();

            log.info(logPrefix + "Assembling list of Jenkins Jobs for 2nd Instance...");

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

    public void connectToAMQP() throws Exception {
        if (!Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).isConfigured2()) {
            return;
        }

        String syncId = getSyncId();

        ConnectionFactory factory = new ConnectionFactory();
        factory.setAutomaticRecoveryEnabled(false);

        EndpointManager2 em = new EndpointManager2();

        // Public Jenkins Client Credentials
        factory.setUsername("jenkins");
        factory.setPassword("jenkins");

        String host = em.getVelocityHostname();
        String rabbitHost = Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getRabbitMQHost2();
        if (rabbitHost != null && !rabbitHost.equals("")) {
            try {
                if (rabbitHost.endsWith("/")) {
                    rabbitHost = rabbitHost.substring(0, rabbitHost.length() - 1);
                }
                URL urlObj = new URL(rabbitHost);
                host = urlObj.getHost();
            } catch (MalformedURLException e) {
                log.warn("Provided Rabbit MQ Host is not a valid hostname. Using default for 2nd Instance: " + host, e);
            }
        }
        log.info(host);
        factory.setHost(host);

        int port = 5672;
        String rabbitPort = Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getRabbitMQPort2();

        if (rabbitPort != null && !rabbitPort.equals("")) {
            try {
                port = Integer.parseInt(rabbitPort);
            } catch (NumberFormatException nfe) {
                log.warn("Provided Rabbit MQ port is not an integer.  Using default 5672 for 2nd Instance");
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

            log.info("Connecting to RabbitMQ for 2nd Instance");

            String EXCHANGE_NAME = "jenkins";
            String queueName = "jenkins.client." + syncId;

            Consumer consumer = new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope,
                                            AMQP.BasicProperties properties, byte[] body) throws IOException {

                    if (envelope.getRoutingKey().contains(".heartbeat")) {
                        String syncId = getSyncId();
                        String syncToken = getSyncToken();

                        String url = removeTrailingSlash(Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getBaseUrl2());
                        boolean connected = CloudPublisher.testConnection2(syncId, syncToken, url);
                    } else {
                        String message = new String(body, "UTF-8");
                        System.out.println(" [x] Received '" + message + "' from 2nd Instance");

                        CloudWorkListener2 cloudWorkListener = new CloudWorkListener2();
                        cloudWorkListener.call("startJob", message);
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
