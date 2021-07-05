package com.ibm.devops.connect;

import org.jenkinsci.plugins.uniqueid.IdStore;

import com.ibm.devops.connect.DevOpsGlobalConfiguration;
import jenkins.model.Jenkins;
import java.util.List;

public class JenkinsIntegrationId {
    public JenkinsIntegrationId () {

    }

    public String getIntegrationId(int instanceNum) {
        String result = getSyncId(instanceNum) + "_" + getJenkinsId();
        return result;
    }

    private String getJenkinsId() {
        String jenkinsId;
        if (IdStore.getId(Jenkins.getInstance()) != null) {
            jenkinsId = IdStore.getId(Jenkins.getInstance());
        } else {
            IdStore.makeId(Jenkins.getInstance());
            jenkinsId = IdStore.getId(Jenkins.getInstance());
        }

        return jenkinsId;
    }

    private String getSyncId(int instanceNum) {
        List<Entry> entries = Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getEntries();
        return entries.get(instanceNum).getSyncId();
    }
}