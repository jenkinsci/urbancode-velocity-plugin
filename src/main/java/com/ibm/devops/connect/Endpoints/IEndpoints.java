package com.ibm.devops.connect.Endpoints;

public interface IEndpoints {

    public String getSyncApiEndpoint(int instanceNum);

    public String getGraphqlApiEndpoint(int instanceNum);

    public String getPipelinesEndpoint(int instanceNum);

    public String getSyncApiEndpoint(String baseUrl);

    public String getSyncStoreEndpoint(int instanceNum);

    public String getConnectEndpoint(int instanceNum);

    public String getQualityDataEndpoint(int instanceNum);

    public String getQualityDataRawEndpoint(int instanceNum);

    public String getVelocityHostname(int instanceNum);

    public String getReleaseEvensApiEndpoint(int instanceNum);

    public String getDotsEndpoint(int instanceNum);
}