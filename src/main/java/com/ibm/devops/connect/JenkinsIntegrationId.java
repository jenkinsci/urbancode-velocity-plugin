package com.ibm.devops.connect;

import org.jenkinsci.plugins.uniqueid.IdStore;
import com.ibm.devops.connect.DevOpsGlobalConfiguration;
import jenkins.model.Jenkins;

public class JenkinsIntegrationId {
    public JenkinsIntegrationId () {

    }

    public String getIntegrationId() {
        String result = getSyncId() + "_" + getJenkinsId();
        return result;
    }

    private String getJenkinsId() {
        String jenkinsId;
	Jenkins jenkins = Jenkins.getInstanceOrNull();
	if (jenkins != null){
		jenkinsId = IdStore.getId(jenkins);
		if (jenkinsId == null) {        
            		IdStore.makeId(jenkins);
            		jenkinsId = IdStore.getId(jenkins);
        	}	
	}//but what if jenkins is null? Maybe get() instead of getInstanceOrNull()
        return jenkinsId;
    }

    private String getSyncId() {
        return Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncId();
    }
}
