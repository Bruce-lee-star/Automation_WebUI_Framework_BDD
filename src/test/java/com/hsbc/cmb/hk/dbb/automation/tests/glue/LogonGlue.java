package com.hsbc.cmb.hk.dbb.automation.tests.glue;

import com.hsbc.cmb.hk.dbb.automation.tests.steps.LoginSteps;
import io.cucumber.java.en.Given;
import net.serenitybdd.annotations.Steps;

public class LogonGlue {
    @Steps
    private LoginSteps loginSteps;

    @Given("logon DBB {string} environment as user {string}")
    public void logonDBBEnvironmentAsUserGlue(String env, String username) {
        loginSteps.logonDBBEnvironmentAsUser(env, username);
    }
}
