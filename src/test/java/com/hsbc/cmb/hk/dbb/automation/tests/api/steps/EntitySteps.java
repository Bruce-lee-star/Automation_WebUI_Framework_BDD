package com.hsbc.cmb.hk.dbb.automation.tests.api.steps;

import com.hsbc.cmb.hk.dbb.automation.framework.api.core.services.TestServices;
import com.hsbc.cmb.hk.dbb.automation.framework.api.core.step.BaseStep;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import net.serenitybdd.core.steps.UIInteractionSteps;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Entity Initialization Steps
 * Handles entity building and initialization
 */
public class EntitySteps extends UIInteractionSteps {

    @Autowired
    private BaseStep baseStep;

    @Given("an entity")
    public void buildEntity() {
        this.baseStep = TestServices.initialize().baseStep();
    }

    @Given("an entity with {string}")
    public void buildEntity(String entityName) {
        this.baseStep = TestServices.initialize().withEntity(entityName).baseStep();
    }

    @Given("an entity {string} as env {string}")
    public void buildEntity(String entityName, String env) {
        this.baseStep = TestServices.initialize().withEntity(entityName).withEnv(env).baseStep();
    }

    @Given("endpoint {string} with {string} method")
    public void loadEndpointConfig(String endpointName, String method) {
        baseStep.loadEndpointConfig(endpointName, method);
    }

    @When("I send {string} request to {string} endpoint")
    public void sendRequestWithEndpoint(String method, String endpointName) {
        // Load endpoint configuration
        boolean loaded = baseStep.loadEndpointConfig(endpointName, method);
        if (!loaded) {
            throw new RuntimeException("Failed to load endpoint configuration: " + method + " " + endpointName);
        }

        // Send request based on method
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
