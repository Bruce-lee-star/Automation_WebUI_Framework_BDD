package com.hsbc.cmb.hk.dbb.automation.tests.api.steps;

import com.hsbc.cmb.hk.dbb.automation.framework.api.core.step.BaseStep;
import io.cucumber.java.en.When;
import net.serenitybdd.core.steps.UIInteractionSteps;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Request Steps
 * Handles sending HTTP requests
 */
public class RequestSteps extends UIInteractionSteps {

    @Autowired
    private BaseStep baseStep;

    @When("I send GET request")
    public void sendGetRequest() {
        baseStep.getResource();
    }

    @When("I send POST request")
    public void sendPostRequest() {
        baseStep.postPayload();
    }

    @When("I send PUT request")
    public void sendPutRequest() {
        baseStep.putPayload();
    }

    @When("I send PATCH request")
    public void sendPatchRequest() {
        baseStep.patchPayload();
    }

    @When("I send DELETE request")
    public void sendDeleteRequest() {
        baseStep.deleteResource();
    }

    @When("I send {string} request")
    public void sendRequest(String method) {
        String upperMethod = method.toUpperCase();
        switch (upperMethod) {
            case "GET":
                baseStep.getResource();
                break;
            case "POST":
                baseStep.postPayload();
                break;
            case "PUT":
                baseStep.putPayload();
                break;
            case "PATCH":
                baseStep.patchPayload();
                break;
            case "DELETE":
                baseStep.deleteResource();
                break;
            default:
                throw new RuntimeException("Unsupported HTTP method: " + method);
        }
    }
}
