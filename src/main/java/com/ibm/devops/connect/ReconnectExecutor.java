/*******************************************************************************
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2017. All Rights Reserved.
 *
 * Note to U.S. Government Users Restricted Rights:  Use,
 * duplication or disclosure restricted by GSA ADP Schedule
 * Contract with IBM Corp.
 *******************************************************************************/
package com.ibm.devops.connect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jenkins.model.Jenkins;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.lang.Runnable;
import java.util.List;

public class ReconnectExecutor {
    public static final Logger log = LoggerFactory.getLogger(ReconnectExecutor.class);

    private CloudSocketComponent cloudSocketInstance;

    public ReconnectExecutor(CloudSocketComponent cloudSocketInstance) {
        this.cloudSocketInstance = cloudSocketInstance;
    }

    public void startReconnectExecutor() {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

        ReconnectRunner runner = new ReconnectRunner();

        executor.scheduleWithFixedDelay(runner, 10, 10, TimeUnit.SECONDS);
    }

    private List<Entry> getEntries() {
        return Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getEntries();
    }

    private class ReconnectRunner implements Runnable {
        @Override
        public void run() {
            for (Entry entry : getEntries()) {
                if (!cloudSocketInstance.isAMQPConnected(entry) && entry.isConfigured()) {
                    try {
                        cloudSocketInstance.connectToAMQP(entry);
                    } catch (Exception e) {
                        log.error("[UrbanCode Velocity " + entry.getBaseUrl() + "] Unable to Reconnect to UCV AMQP", e);
                    }
                }
            }
        }
    }

}