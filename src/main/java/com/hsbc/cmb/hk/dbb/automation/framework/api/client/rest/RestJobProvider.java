package com.hsbc.cmb.hk.dbb.automation.framework.api.client.rest;

import com.hsbc.cmb.hk.dbb.automation.framework.api.client.AbstractApiJobHelper;
import com.hsbc.cmb.hk.dbb.automation.framework.api.client.rest.impl.*;
import com.hsbc.cmb.hk.dbb.automation.framework.api.config.ConfigProvider;
import com.hsbc.cmb.hk.dbb.automation.framework.api.domain.enums.ConfigKeys;
import io.restassured.response.ValidatableResponse;

public class RestJobProvider extends AbstractApiJobHelper {

    public void postPayload() {
        this.setRestJob(new RestPostJob());
        this.getRestJob().perform(this.getEntity());
    }

    public void getResource() {
        this.setRestJob(new RestGetJob());
        this.getRestJob().perform(this.getEntity());
    }

    public void putPayload() {
        this.setRestJob(new RestPutJob());
        this.getRestJob().perform(this.getEntity());
    }

    public void patchPayload() {
        this.setRestJob(new RestPatchJob());
        this.getRestJob().perform(this.getEntity());
    }

    public void deleteResource() {
        this.setRestJob(new RestDeleteJob());
        this.getRestJob().perform(this.getEntity());
    }

    public String getResponseJson() {
        return this.getEntity().getResponsePayload();
    }

    public ValidatableResponse getValidatableResponse() {
        return this.getEntity().getValidatableResponse();

    }

    public int getResponseCode() {
        return this.getEntity().getResponseCode();
    }

    public String getRequestBody() {
        return this.getEntity().getRequestPayload();
    }

    public void setRequestBody(final String requestBody){
        this.getEntity().setRequestPayload(requestBody);
    }

    public void setEndPoint(final String endPoint){
        if(endPoint.startsWith("/")){
            this.getEntity().setEndpoint(endPoint);
        }else {
            this.getEntity().setEndpoint("/" + endPoint);
        }
    }

    public String getEndPoint(){
        return this.getEntity().getEndpoint();
    }

    public void switchBasePath(final String key){
        final String basePath = ConfigProvider.getConfig(ConfigKeys.API_BASE_PATH.toString()).getString(key);
    }

    public void switchBaseUri(final String key){
        final String basePath = ConfigProvider.getConfig(ConfigKeys.API_BASE_URI.toString()).getString(key);
    }

}
