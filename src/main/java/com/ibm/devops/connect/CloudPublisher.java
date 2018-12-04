/*
 <notice>

 Copyright 2016, 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.connect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.devops.connect.DevOpsGlobalConfiguration;

import net.sf.json.JSONObject;
import net.sf.json.JSONArray;

import com.google.gson.*;
import jenkins.model.Jenkins;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.message.AbstractHttpMessage;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.NoSuchAlgorithmException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;

import com.ibm.devops.connect.Endpoints.EndpointManager;

import org.apache.http.HttpEntity;

public class CloudPublisher  {
	public static final Logger log = LoggerFactory.getLogger(CloudPublisher.class);
	private String logPrefix= "[IBM Cloud DevOps] CloudPublisher#";

    private final String JENKINS_JOB_ENDPOINT_URL = "api/v1/jenkins/jobs";
    private final String JENKINS_JOB_STATUS_ENDPOINT_URL = "api/v1/jenkins/jobStatus";
    private final String JENKINS_TEST_CONNECTION_URL = "api/v1/jenkins/testConnection";
    private final String INTEGRATIONS_ENDPOINT_URL = "api/v1/integrations";
    private final String INTEGRATION_ENDPOINT_URL = "api/v1/integrations/{integration_id}";

    private static String BUILD_API_URL = "/organizations/{org_name}/toolchainids/{toolchain_id}/buildartifacts/{build_artifact}/builds";
    private final static String CONTENT_TYPE_JSON = "application/json";
    private final static String CONTENT_TYPE_XML = "application/xml";

    // form fields from UI
    private String applicationName;
    private String orgName;
    private String credentialsId;
    private String toolchainName;

    private String dlmsUrl;
    private PrintStream printStream;
    private File root;
    private static String bluemixToken;
    private static String preCredentials;

    // fields to support jenkins pipeline
    private String result;
    private String gitRepo;
    private String gitBranch;
    private String gitCommit;
    private String username;
    private String password;
    // optional customized build number
    private String buildNumber;

    public CloudPublisher() {
    }

    private String getSyncApiUrl() {
        EndpointManager em = new EndpointManager();
        return em.getSyncApiEndpoint();
    }

    private String getSyncApiUrl(String baseUrl) {
        EndpointManager em = new EndpointManager();
        return em.getSyncApiEndpoint(baseUrl);
    }

    private String getSyncStoreUrl() {
        EndpointManager em = new EndpointManager();
        return em.getSyncStoreEndpoint();
    }

    private String getQualityDataUrl() {
        EndpointManager em = new EndpointManager();
        return em.getQualityDataEndpoint();
    }

    /**
     * Upload the build information to Sync API - API V1.
     */
    public boolean uploadJobInfo(JSONObject jobJson) {
        String url = this.getSyncApiUrl() + JENKINS_JOB_ENDPOINT_URL;

        JSONArray payload = new JSONArray();
        payload.add(jobJson);

        System.out.println("SENDING JOBS TO: ");
        System.out.println(url);
        System.out.println(jobJson.toString());

        return postToSyncAPI(url, payload.toString());
    }

    public boolean uploadJobStatus(JSONObject jobStatus) {

        String url = this.getSyncApiUrl() + JENKINS_JOB_STATUS_ENDPOINT_URL;
        return postToSyncAPI(url, jobStatus.toString());
    }

    public boolean uploadQualityData(HttpEntity entity) throws Exception {
        String localLogPrefix= logPrefix + "uploadQualityData ";

        String resStr = "";

        String url = this.getQualityDataUrl();
        CloseableHttpClient httpClient = HttpClients.createDefault();

        boolean acceptAllCerts = true;

        if (acceptAllCerts) {
            try {
                SSLContextBuilder builder = new SSLContextBuilder();
                builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
                SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
                        builder.build(), new AllowAllHostnameVerifier());
                httpClient = HttpClients.custom().setSSLSocketFactory(sslsf).build();
            } catch (NoSuchAlgorithmException nsae) {
                nsae.printStackTrace();
            } catch (KeyManagementException kme) {
                kme.printStackTrace();
            } catch (KeyStoreException kse) {
                kse.printStackTrace();
            }
        }

        HttpPost postMethod = new HttpPost(url);

        attachHeaders(postMethod);
        postMethod.setEntity(entity);

        CloseableHttpResponse response = httpClient.execute(postMethod);

        resStr = EntityUtils.toString(response.getEntity());
        if (response.getStatusLine().toString().contains("201")) {
            log.info(localLogPrefix + "Upload Quality Data successfully");
            return true;
        } else {
            throw new Exception("Bad response code when uploading Quality Data: " + response.getStatusLine() + " - " + resStr);
        }
    }

    private void attachHeaders(AbstractHttpMessage message) {
        String syncId = Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncId();
        message.setHeader("sync_token", Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncToken());
        message.setHeader("sync_id", syncId);
        message.setHeader("instance_type", "JENKINS");
        message.setHeader("instance_id", syncId);
        message.setHeader("integration_id", syncId);

        // Must include both _ and - headers because NGINX services don't pass _ headers by default and the original version of the Velocity services expected the _ headers
        message.setHeader("sync-token", Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncToken());
        message.setHeader("sync-id", syncId);
        message.setHeader("instance-type", "JENKINS");
        message.setHeader("instance-id", syncId);
        message.setHeader("integration-id", syncId);
    }

    private boolean postToSyncAPI(String url, String payload) {
    	String localLogPrefix= logPrefix + "uploadJobInfo ";

        String resStr = "";

        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();

            boolean acceptAllCerts = true;

            if (acceptAllCerts) {
                try {
                    SSLContextBuilder builder = new SSLContextBuilder();
                    builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
                    SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build(), new AllowAllHostnameVerifier());
                    httpClient = HttpClients.custom().setSSLSocketFactory(sslsf).build();
                } catch (NoSuchAlgorithmException nsae) {
                    nsae.printStackTrace();
                } catch (KeyManagementException kme) {
                    kme.printStackTrace();
                } catch (KeyStoreException kse) {
                    kse.printStackTrace();
                }
            }

            HttpPost postMethod = new HttpPost(url);

            attachHeaders(postMethod);

            postMethod.setHeader("Content-Type", "application/json");

            StringEntity data = new StringEntity(payload);
            postMethod.setEntity(data);

            CloseableHttpResponse response = httpClient.execute(postMethod);

            resStr = EntityUtils.toString(response.getEntity());
            if (response.getStatusLine().toString().contains("200")) {
                // get 200 response
                log.info(localLogPrefix + "Upload Job Information successfully");
                return true;

            } else {
                // if gets error status
                log.error(localLogPrefix + "Error: Failed to upload Job, response status " + response.getStatusLine());
            }
        } catch (JsonSyntaxException e) {
            log.error(localLogPrefix + "Invalid Json response, response: " + resStr);
        } catch (IllegalStateException e) {
            // will be triggered when 403 Forbidden
            try {
                log.error(localLogPrefix + "Please check if you have the access to " + URLEncoder.encode(this.orgName, "UTF-8") + " org");
            } catch (UnsupportedEncodingException e1) {
                e1.printStackTrace();
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean testConnection(String syncId, String syncToken, String baseUrl) {
        String url = this.getSyncApiUrl(baseUrl) + JENKINS_TEST_CONNECTION_URL;
        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();

            boolean acceptAllCerts = true;

            if (acceptAllCerts) {
                try {
                    SSLContextBuilder builder = new SSLContextBuilder();
                    builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
                    SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
                            builder.build(), new AllowAllHostnameVerifier());
                    httpClient = HttpClients.custom().setSSLSocketFactory(sslsf).build();
                } catch (NoSuchAlgorithmException nsae) {
                    nsae.printStackTrace();
                } catch (KeyManagementException kme) {
                    kme.printStackTrace();
                } catch (KeyStoreException kse) {
                    kse.printStackTrace();
                }
            }

            HttpGet getMethod = new HttpGet(url);
            // postMethod = addProxyInformation(postMethod);
            getMethod.setHeader("sync_token", syncToken);
            getMethod.setHeader("sync_id", syncId);
            getMethod.setHeader("instance_type", "JENKINS");
            getMethod.setHeader("instance_id", syncId);
            getMethod.setHeader("integration_id", syncId);

            // Must include both _ and - headers because NGINX services don't pass _ headers by default and the original version of the Velocity services expected the _ headers
            getMethod.setHeader("sync-token", syncToken);
            getMethod.setHeader("sync-id", syncId);
            getMethod.setHeader("instance-type", "JENKINS");
            getMethod.setHeader("instance-id", syncId);
            getMethod.setHeader("integration-id", syncId);

            CloseableHttpResponse response = httpClient.execute(getMethod);
            if (response.getStatusLine().toString().contains("200")) {
                // get 200 response
                log.info("Connected to Velocity service successfully");
                return true;
            } else {
                log.info("Could not authenticate to Velocity Services");
            }
        } catch (IllegalStateException e) {
            log.error("Could not connect to Velocity services");
        } catch (UnsupportedEncodingException e) {
            log.error("Could not connect to Velocity services");
        } catch (ClientProtocolException e) {
            log.error("Could not connect to Velocity services");
        } catch (IOException e) {
            log.error("Could not connect to Velocity services");
        }

        return false;
    }

}
