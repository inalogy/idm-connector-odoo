package com.cognitumsoftware.connector.odoo;

import org.apache.commons.lang3.StringUtils;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;

import java.util.function.Predicate;

/**
 * Basic configuration of the connector like connection properties, authentication.
 */
public class OdooConfiguration extends AbstractConfiguration {

    private String url;
    private String database;
    private String username;
    private GuardedString password;
    private String retrieveModels;
    private String expandModels;

    public OdooConfiguration() {
    }

    public OdooConfiguration(OdooConfiguration other) {
        this.url = other.url;
        this.database = other.database;
        this.username = other.username;
        this.password = other.password;
        this.retrieveModels = other.retrieveModels;
        this.expandModels = other.expandModels;
    }

    private void required(String fieldName, String field) {
        required(fieldName, field, StringUtils::isNoneEmpty);
    }

    private <T> void required(String fieldName, T field, Predicate<T> presentTester) {
        if (!presentTester.test(field)) {
            throw new ConfigurationException("Configuration Property " + fieldName + " is required");
        }
    }

    @Override
    public void validate() {
        required("url", url);
        required("database", database);
        required("username", username);
        required("password", password, p -> p != null && !new GuardedString().equals(p));
    }

    @ConfigurationProperty(
            displayMessageKey = "odoo.config.url",
            helpMessageKey = "odoo.config.url.help",
            groupMessageKey = "odoo.config.group.basic",
            order = 1,
            required = true)
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = StringUtils.removeEnd(url, "/");
    }

    @ConfigurationProperty(
            displayMessageKey = "odoo.config.database",
            helpMessageKey = "odoo.config.database.help",
            groupMessageKey = "odoo.config.group.basic",
            order = 2,
            required = true)
    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    @ConfigurationProperty(
            displayMessageKey = "odoo.config.username",
            helpMessageKey = "odoo.config.username.help",
            groupMessageKey = "odoo.config.group.basic",
            order = 3,
            required = true)
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @ConfigurationProperty(
            displayMessageKey = "odoo.config.password",
            helpMessageKey = "odoo.config.password.help",
            groupMessageKey = "odoo.config.group.basic",
            order = 4,
            required = true,
            confidential = true)
    public GuardedString getPassword() {
        return password;
    }

    public void setPassword(GuardedString password) {
        this.password = password;
    }

    @ConfigurationProperty(
            displayMessageKey = "odoo.config.models.retrieve",
            helpMessageKey = "odoo.config.models.retrieve.help",
            groupMessageKey = "odoo.config.group.schema",
            order = 10)
    public String getRetrieveModels() {
        return retrieveModels;
    }

    public void setRetrieveModels(String retrieveModels) {
        this.retrieveModels = retrieveModels;
    }

    @ConfigurationProperty(
            displayMessageKey = "odoo.config.models.expand",
            helpMessageKey = "odoo.config.models.expand.help",
            groupMessageKey = "odoo.config.group.schema",
            order = 10)
    public String getExpandModels() {
        return expandModels;
    }

    public void setExpandModels(String expandModels) {
        this.expandModels = expandModels;
    }

}

