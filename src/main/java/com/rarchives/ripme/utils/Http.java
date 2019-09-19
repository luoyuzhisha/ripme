package com.rarchives.ripme.utils;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.rarchives.ripme.ripper.AbstractRipper;

/**
 * Wrapper around the Jsoup connection methods.
 *
 * Benefit is retry logic.
 */
public class Http {

    private static final int    TIMEOUT = Utils.getConfigInteger("page.timeout", 5 * 1000);
    private static final Logger logger  = Logger.getLogger(Http.class);

    private int retries;
    private String url;
    private Connection connection;

    // Constructors
    public Http(String url) {
        this.url = url;
        defaultSettings();
    }
    private Http(URL url) {
        this.url = url.toExternalForm();
        defaultSettings();
    }

    public static Http url(String url) {
        return new Http(url);
    }
    public static Http url(URL url) {
        return new Http(url);
    }

    private void defaultSettings() {
        this.retries = Utils.getConfigInteger("download.retries", 1);
        connection = Jsoup.connect(this.url);
        connection.userAgent(AbstractRipper.USER_AGENT);
        connection.method(Method.GET);
        connection.timeout(TIMEOUT);
        connection.maxBodySize(0);
        // set proxy host and port
        if ("https".equals(getProtocol(url))) {
            setProxyForHttps();
        } else {
            logger.debug("url is not https, not set proxy for jsoup connection");
        }
    }

    /**
     * set proxy host and port for https Jsoup
     * other connects set proxy at com.rarchives.ripme.App.main(String[])
     * set user name and password at com.rarchives.ripme.App.main(String[])
     */
    private void setProxyForHttps() {
        logger.debug("url is https, set proxy for jsoup connection");
        String sockProxy = Utils.getConfigString("proxy.https", null);
        if (StringUtils.isNotBlank(sockProxy)) {
            if (sockProxy.lastIndexOf("@") != -1) {
                sockProxy = sockProxy.substring(sockProxy.lastIndexOf("@") + 1);
            }
            String[] urlAndPort = sockProxy.split(":");
            if(urlAndPort.length == 2) {
                Integer port = Integer.valueOf(urlAndPort[1].trim());
                connection.proxy(urlAndPort[0].trim(), port);
            }
        }
    }
    // Setters
    public Http timeout(int timeout) {
        connection.timeout(timeout);
        return this;
    }
    public Http ignoreContentType() {
        connection.ignoreContentType(true);
        return this;
    }
    public Http referrer(String ref)  {
        connection.referrer(ref);
        return this;
    }
    public Http referrer(URL ref) {
        return referrer(ref.toExternalForm());
    }
    public Http userAgent(String ua)  {
        connection.userAgent(ua);
        return this;
    }
    public Http retries(int tries) {
        this.retries = tries;
        return this;
    }
    public Http header(String name, String value) {
        connection.header(name,  value);
        return this;
    }
    public Http cookies(Map<String,String> cookies) {
        connection.cookies(cookies);
        return this;
    }
    public Http data(Map<String,String> data) {
        connection.data(data);
        return this;
    }
    public Http data(String name, String value) {
        Map<String,String> data = new HashMap<>();
        data.put(name, value);
        return data(data);
    }
    public Http method(Method method) {
        connection.method(method);
        return this;
    }

    // Getters
    public Connection connection() {
        return connection;
    }
    public Document get() throws IOException {
        connection.method(Method.GET);
        return response().parse();
    }

    public Document post() throws IOException {
        connection.method(Method.POST);
        return response().parse();
    }

    public JSONObject getJSON() throws IOException {
        ignoreContentType();
        String jsonString = response().body();
        return new JSONObject(jsonString);
    }

    public Response response() throws IOException {
        Response response = null;
        IOException lastException = null;
        int retries = this.retries;
        while (--retries >= 0) {
            try {
                response = connection.execute();
                return response;
            } catch (IOException e) {
                logger.warn("Error while loading " + url, e);
                lastException = e;
            }
        }
        throw new IOException("Failed to load " + url + " after " + this.retries + " attempts", lastException);
    }
    
    private static String getProtocol(String url) {
        if (StringUtils.isBlank(url)) {
            // default is http
            return "http";
        }
        if (url.trim().toLowerCase().startsWith("https")) {
            return "https";
        } else {
            return "http";
        }
    }
}
