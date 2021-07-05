package com.ibm.devops.connect.Endpoints;

public class EndpointManager {

    // TODO: Make configurable at build time or otherwise
    private static String profile = "Velocity";
    //private static String profile = "YS1";

    private IEndpoints endpointProvider;

    public EndpointManager() {
        endpointProvider = new EndpointsVelocity();
    }

    public String getSyncApiEndpoint(int instanceNum) {
        return endpointProvider.getSyncApiEndpoint(instanceNum);
    }

    public String getGraphqlApiEndpoint(int instanceNum) {
        return endpointProvider.getGraphqlApiEndpoint(instanceNum);
    }

    public String getPipelinesEndpoint(int instanceNum) {
        return endpointProvider.getPipelinesEndpoint(instanceNum);
    }

    public String getQualityDataEndpoint(int instanceNum) {
        return endpointProvider.getQualityDataEndpoint(instanceNum);
    }

    public String getQualityDataRawEndpoint(int instanceNum) {
        return endpointProvider.getQualityDataRawEndpoint(instanceNum);
    }

    public String getSyncApiEndpoint(String baseUrl) {
        return endpointProvider.getSyncApiEndpoint(baseUrl);
    }

    public String getReleaseEvensApiEndpoint(int instanceNum) {
        return endpointProvider.getReleaseEvensApiEndpoint(instanceNum);
    }

    public String getDotsEndpoint(int instanceNum) {
        return endpointProvider.getDotsEndpoint(instanceNum);
    }

    public String getSyncStoreEndpoint(int instanceNum) {
        return endpointProvider.getSyncStoreEndpoint(instanceNum);
    }

    public String getConnectEndpoint(int instanceNum) {
        return endpointProvider.getConnectEndpoint(instanceNum);
    }

    public String getVelocityHostname(int instanceNum) {
        return endpointProvider.getVelocityHostname(instanceNum);
    }
}