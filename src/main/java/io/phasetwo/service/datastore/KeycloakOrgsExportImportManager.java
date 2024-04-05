package io.phasetwo.service.datastore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.phasetwo.service.datastore.representation.KeycloakOrgsRealmRepresentation;
import io.phasetwo.service.model.OrganizationProvider;
import io.phasetwo.service.resource.Converters;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.common.util.CollectionUtil;
import org.keycloak.exportimport.ExportAdapter;
import org.keycloak.exportimport.ExportOptions;
import org.keycloak.exportimport.util.ExportUtils;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.jose.jws.JWSInputException;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.managers.RealmManager;
import org.keycloak.services.resources.admin.AdminAuth;
import org.keycloak.storage.ImportRealmFromRepresentationEvent;
import org.keycloak.storage.datastore.DefaultExportImportManager;
import org.keycloak.util.JsonSerialization;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

import static io.phasetwo.service.util.ImportUtils.addInvitations;
import static io.phasetwo.service.util.ImportUtils.addMembers;
import static io.phasetwo.service.util.ImportUtils.createOrganization;
import static io.phasetwo.service.util.ImportUtils.createOrganizationIdp;
import static io.phasetwo.service.util.ImportUtils.createOrganizationRoles;
import static org.keycloak.models.utils.StripSecretsUtils.stripForExport;

@JBossLog
public class KeycloakOrgsExportImportManager extends DefaultExportImportManager {
    private final KeycloakSession session;

    public KeycloakOrgsExportImportManager(KeycloakSession session) {
        super(session);
        this.session = session;
    }

    @Override
    public void exportRealm(RealmModel realm, ExportOptions options, ExportAdapter callback) {
        callback.setType(MediaType.APPLICATION_JSON);
        callback.writeToOutputStream(
                outputStream -> {
                    RealmRepresentation realmRepresentation =
                            ExportUtils.exportRealm(session, realm, options, false);
                    OrganizationProvider organizationProvider =
                            session.getProvider(OrganizationProvider.class);

                    KeycloakOrgsRealmRepresentation keycloakOrgsRepresentation;
                    var mapper = new ObjectMapper();
                    try {
                        var json = mapper.writeValueAsString(realmRepresentation);
                        log.debugv("export realm json: {0}", json);
                        keycloakOrgsRepresentation =
                                mapper.readValue(json, KeycloakOrgsRealmRepresentation.class);
                    } catch (JsonProcessingException e) {
                        throw new ModelException("unable to read contents from Json", e);
                    }

                    var organizations =
                            organizationProvider
                                    .searchForOrganizationStream(
                                            realm, Map.of(), 0, Constants.DEFAULT_MAX_RESULTS, Optional.empty())
                                    .map(
                                            organization ->
                                                    Converters.convertOrganizationModelToOrganizationRepresentation(
                                                            organization, realm, options))
                                    .toList();
                    keycloakOrgsRepresentation.setOrganizations(organizations);

                    stripForExport(session, keycloakOrgsRepresentation);

                    JsonSerialization.writeValueToStream(outputStream, keycloakOrgsRepresentation);
                    outputStream.close();
                });
    }

    @Override
    public RealmModel importRealm(InputStream requestBody) {
        KeycloakOrgsRealmRepresentation keycloakOrgsRepresentation;
        try {
            keycloakOrgsRepresentation =
                    JsonSerialization.readValue(requestBody, KeycloakOrgsRealmRepresentation.class);
        } catch (IOException e) {
            throw new ModelException("unable to read contents from stream", e);
        }
        log.debugv("importRealm: {0}", keycloakOrgsRepresentation.getRealm());
        return ImportRealmFromRepresentationEvent.fire(session, keycloakOrgsRepresentation);
    }

    @Override
    public void importRealm(RealmRepresentation rep, RealmModel newRealm, boolean skipUserDependent) {
        OrganizationProvider organizationProvider = session.getProvider(OrganizationProvider.class);
        super.importRealm(rep, newRealm, skipUserDependent);

        var keycloakOrgsRepresentation = (KeycloakOrgsRealmRepresentation) rep;
        var organizations = keycloakOrgsRepresentation.getOrganizations();
        if (!CollectionUtil.isEmpty(organizations)) {
            UserModel createdBy;
            if (session.getContext().getAuthenticationSession() != null) {
                var auth = authenticateRealmAdminRequest(session.getContext().getRequestHeaders());
                createdBy = auth.getUser();
            } else {
                createdBy = null;
            }
            organizations.forEach(
                    organizationRepresentation -> {
                        var org =
                                createOrganization(createdBy,
                                        newRealm, organizationRepresentation.getOrganization(), organizationProvider);

                        createOrganizationRoles(organizationRepresentation.getRoles(), org);

                        createOrganizationIdp(newRealm, organizationRepresentation.getIdpLink(), org);

                        addMembers(session, newRealm, organizationRepresentation, org);

                        addInvitations(session, newRealm, organizationRepresentation, org);
                    });
        }
    }

    protected AdminAuth authenticateRealmAdminRequest(HttpHeaders headers) {
        String tokenString = AppAuthManager.extractAuthorizationHeaderToken(headers);
        if (tokenString == null) throw new NotAuthorizedException("Bearer");
        AccessToken token;
        try {
            JWSInput input = new JWSInput(tokenString);
            token = input.readJsonContent(AccessToken.class);
        } catch (JWSInputException e) {
            throw new NotAuthorizedException("Bearer token format error");
        }
        String realmName = token.getIssuer().substring(token.getIssuer().lastIndexOf('/') + 1);
        RealmManager realmManager = new RealmManager(session);
        RealmModel realm = realmManager.getRealmByName(realmName);
        if (realm == null) {
            throw new NotAuthorizedException("Unknown realm in token");
        }
        session.getContext().setRealm(realm);

        AuthenticationManager.AuthResult authResult =
                new AppAuthManager.BearerTokenAuthenticator(session)
                        .setRealm(realm)
                        .setConnection(session.getContext().getConnection())
                        .setHeaders(headers)
                        .authenticate();

        if (authResult == null) {
            log.debug("Token not valid");
            throw new NotAuthorizedException("Bearer");
        }

        return new AdminAuth(
                realm, authResult.getToken(), authResult.getUser(), authResult.getClient());
    }
}
