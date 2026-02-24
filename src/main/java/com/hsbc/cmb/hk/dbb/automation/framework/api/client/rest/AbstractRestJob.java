package com.hsbc.cmb.hk.dbb.automation.framework.api.client.rest;

import com.hsbc.cmb.hk.dbb.automation.framework.api.config.ConfigProvider;
import com.hsbc.cmb.hk.dbb.automation.framework.api.config.FrameworkConfig;
import com.hsbc.cmb.hk.dbb.automation.framework.api.core.entity.Entity;
import com.hsbc.cmb.hk.dbb.automation.framework.api.domain.enums.ConfigKeys;
import com.typesafe.config.Config;
import net.serenitybdd.rest.SerenityRest;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.ValidatableResponse;

import java.util.Optional;

public abstract class AbstractRestJob {

    private static RestAssuredConfig restAssuredConfig;

    private ValidatableResponse validatableResponse;

    static {
        initializeRestAssuredConfig();
        SerenityRest.useRelaxedHTTPSValidation();
    }

    public abstract void perform(Entity entity);

    public static RestAssuredConfig getRestAssuredConfig() {
        return restAssuredConfig;
    }

    public static void setRestAssuredConfig(RestAssuredConfig restAssuredConfig) {
        AbstractRestJob.restAssuredConfig = restAssuredConfig;
    }

    public ValidatableResponse getValidatableResponse() {
        return validatableResponse;
    }

    public void setValidatableResponse(ValidatableResponse validatableResponse) {
        this.validatableResponse = validatableResponse;
    }

    private static void initializeRestAssuredConfig() {
        final Config config = ConfigProvider.getConfig();
        int httpConnectTimeout;
        int httpSocketTimeout;

        // Priority: System Property > FrameworkConfig > Default value
        Optional<String> opt = Optional.ofNullable(System.getProperty(ConfigKeys.HTTP_CONNECTION_TIMEOUT.toString()));
        httpConnectTimeout = opt
            .map(Integer::parseInt)
            .orElse(FrameworkConfig.getConnectionTimeout());

        opt = Optional.ofNullable(System.getProperty(ConfigKeys.HTTP_SOCKET_TIMEOUT.toString()));
        httpSocketTimeout = opt
            .map(Integer::parseInt)
            .orElse(FrameworkConfig.getSocketTimeout());

        final RestAssuredConfig restAssuredConfig = RestAssuredConfig.config()
                .httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", httpConnectTimeout)
                        .setParam("http.socket.timeout", httpSocketTimeout));
        setRestAssuredConfig(restAssuredConfig);
    }
}
