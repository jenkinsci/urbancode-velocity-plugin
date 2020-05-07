/*
 <notice>

 Copyright 2018 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
*/

package com.ibm.devops.connect;

import hudson.Extension;
import hudson.model.listeners.RunListener;
import hudson.model.TaskListener;
import hudson.model.Cause;
import hudson.model.Run;
import hudson.tasks.BuildStep;
import jenkins.model.Jenkins;

import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.json.JSONObject;
import net.sf.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

import com.ibm.devops.connect.CloudCause.JobStatus;
import com.ibm.devops.connect.Status.AbstractJenkinsStatus;
import com.ibm.devops.connect.Status.JenkinsJobStatus;
import com.ibm.devops.connect.Status.JenkinsPipelineStatus;

@Extension
public class CloudRunListener extends RunListener<Run> {
    public static final Logger log = LoggerFactory.getLogger(CloudRunListener.class);

    @Override
    public void onStarted(Run run, TaskListener listener) {
        if (Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).isConfigured()) {
            CloudCause cloudCause = getCloudCause(run);
            if (cloudCause == null) {
                cloudCause = new CloudCause();
            }

            AbstractJenkinsStatus status = null;
            if (run instanceof WorkflowRun) {
                status = new JenkinsPipelineStatus((WorkflowRun)run, cloudCause, null, listener, true, false);
            } else {
                status = new JenkinsJobStatus(run, cloudCause, null, listener, true, false);
            }
            status.setRunStatus(true);
            JSONObject statusUpdate = status.generate(false);
            CloudPublisher.uploadJobStatus(statusUpdate);
        }
    }

    @Override
    public void onCompleted(Run run, TaskListener listener) {
        if (Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).isConfigured()) {
            CloudCause cloudCause = getCloudCause(run);
            if (cloudCause == null) {
                cloudCause = new CloudCause();
            }

            AbstractJenkinsStatus status = null;
            if (run instanceof WorkflowRun) {
                status = new JenkinsPipelineStatus((WorkflowRun)run, cloudCause, null, listener, false, false);
            } else {
                status = new JenkinsJobStatus(run, cloudCause, null, listener, false, false);
            }
            status.setRunStatus(true);
            JSONObject statusUpdate = status.generate(true);
            CloudPublisher.uploadJobStatus(statusUpdate);
        }
    }

    private CloudCause getCloudCause(Run run) {
        List<Cause> causes = run.getCauses();

        for(Cause cause : causes) {
            if (cause instanceof CloudCause ) {
                return (CloudCause)cause;
            }
        }

        return null;
    }
}
