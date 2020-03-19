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

@Extension
public class ConnectComputerListener extends ComputerListener {
	public static final Logger log = LoggerFactory.getLogger(ConnectComputerListener.class);
    private String logPrefix= "[UrbanCode Velocity] ConnectComputerListener#";

    private static CloudSocketComponent cloudSocketInstance;
    private static ReconnectExecutor reconnectExecutor;

    private static void setCloudSocketComponent( CloudSocketComponent comp ) {
        cloudSocketInstance = comp;
    }

    @Override
    public void onOnline(Computer c) {
        if ( c instanceof jenkins.model.Jenkins.MasterComputer && Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).isConfigured()) {
            logPrefix= logPrefix + "onOnline ";
            String url = getConnectUrl();

            CloudWorkListener listener = new CloudWorkListener();

            ConnectComputerListener.setCloudSocketComponent(new CloudSocketComponent(listener, url));

            try {
                log.info(logPrefix + "Connecting to Cloud Services...");
                getCloudSocketInstance().connectToCloudServices();
            } catch (Exception e) {
                log.error(logPrefix + "Exception caught while connecting to Cloud Services: " + e);
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
    }

    private String getConnectUrl() {
        EndpointManager em = new EndpointManager();
        return em.getConnectEndpoint();
    }

    public CloudSocketComponent getCloudSocketInstance() {
        return ConnectComputerListener.cloudSocketInstance;
    }
}