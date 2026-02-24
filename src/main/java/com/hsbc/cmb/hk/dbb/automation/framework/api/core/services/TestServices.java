package com.hsbc.cmb.hk.dbb.automation.framework.api.core.services;

import com.hsbc.cmb.hk.dbb.automation.framework.api.core.step.BaseStep;
import com.hsbc.cmb.hk.dbb.automation.framework.api.core.step.BaseStepFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TestServices provides centralized management for API test initialization with chainable calls.
 * <p>
 * This is the ONLY way to create BaseStep instances. Direct instantiation of BaseStep is not allowed.
 * <p>
 * Example usage:
 * {@code BaseStep baseStep = TestServices.initialize().baseStep();}
 * {@code BaseStep baseStep = TestServices.initialize().withEntity("petstore").baseStep();}
 * {@code BaseStep baseStep = TestServices.initialize().withEntity("petstore").withEnv("dev").baseStep();}
 * </p>
 */
public class TestServices {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestServices.class);
    private static TestServices instance;
    private String entityName; // Store the entity name to load
    private String env; // Store the environment name

    // Private constructor - singleton pattern
    private TestServices() {}

    /**
     * Initialize TestServices instance
     * @return TestServices instance for chainable calls
     */
    public static TestServices initialize() {
        if (instance == null) {
            instance = new TestServices();
        }
        return instance;
    }

    /**
     * Set the entity name to load configuration for
     * Configuration will be loaded from {entityName}.conf or {entityName}.properties
     * @param entityName name of the entity/configuration
     * @return this instance for chainable calls
     */
    public TestServices withEntity(String entityName) {
        this.entityName = entityName;
        return this;
    }

    /**
     * Set the environment for the entity
     * Environment-specific configuration will override base configuration
     * @param env environment name (e.g., "dev", "test", "prod")
     * @return this instance for chainable calls
     */
    public TestServices withEnv(String env) {
        this.env = env;
        return this;
    }

    /**
     * Create a BaseStep instance.
     * - If entityName is set, loads configuration from {entityName}.conf or {entityName}.properties
     * - If env is set, environment-specific configuration will be applied
     * - If entityName is not set, creates a BaseStep with null entity (for dynamic configuration)
     * <p>
     * Note: This is the ONLY way to create BaseStep instances. Direct instantiation is not allowed.
     *
     * @return BaseStep instance
     */
    public BaseStep baseStep() {
        BaseStep baseStep;
        if (entityName != null && !entityName.trim().isEmpty()) {
            // Create BaseStep with configured entity
            LOGGER.info("Creating BaseStep with entity: {}, env: {}", entityName, env);
            baseStep = BaseStepFactory.createWithEntity(entityName.trim(), env);
        } else {
            // Create BaseStep with null entity (for dynamic configuration)
            LOGGER.info("Creating BaseStep with null entity (dynamic configuration)");
            baseStep = BaseStepFactory.createWithNullEntity();
        }
        return baseStep;
    }
}