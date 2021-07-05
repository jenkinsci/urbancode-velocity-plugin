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

    public ReconnectExecutor (CloudSocketComponent cloudSocketInstance) {
        this.cloudSocketInstance = cloudSocketInstance;
    }

    public void startReconnectExecutor() {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

        ReconnectRunner runner = new ReconnectRunner();

        executor.scheduleWithFixedDelay(runner, 10, 10, TimeUnit.SECONDS);
    }

    private class ReconnectRunner implements Runnable {
        @Override
        public void run()
        {
            List<Entry> entries = Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getEntries();
            for (int instanceNum = 0; instanceNum < entries.size(); instanceNum++) {
            if (!cloudSocketInstance.isAMQPConnected(instanceNum) && entries.get(instanceNum).isConfigured()) {
                    try {
                        cloudSocketInstance.connectToAMQP(instanceNum);
                    } catch (Exception e) {
                        log.error("[UrbanCode Velocity "+ entries.get(instanceNum).getBaseUrl() + "] Unable to Reconnect to UCV AMQP", e);
                    }
                }
            }
        }
    }

}