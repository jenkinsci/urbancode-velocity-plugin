/*******************************************************************************
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2017. All Rights Reserved.
 *
 * Note to U.S. Government Users Restricted Rights:  Use,
 * duplication or disclosure restricted by GSA ADP Schedule
 * Contract with IBM Corp.
 *******************************************************************************/
package com.ibm.devops.connect;

import hudson.slaves.ComputerListener;
import jenkins.model.Jenkins;
import hudson.model.Computer;
import hudson.Extension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.devops.connect.Endpoints.EndpointManager;

import com.ibm.devops.connect.ReconnectExecutor;
import java.util.List;

@Extension
public class ConnectComputerListener extends ComputerListener {
	public static final Logger log = LoggerFactory.getLogger(ConnectComputerListener.class);

    private static CloudSocketComponent cloudSocketInstance;
    private static ReconnectExecutor reconnectExecutor;

    public static boolean isRabbitConnected(int instanceNum){
        return cloudSocketInstance.isAMQPConnected(instanceNum);
    }

    private static void setCloudSocketComponent( CloudSocketComponent comp ) {
        cloudSocketInstance = comp;
    }

    @Override
    public void onOnline(Computer c) {
        List<Entry> entries = Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getEntries();
        int instanceNum = 0;
        for (Entry entry : entries) {
            if ( c instanceof jenkins.model.Jenkins.MasterComputer && entry.isConfigured()) {
                String url = getConnectUrl(instanceNum);
    
                CloudWorkListener listener = new CloudWorkListener();
    
                ConnectComputerListener.setCloudSocketComponent(new CloudSocketComponent(listener, url));
    
                try {
                    log.info("[UrbanCode Velocity "+ entry.getBaseUrl() + "] ConnectComputerListener#onOnline - Connecting to Cloud Services...");
                    getCloudSocketInstance().connectToCloudServices(instanceNum);
                } catch (Exception e) {
                    log.error("[UrbanCode Velocity "+ entry.getBaseUrl() + "] ConnectComputerListener#onOnline - Exception caught while connecting to Cloud Services: " + e);
                    e.printStackTrace();
                }
    
                // Synchronized to protect lazy initalization of static variable
                synchronized(this) {
                    if(reconnectExecutor == null) {
                        reconnectExecutor = new ReconnectExecutor(cloudSocketInstance);
                        reconnectExecutor.startReconnectExecutor();
                    }
                }
            }
            instanceNum =instanceNum + 1;
        }
    }

    private String getConnectUrl(int i) {
        EndpointManager em = new EndpointManager();
        return em.getConnectEndpoint(i);
    }

    public CloudSocketComponent getCloudSocketInstance() {
        return ConnectComputerListener.cloudSocketInstance;
    }
}