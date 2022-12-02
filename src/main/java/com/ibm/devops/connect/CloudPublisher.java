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

import net.sf.json.JSONObject;
import net.sf.json.JSONArray;

import com.google.gson.*;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.message.AbstractHttpMessage;
import org.apache.http.entity.StringEntity;
import org.apache.http.HttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.StatusLine;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;

import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.List;
import java.security.NoSuchAlgorithmException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import javax.net.ssl.SSLContext;

import com.ibm.devops.connect.Endpoints.EndpointManager;

import org.apache.http.HttpEntity;
import java.util.*;

public class CloudPublisher {
    public static final Logger log = LoggerFactory.getLogger(CloudPublisher.class);

    private final static String JENKINS_JOB_ENDPOINT_URL = "api/v1/jenkins/jobs";
    private final static String JENKINS_JOB_STATUS_ENDPOINT_URL = "api/v1/jenkins/jobStatus";
    private final static String JENKINS_TEST_CONNECTION_URL = "api/v1/jenkins/testConnection";
    private final static String BUILD_UPLOAD_URL = "api/v1/builds";
    private final static String DEPLOYMENT_UPLOAD_URL = "api/v1/deployments";

    private static CloseableHttpClient httpClient;
    private static CloseableHttpAsyncClient asyncHttpClient;
    private static Boolean acceptAllCerts = true;
    private static int requestTimeoutSeconds = 90;

    public static void ensureHttpClientInitialized() {
        if (httpClient == null) {
            httpClient = HttpClients.createDefault();
            if (acceptAllCerts) {
                try {
                    SSLContextBuilder builder = new SSLContextBuilder();
                    builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
                    SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build(),
                            new AllowAllHostnameVerifier());
                    RequestConfig config = RequestConfig.custom()
                            .setConnectTimeout(requestTimeoutSeconds * 1000)
                            .setConnectionRequestTimeout(requestTimeoutSeconds * 1000)
                            .setSocketTimeout(requestTimeoutSeconds * 1000).build();
                    httpClient = HttpClients.custom().setSSLSocketFactory(sslsf).setDefaultRequestConfig(config)
                            .build();
                } catch (NoSuchAlgorithmException nsae) {
                    nsae.printStackTrace();
                } catch (KeyManagementException kme) {
                    kme.printStackTrace();
                } catch (KeyStoreException kse) {
                    kse.printStackTrace();
                }
            }
        }
    }

    public static void ensureAsyncHttpClientInitialized() {
        if (asyncHttpClient == null) {
            asyncHttpClient = HttpAsyncClients.createDefault();
            if (acceptAllCerts) {
                try {
                    TrustStrategy acceptingTrustStrategy = new TrustStrategy() {
                        public boolean isTrusted(X509Certificate[] certificate, String authType) {
                            return true;
                        }
                    };
                    SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy)
                            .build();

                    asyncHttpClient = HttpAsyncClients.custom()
                            .setSSLHostnameVerifier(SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER)
                            .setSSLContext(sslContext)
                            .disableCookieManagement()
                            .build();
                } catch (NoSuchAlgorithmException nsae) {
                    nsae.printStackTrace();
                } catch (KeyManagementException kme) {
                    kme.printStackTrace();
                } catch (KeyStoreException kse) {
                    kse.printStackTrace();
                }
            }
            asyncHttpClient.start();
        }
    }

    private static String getSyncApiUrl(Entry entry) {
        EndpointManager em = new EndpointManager();
        return em.getSyncApiEndpoint(entry);
    }

    private static String getSyncApiUrl(String baseUrl) {
        EndpointManager em = new EndpointManager();
        return em.getSyncApiEndpoint(baseUrl);
    }

    public static String getQualityDataUrl(Entry entry) {
        EndpointManager em = new EndpointManager();
        return em.getQualityDataEndpoint(entry);
    }

    private static String getQualityDataRawUrl(Entry entry) {
        EndpointManager em = new EndpointManager();
        return em.getQualityDataRawEndpoint(entry);
    }

    private static String getBuildUploadUrl(Entry entry) {
        EndpointManager em = new EndpointManager();
        return em.getReleaseEvensApiEndpoint(entry) + BUILD_UPLOAD_URL;
    }

    private static String getDeploymentUploadUrl(Entry entry) {
        EndpointManager em = new EndpointManager();
        return em.getReleaseEvensApiEndpoint(entry) + DEPLOYMENT_UPLOAD_URL;
    }

    private static String getDotsUrl(Entry entry) {
        EndpointManager em = new EndpointManager();
        return em.getDotsEndpoint(entry);
    }

    private static String getGraphqlUrl(Entry entry) {
        EndpointManager em = new EndpointManager();
        return em.getGraphqlApiEndpoint(entry);
    }

    /**
     * Upload the build information to Sync API - API V1.
     */
    public static void uploadJobInfo(JSONObject jobJson, Entry entry) {
        String url = CloudPublisher.getSyncApiUrl(entry) + JENKINS_JOB_ENDPOINT_URL;

        JSONArray payload = new JSONArray();
        payload.add(jobJson);

        System.out.println("SENDING JOBS TO: ");
        System.out.println(url);
        System.out.println(jobJson.toString());

        CloudPublisher.postToSyncAPI(url, payload.toString(), entry);
    }

    public static void uploadJobStatus(JSONObject jobStatus, Entry entry) {
        String url = CloudPublisher.getSyncApiUrl(entry) + JENKINS_JOB_STATUS_ENDPOINT_URL;
        System.out.println("sending");
        CloudPublisher.postToSyncAPI(url, jobStatus.toString(), entry);
    }

    public static String uploadBuild(String payload, Entry entry) throws Exception {
        CloudPublisher.ensureHttpClientInitialized();
        String resStr = "";
        String url = CloudPublisher.getBuildUploadUrl(entry);
        CloseableHttpResponse response = null;
        String logPrefix = "[UrbanCode Velocity " + entry.getBaseUrl() + "] CloudPublisher#uploadBuild";

        try {
            HttpPost postMethod = new HttpPost(url);
            attachHeaders(postMethod, entry);
            postMethod.setHeader("Content-Type", "application/json");
            postMethod.setHeader("Authorization", "UserAccessKey " + entry.getApiToken());
            postMethod.setEntity(new StringEntity(payload));

            response = httpClient.execute(postMethod);
            resStr = EntityUtils.toString(response.getEntity());
            if (response.getStatusLine().toString().contains("200")) {
                log.info(logPrefix + " Uploaded Build successfully");
            } else {
                throw new Exception(logPrefix + "Bad response code when uploading Build: " + response.getStatusLine()
                        + " - " + resStr);
            }
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (Exception e) {
                    log.error(logPrefix + " Could not close uploadBuild response");
                }
            }
        }
        return resStr;
    }

    public static String uploadDeployment(String payload, Entry entry) throws Exception {
        CloudPublisher.ensureHttpClientInitialized();
        String resStr = "";
        String url = CloudPublisher.getDeploymentUploadUrl(entry);
        CloseableHttpResponse response = null;
        String logPrefix = "[UrbanCode Velocity " + entry.getBaseUrl() + "] CloudPublisher#uploadDeployment";

        try {
            HttpPost postMethod = new HttpPost(url);
            attachHeaders(postMethod, entry);
            postMethod.setHeader("Content-Type", "application/json");
            postMethod.setHeader("Authorization", "UserAccessKey " + entry.getApiToken());
            postMethod.setEntity(new StringEntity(payload));

            response = httpClient.execute(postMethod);
            resStr = EntityUtils.toString(response.getEntity());
            if (response.getStatusLine().toString().contains("200")) {
                log.info(logPrefix + " Uploaded Deployment successfully");
            } else {
                throw new Exception(logPrefix + "Bad response code when uploading Deployment: "
                        + response.getStatusLine() + " - " + resStr);
            }
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (Exception e) {
                    log.error(logPrefix + " Could not close uploadDeployment response");
                }
            }
        }
        return resStr;
    }

    public static List<Entry> getFinalEntriesList(String jobString, String instanceBaseUrl, List<Entry> entries) {
        List<Entry> finalEntriesList = new ArrayList<>();
        if (StringUtils.isNotEmpty(instanceBaseUrl)) {
            for (Entry entry : entries) {
                if (removeTrailingSlash(instanceBaseUrl).equals(removeTrailingSlash(entry.getBaseUrl()))) {
                    finalEntriesList.add(entry);
                    break;
                }
            }
            if (finalEntriesList.size() == 0) {
                log.info("Using default: " + jobString + "to All UCV Instances.");
                return entries;
            }
        } else {
            log.info("UCV Instance BaseUrl is not provided for " + jobString + ".  Using default: " + jobString
                    + "to All UCV Instances.");
            return entries;
        }
        return finalEntriesList;
    }

    public static String checkGate(Entry entry, String pipelineId, String stageName, String versionId)
            throws Exception {
        CloudPublisher.ensureHttpClientInitialized();
        String resStr = "";
        String url = CloudPublisher.getDotsUrl(entry);
        CloseableHttpResponse response = null;
        String logPrefix = "[UrbanCode Velocity " + entry.getBaseUrl() + "] CloudPublisher#checkGate";

        try {
            URIBuilder builder = new URIBuilder(url);
            builder.setParameter("pipelineId", pipelineId);
            builder.setParameter("stageName", stageName);
            builder.setParameter("versionId", versionId);
            URI uri = builder.build();
            System.out.println("TEST gates url: " + uri.toString());
            HttpGet getMethod = new HttpGet(uri);
            attachHeaders(getMethod, entry);
            getMethod.setHeader("Accept", "application/json");
            getMethod.setHeader("Authorization", "UserAccessKey " + entry.getApiToken());

            response = httpClient.execute(getMethod);
            resStr = EntityUtils.toString(response.getEntity());
            if (response.getStatusLine().toString().contains("200")) {
                log.info(logPrefix + " Gates Checked Successfully");
            } else {
                throw new Exception(logPrefix + " Bad response code when uploading Deployment: "
                        + response.getStatusLine() + " - " + resStr);
            }
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (Exception e) {
                    log.error(logPrefix + " Could not close uploadDeployment response");
                }
            }
        }
        return resStr;
    }

    public static Boolean isPipeline(Entry entry, String pipelineId) throws Exception {
        CloudPublisher.ensureHttpClientInitialized();
        String resStr = "";
        String url = CloudPublisher.getGraphqlUrl(entry);
        CloseableHttpResponse response = null;
        try {
            URIBuilder builder = new URIBuilder(url);
            builder.setParameter("query", "{pipelineById(pipelineId: \"" + pipelineId + "\"){_id}}");
            URI uri = builder.build();
            System.out.println("TEST gates url: " + uri.toString());
            HttpGet getMethod = new HttpGet(uri);
            attachHeaders(getMethod, entry);
            getMethod.setHeader("Accept", "application/json");
            getMethod.setHeader("Authorization", "UserAccessKey " + entry.getApiToken());

            response = httpClient.execute(getMethod);
            resStr = EntityUtils.toString(response.getEntity());
            log.info(resStr);
            if (resStr.contains(pipelineId)) {
                return true;
            } else {
                return false;
            }
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (Exception e) {
                    log.error("[UrbanCode Velocity " + entry.getBaseUrl()
                            + "] CloudPublisher#isPipeline Could not close isPipeline response");
                }
            }
        }
    }

    public static boolean uploadQualityData(HttpEntity entity, String url, String userAccessKey) throws Exception {
        CloudPublisher.ensureHttpClientInitialized();
        String resStr = "";
        CloseableHttpResponse response = null;
        boolean success = false;
        String logPrefix = "[UrbanCode Velocity] CloudPublisher#uploadQualityData";

        try {
            HttpPost postMethod = new HttpPost(url);
            postMethod.setHeader("Authorization", "UserAccessKey " + userAccessKey);
            postMethod.setEntity(entity);

            response = httpClient.execute(postMethod);
            resStr = EntityUtils.toString(response.getEntity());
            if (response.getStatusLine().toString().contains("200")) {
                log.info(logPrefix + " Upload Quality Data successfully");
                success = true;
            }
            return success;
        } finally {
            StatusLine status = null;
            if (response != null) {
                status = response.getStatusLine();
                try {
                    response.close();
                } catch (Exception e) {
                    log.error(logPrefix + " Could not close uploadQualityData response");
                }
            }
            if (!success) {
                throw new Exception(
                        logPrefix + " Bad response code when uploading Quality Data: " + status + " - " + resStr);
            }
        }
    }

    public static void uploadQualityDataRaw(Entry entry, String payload) throws Exception {
        CloudPublisher.ensureHttpClientInitialized();
        String resStr = "";
        String url = CloudPublisher.getQualityDataRawUrl(entry);
        CloseableHttpResponse response = null;
        String logPrefix = "[UrbanCode Velocity " + entry.getBaseUrl() + "] CloudPublisher#uploadMetricDataRaw";

        try {
            HttpPost postMethod = new HttpPost(url);
            attachHeaders(postMethod, entry);
            postMethod.setHeader("Content-Type", "application/json");
            postMethod.setHeader("Authorization", "UserAccessKey " + entry.getApiToken());
            postMethod.setEntity(new StringEntity(payload));

            response = httpClient.execute(postMethod);
            resStr = EntityUtils.toString(response.getEntity());
            if (response.getStatusLine().toString().contains("200")) {
                log.info(logPrefix + " Uploaded Metric (raw) successfully");
            } else {
                throw new Exception(logPrefix + " Bad response code when uploading Metric (raw): "
                        + response.getStatusLine() + " - " + resStr);
            }
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (Exception e) {
                    log.error(logPrefix + " Could not close uploadQualityDataRaw response");
                }
            }
        }
    }

    private static void attachHeaders(AbstractHttpMessage message, Entry entry) {
        String syncId = entry.getSyncId();
        message.setHeader("sync_token", entry.getSyncToken());
        message.setHeader("sync_id", syncId);
        message.setHeader("instance_type", "JENKINS");
        message.setHeader("instance_id", syncId);
        message.setHeader("integration_id", syncId);

        // Must include both _ and - headers because NGINX services don't pass _ headers
        // by default and the original version of the Velocity services expected the _
        // headers
        message.setHeader("sync-token", entry.getSyncToken());
        message.setHeader("sync-id", syncId);
        message.setHeader("instance-type", "JENKINS");
        message.setHeader("instance-id", syncId);
        message.setHeader("integration-id", syncId);
    }

    private static void postToSyncAPI(String url, String payload, Entry entry) {
        CloudPublisher.ensureAsyncHttpClientInitialized();
        String logPrefix = "[UrbanCode Velocity " + entry.getBaseUrl() + "] CloudPublisher#uploadJobInfo";
        try {
            HttpPost postMethod = new HttpPost(url);
            attachHeaders(postMethod, entry);
            postMethod.setHeader("Content-Type", "application/json");
            StringEntity data = new StringEntity(payload);
            postMethod.setEntity(data);

            asyncHttpClient.execute(postMethod, new FutureCallback<HttpResponse>() {
                public void completed(final HttpResponse response2) {
                    if (response2.getStatusLine().toString().contains("200")) {
                        log.info(logPrefix + " Upload Job Information successfully");
                    } else {
                        log.error(logPrefix + " Error: Upload Job has bad status code, response status "
                                + response2.getStatusLine());
                    }
                    try {
                        EntityUtils.toString(response2.getEntity());
                    } catch (JsonSyntaxException e) {
                        log.error(logPrefix + " Invalid Json response, response: " + response2.getEntity());
                    } catch (IOException e) {
                        log.error(logPrefix + " Input/Output error, response: " + response2.getEntity());
                    }
                }

                public void failed(final Exception ex) {
                    log.error(logPrefix + " Error: Failed to upload Job, response status " + ex.getMessage());
                    ex.printStackTrace();
                    if (ex instanceof IllegalStateException) {
                        log.error(logPrefix + " Please check if you have the access to the configured tenant.");
                    }
                }

                public void cancelled() {
                    log.error(logPrefix + " Error: Upload Job cancelled.");
                }
            });
        } catch (UnsupportedEncodingException e) {
            log.error(logPrefix + "UnsupportedEncodingException trying to post job data", e);
        } catch (Exception e) {
            log.error(logPrefix + "Error trying to post job data", e);
        }
    }

    public static String removeTrailingSlash(String url) {
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public static boolean testConnection(String syncId, String syncToken, String baseUrl) {
        CloudPublisher.ensureHttpClientInitialized();
        String url = getSyncApiUrl(baseUrl) + JENKINS_TEST_CONNECTION_URL;
        CloseableHttpResponse response = null;
        try {
            HttpGet getMethod = new HttpGet(url);
            // postMethod = addProxyInformation(postMethod);
            getMethod.setHeader("sync_token", syncToken);
            getMethod.setHeader("sync_id", syncId);
            getMethod.setHeader("instance_type", "JENKINS");
            getMethod.setHeader("instance_id", syncId);
            getMethod.setHeader("integration_id", syncId);

            // Must include both _ and - headers because NGINX services don't pass _ headers
            // by default and the original version of the Velocity services expected the _
            // headers
            getMethod.setHeader("sync-token", syncToken);
            getMethod.setHeader("sync-id", syncId);
            getMethod.setHeader("instance-type", "JENKINS");
            getMethod.setHeader("instance-id", syncId);
            getMethod.setHeader("integration-id", syncId);

            response = httpClient.execute(getMethod);

            if (response.getStatusLine().toString().contains("200")) {
                // get 200 response
                log.info("Connected to Velocity service successfully for: " + baseUrl);
                return true;
            } else {
                log.warn("Could not authenticate to Velocity Services for: " + baseUrl);
                log.warn(response.toString());
            }
        } catch (IllegalStateException e) {
            log.error("Could not connect to Velocity for: " + baseUrl);
            log.error(e.getMessage());
        } catch (UnsupportedEncodingException e) {
            log.error("Could not connect to Velocity for: " + baseUrl);
            log.error(e.getMessage());
        } catch (ClientProtocolException e) {
            log.error("Could not connect to Velocity for: " + baseUrl);
            log.error(e.getMessage());
        } catch (IOException e) {
            log.error("Could not connect to Velocity for: " + baseUrl);
            log.error(e.getMessage());
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (Exception e) {
                    log.error("Could not close testconnection response for: " + baseUrl);
                }
            }
        }

        return false;
    }

}
