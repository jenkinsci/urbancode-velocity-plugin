package com.ibm.devops.connect;

import hudson.model.Cause;
import hudson.model.Node;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import net.sf.json.JSONObject;
import net.sf.json.JSONArray;
import com.ibm.devops.connect.Status.DRAData;
import com.ibm.devops.connect.Status.SourceData;

/**
* This is the cause object that is attached to a build if it is started by UrbanCode Velocity.
*/
public class CloudCause extends Cause {

    public enum JobStatus {
        unstarted, started, success, failure
    }

    private String workId;
    private String returnProps;
    private List<String> steps = new ArrayList<String>();

    private SourceData sourceData;
    private DRAData draData;

    private Boolean createdFromCR = false;

    // private ConnectSocket socket;

    public CloudCause(String workId, JSONObject returnProps) {
        this.workId = workId;
        this.returnProps = returnProps.toString();
        this.createdFromCR = true;
    }

    public CloudCause() {
        this.createdFromCR = false;
    }

    @Override
    public String getShortDescription() {
        return "Started due to a request from UrbanCode Velocity. Work Id: " + this.workId;
    }

    public void addStep(String name, String status, String message, boolean isFatal) {
        JSONObject obj = new JSONObject();
        obj.put("name", name);
        obj.put("status", status);
        obj.put("message", message);
        obj.put("isFatal", isFatal);
        steps.add(obj.toString());
    }

    public void setSourceData(SourceData sourceData) {
        this.sourceData = sourceData;
    }

    public SourceData getSourceData() {
        return this.sourceData;
    }

    public void setDRAData(DRAData draData) {
        this.draData = draData;
    }

    public DRAData getDRAData() {
        return this.draData;
    }

    public JSONObject getSourceDataJson() {
        if(this.sourceData == null) {
            return new JSONObject();
        } else {
            return sourceData.toJson();
        }
    }

    public JSONObject getDRADataJson() {
        if(this.draData == null) {
            return new JSONObject();
        } else {
            return draData.toJson();
        }
    }

    public void updateLastStep(String name, String status, String message, boolean isFatal) {
        if (steps.size() == 0) {
            addStep(name, status, message, isFatal);
        } else {
            JSONObject obj = JSONObject.fromObject(steps.get(steps.size() - 1));
            if(name != null) {
                obj.put("name", name);
            }
            obj.put("status", status);
            obj.put("message", message);
            obj.put("isFatal", isFatal);

            steps.set(steps.size() - 1, obj.toString());
        }
    }

    public Boolean isCreatedByCR() {
        return this.createdFromCR;
    }

    public JSONObject getReturnProps() {
        return JSONObject.fromObject(returnProps);
    }

    public JSONArray getStepsArray() {
        JSONArray result = new JSONArray();
        for (String objString : steps) {
            result.add(JSONObject.fromObject(objString));
        }

        return result;
    }
}