package com.sheets;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.*;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.sheets.config.SheetsProperties;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;

/** Shared client factory. Only the explicit Quickstart path may open a consent browser. */
@Component
public class GoogleSheetsClientProvider {
    private final SheetsProperties properties;
    public GoogleSheetsClientProvider(SheetsProperties properties) { this.properties = properties; }
    public List<String> scopes() {
        return List.of(properties.isCatalogEnabled() ? SheetsScopes.SPREADSHEETS : SheetsScopes.SPREADSHEETS_READONLY);
    }
    private String credentialKey() { return properties.isCatalogEnabled() ? "catalog-user" : "user"; }
    protected GoogleAuthorizationCodeFlow createFlow() throws IOException, GeneralSecurityException {
        var transport = GoogleNetHttpTransport.newTrustedTransport();
        var json = GsonFactory.getDefaultInstance();
        var resource = new DefaultResourceLoader().getResource(properties.getCredentialsPath());
        GoogleClientSecrets secrets;
        try (var reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            secrets = GoogleClientSecrets.load(json, reader);
        }
        return new GoogleAuthorizationCodeFlow.Builder(transport, json, secrets, scopes())
            .setDataStoreFactory(new FileDataStoreFactory(new File(properties.getTokensDirectory())))
            .setRequestInitializer(this::configureRequest).setAccessType("offline").build();
    }
    public Sheets client() throws IOException, GeneralSecurityException {
        try { return storedClient(); }
        catch (IOException | GeneralSecurityException | RuntimeException failure) {
            throw new IOException("Stored Sheets authorization unavailable or inadequate; use explicit Quickstart authorization");
        }
    }
    private Sheets storedClient() throws IOException, GeneralSecurityException {
        var flow = createFlow();
        var credential = flow.loadCredential(credentialKey());
        if (credential == null) throw new IOException("Stored Sheets authorization missing; use explicit Quickstart authorization");
        if (credential.getAccessToken() == null || credential.getExpiresInSeconds() == null || credential.getExpiresInSeconds() <= 60) {
            if (!credential.refreshToken()) throw new IOException("Stored Sheets authorization cannot refresh");
        }
        verifyGrant(flow, credential);
        return new Sheets.Builder(flow.getTransport(), GsonFactory.getDefaultInstance(), request -> {
            credential.initialize(request); configureRequest(request);
        }).setApplicationName("Qualifica Mais Analitic").build();
    }
    /** Called only by SheetsQuickstart --authorize; never by a Spring/background worker. */
    public void authorizeInteractively() throws IOException, GeneralSecurityException {
        try { renewAuthorization(); }
        catch (IOException | GeneralSecurityException | RuntimeException failure) {
            throw new IOException("Explicit Sheets authorization failed");
        }
    }
    private void renewAuthorization() throws IOException, GeneralSecurityException {
        var flow = createFlow();
        var receiver = createReceiver();
        try {
            var redirect = receiver.getRedirectUri();
            var url = flow.newAuthorizationUrl().setRedirectUri(redirect).set("prompt", "consent");
            openAuthorizationUrl(url.build());
            var response = flow.newTokenRequest(receiver.waitForCode()).setRedirectUri(redirect).execute();
            // Only an explicit new consent replaces this key; readonly tokens remain untouched.
            var credential = flow.createAndStoreCredential(response, credentialKey());
            verifyGrant(flow, credential);
        } finally { receiver.stop(); }
    }
    protected com.google.api.client.extensions.java6.auth.oauth2.VerificationCodeReceiver createReceiver() {
        return new LocalServerReceiver.Builder().setPort(properties.getOauthPort()).build();
    }
    protected void openAuthorizationUrl(String url) throws IOException {
        new AuthorizationCodeInstalledApp.DefaultBrowser().browse(url);
    }
    private void verifyGrant(GoogleAuthorizationCodeFlow flow, Credential credential) throws IOException {
        var url = new GenericUrl("https://oauth2.googleapis.com/tokeninfo");
        url.put("access_token", credential.getAccessToken());
        var request = flow.getTransport().createRequestFactory(this::configureRequest).buildGetRequest(url);
        request.setParser(new JsonObjectParser(GsonFactory.getDefaultInstance()));
        var response = request.execute();
        GenericJson grant;
        try { grant = response.parseAs(GenericJson.class); } finally { response.disconnect(); }
        var granted = new HashSet<>(Arrays.asList(Objects.toString(grant.get("scope"), "").split(" ")));
        if (properties.isCatalogEnabled() ? !granted.contains(SheetsScopes.SPREADSHEETS)
                : !granted.contains(SheetsScopes.SPREADSHEETS_READONLY) && !granted.contains(SheetsScopes.SPREADSHEETS))
            throw new IOException("Stored Sheets authorization lacks required scope");
    }
    private void configureRequest(HttpRequest request) {
        request.setConnectTimeout(boundedTimeout(properties.getConnectTimeoutMs(),10000));
        request.setReadTimeout(boundedTimeout(properties.getReadTimeoutMs(),30000));
        request.setLoggingEnabled(false); request.setCurlLoggingEnabled(false);
        request.setNumberOfRetries(0);
    }
    private int boundedTimeout(int configured, int fallback) {
        return configured > 0 ? Math.min(configured,120000) : fallback;
    }
}
