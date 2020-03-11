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


public class CloudSocketComponent {

    public static final Logger log = LoggerFactory.getLogger(CloudSocketComponent.class);
    private String logPrefix= "[UrbanCode Velocity] CloudSocketComponent#";

    final private IWorkListener workListener;
    final private String cloudUrl;

    private static Connection conn;

    private static boolean otherIntegrationExists = false;

    private static void setOtherIntegrationsExists(boolean exists) {
        otherIntegrationExists = exists;
    }

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
        if (conn == null) {
            return false;
        }

        return conn.isOpen();
    }

    public void connectToAMQP() throws Exception {
        if (!Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).isConfigured()) {
            return;
        }

        String syncId = getSyncId();

        ConnectionFactory factory = new ConnectionFactory();
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

            String EXCHANGE_NAME = "jenkins";
            String queueName = "jenkins.client." + syncId;

            Consumer consumer = new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope,
                                            AMQP.BasicProperties properties, byte[] body) throws IOException {

                    if (envelope.getRoutingKey().contains(".heartbeat")) {
                        String syncId = getSyncId();
                        String syncToken = getSyncToken();

                        String url = removeTrailingSlash(Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getBaseUrl());
                        boolean connected = CloudPublisher.testConnection(syncId, syncToken, url);
                    } else {
                        String message = new String(body, "UTF-8");
                        System.out.println(" [x] Received '" + message + "'");

                        CloudWorkListener2 cloudWorkListener = new CloudWorkListener2();
                        cloudWorkListener.call("startJob", message);
                    }
                }
            };

            channel.basicConsume(queueName, true, consumer);
        }
        
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
