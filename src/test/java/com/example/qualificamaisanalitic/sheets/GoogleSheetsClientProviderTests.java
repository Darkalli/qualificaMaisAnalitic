package com.example.qualificamaisanalitic.sheets;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.http.*;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.*;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.sheets.GoogleSheetsClientProvider;
import com.sheets.config.SheetsProperties;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleSheetsClientProviderTests {
    @Test void explicitAuthorizationAlwaysRenewsConsentWithoutDeletingExistingTokens() throws Exception {
        var p = new SheetsProperties(); p.setCatalogEnabled(true);
        var flow = mock(GoogleAuthorizationCodeFlow.class);
        var old = mock(Credential.class); var fresh = mock(Credential.class);
        when(flow.loadCredential("catalog-user")).thenReturn(old);
        when(old.getAccessToken()).thenReturn("test-old"); when(old.getRefreshToken()).thenReturn("test-refresh");
        var url = new com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl("test-client","http://localhost:8888",List.of(SheetsScopes.SPREADSHEETS));
        when(flow.newAuthorizationUrl()).thenReturn(url);
        var receiver = mock(com.google.api.client.extensions.java6.auth.oauth2.VerificationCodeReceiver.class);
        when(receiver.getRedirectUri()).thenReturn("http://localhost:8888"); when(receiver.waitForCode()).thenReturn("test-only-code");
        var exchange = mock(com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest.class,RETURNS_SELF);
        when(flow.newTokenRequest("test-only-code")).thenReturn(exchange);
        var tokens = new com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse();
        when(exchange.execute()).thenReturn(tokens);
        when(flow.createAndStoreCredential(tokens,"catalog-user")).thenReturn(fresh);
        when(fresh.getAccessToken()).thenReturn("test-new");
        when(flow.getTransport()).thenReturn(new MockHttpTransport.Builder().setLowLevelHttpResponse(
            new MockLowLevelHttpResponse().setContentType("application/json").setContent("{\"scope\":\"" + SheetsScopes.SPREADSHEETS + "\"}")).build());
        var opened = new java.util.ArrayList<String>();
        var provider = new GoogleSheetsClientProvider(p) {
            @Override protected GoogleAuthorizationCodeFlow createFlow() { return flow; }
            @Override protected com.google.api.client.extensions.java6.auth.oauth2.VerificationCodeReceiver createReceiver() { return receiver; }
            @Override protected void openAuthorizationUrl(String value) { opened.add(value); }
        };
        assertDoesNotThrow(provider::authorizeInteractively);
        assertEquals(1,opened.size()); assertTrue(opened.getFirst().contains("prompt=consent"));
        verify(flow).createAndStoreCredential(tokens,"catalog-user"); verify(receiver).stop();
    }
    @Test void scopeIsReadOnlyByDefaultAndWritableOnlyWhenExplicitlyEnabled() {
        var p = new SheetsProperties(); var provider = new GoogleSheetsClientProvider(p);
        assertEquals(List.of(SheetsScopes.SPREADSHEETS_READONLY),provider.scopes());
        p.setCatalogEnabled(true); assertEquals(List.of(SheetsScopes.SPREADSHEETS),provider.scopes());
    }
    @Test void missingStoredCredentialFailsWithoutInteractiveAuthorization() throws Exception {
        var p = new SheetsProperties(); var flow = mock(GoogleAuthorizationCodeFlow.class);
        var provider = new GoogleSheetsClientProvider(p) { @Override protected GoogleAuthorizationCodeFlow createFlow() { return flow; } };
        assertThrows(IOException.class,provider::client);
        verify(flow).loadCredential("user"); verifyNoMoreInteractions(flow);
    }
    @Test void readOnlyGrantCannotEnableCatalogue() throws Exception {
        var p = new SheetsProperties(); p.setCatalogEnabled(true);
        var flow = mock(GoogleAuthorizationCodeFlow.class);
        var credential = mock(Credential.class);
        when(flow.loadCredential("catalog-user")).thenReturn(credential);
        when(credential.getAccessToken()).thenReturn("test-only-access-token");
        when(credential.getExpiresInSeconds()).thenReturn(3600L);
        when(flow.getTransport()).thenReturn(new MockHttpTransport.Builder().setLowLevelHttpResponse(
            new MockLowLevelHttpResponse().setContentType("application/json").setContent("{\"scope\":\"" + SheetsScopes.SPREADSHEETS_READONLY + "\"}")).build());
        var provider = new GoogleSheetsClientProvider(p) { @Override protected GoogleAuthorizationCodeFlow createFlow() { return flow; } };
        assertThrows(IOException.class,provider::client);
    }
    @Test void authorizationErrorsDoNotExposeTokensMessagesOrExceptionCauses() throws Exception {
        var p = new SheetsProperties();
        var flow = mock(GoogleAuthorizationCodeFlow.class);
        when(flow.loadCredential("user")).thenThrow(new IOException("private-google-message"));
        var provider = new GoogleSheetsClientProvider(p) { @Override protected GoogleAuthorizationCodeFlow createFlow() { return flow; } };
        var failure = assertThrows(IOException.class,provider::client);
        assertFalse(failure.getMessage().contains("private-google-message")); assertNull(failure.getCause());
        var authorization = new GoogleSheetsClientProvider(p) {
            @Override protected GoogleAuthorizationCodeFlow createFlow() throws IOException { throw new IOException("private-google-message"); }
        };
        var interactiveFailure = assertThrows(IOException.class,authorization::authorizeInteractively);
        assertFalse(interactiveFailure.getMessage().contains("private-google-message")); assertNull(interactiveFailure.getCause());
    }
    @Test void expiredStoredCredentialRefreshesNoninteractivelyAndTimeoutsAreBounded() throws Exception {
        var p = new SheetsProperties(); p.setCatalogEnabled(true);
        var flow = mock(GoogleAuthorizationCodeFlow.class); var credential = mock(Credential.class);
        when(flow.loadCredential("catalog-user")).thenReturn(credential);
        when(credential.getAccessToken()).thenReturn("test-only-access-token");
        when(credential.getExpiresInSeconds()).thenReturn(0L);
        when(credential.refreshToken()).thenReturn(true);
        when(flow.getTransport()).thenReturn(new MockHttpTransport.Builder().setLowLevelHttpResponse(
            new MockLowLevelHttpResponse().setContentType("application/json").setContent("{\"scope\":\"" + SheetsScopes.SPREADSHEETS + "\"}")).build());
        var provider = new GoogleSheetsClientProvider(p) { @Override protected GoogleAuthorizationCodeFlow createFlow() { return flow; } };
        var request = provider.client().spreadsheets().get("not-real").buildHttpRequest();
        verify(credential).refreshToken();
        assertEquals(10000,request.getConnectTimeout()); assertEquals(30000,request.getReadTimeout());
        assertFalse(request.isLoggingEnabled());
    }
}
