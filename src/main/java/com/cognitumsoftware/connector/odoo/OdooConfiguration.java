package com.cognitumsoftware.connector.odoo;

import org.apache.commons.lang3.StringUtils;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;

import java.util.function.Predicate;

public class OdooConfiguration extends AbstractConfiguration {

    private String url;
    private String database;
    private String username;
    private GuardedString password;

    public OdooConfiguration() {
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

    @ConfigurationProperty(displayMessageKey = "odoo.config.url", helpMessageKey = "odoo.config.url.help", order = 1, required = true)
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = StringUtils.removeEnd(url, "/");
    }

    @ConfigurationProperty(displayMessageKey = "odoo.config.database", helpMessageKey = "odoo.config.database.help", order = 2, required = true)
    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    @ConfigurationProperty(displayMessageKey = "odoo.config.username", helpMessageKey = "odoo.config.username.help", order = 3, required = true)
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @ConfigurationProperty(displayMessageKey = "odoo.config.password", helpMessageKey = "odoo.config.password.help", order = 4, required = true)
    public GuardedString getPassword() {
        return password;
    }

    public void setPassword(GuardedString password) {
        this.password = password;
    }

}

