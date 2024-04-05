/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.phasetwo.service.exportimport.singlefile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.phasetwo.service.datastore.representation.KeycloakOrgsRealmRepresentation;
import io.phasetwo.service.model.OrganizationProvider;
import io.phasetwo.service.resource.Converters;
import org.jboss.logging.Logger;
import org.keycloak.exportimport.ExportOptions;
import org.keycloak.exportimport.ExportProvider;
import org.keycloak.exportimport.util.ExportImportSessionTask;
import org.keycloak.exportimport.util.ExportUtils;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.ModelException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.services.ServicesLogger;
import org.keycloak.services.util.ObjectMapperResolver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;


//Todo: Lots of testing
public class KeycloakOrgsSingleFileExportProvider implements ExportProvider {

    private static final Logger logger = Logger.getLogger(KeycloakOrgsSingleFileExportProvider.class);

    private File file;

    private final KeycloakSessionFactory factory;
    private String realmName;

    public KeycloakOrgsSingleFileExportProvider(KeycloakSessionFactory factory) {
        this.factory = factory;
    }

    public KeycloakOrgsSingleFileExportProvider withFile(File file) {
        this.file = file;
        return this;
    }

    @Override
    public void exportModel() {
        if (realmName != null) {
            ServicesLogger.LOGGER.realmExportRequested(realmName);
            exportRealm(realmName);
        } else {
            ServicesLogger.LOGGER.fullModelExportRequested();
            logger.infof("Exporting model into file %s", this.file.getAbsolutePath());
            KeycloakModelUtils.runJobInTransaction(factory, new ExportImportSessionTask() {

                @Override
                protected void runExportImportTask(KeycloakSession session) throws IOException {
                    Stream<RealmRepresentation> realms = session.realms().getRealmsStream()
                            .peek(realm -> session.getContext().setRealm(realm))
                            .map(realm ->{
                               var exportedRealm = ExportUtils.exportRealm(session, realm, true, true);

                               var options = new ExportOptions();
                                OrganizationProvider organizationProvider =
                                        session.getProvider(OrganizationProvider.class);

                                KeycloakOrgsRealmRepresentation keycloakOrgsRepresentation;
                                var mapper = new ObjectMapper();
                                try {
                                    var json = mapper.writeValueAsString(exportedRealm);
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

                                return keycloakOrgsRepresentation;
                            });

                    writeToFile(realms);
                }
            });
        }
        ServicesLogger.LOGGER.exportSuccess();
    }

    private void exportRealm(final String realmName) {
        logger.infof("Exporting realm '%s' into file %s", realmName, this.file.getAbsolutePath());
        KeycloakModelUtils.runJobInTransaction(factory, new ExportImportSessionTask() {

            @Override
            protected void runExportImportTask(KeycloakSession session) throws IOException {
                RealmModel realm = session.realms().getRealmByName(realmName);
                Objects.requireNonNull(realm, "realm not found by realm name '" + realmName + "'");
                session.getContext().setRealm(realm);
                RealmRepresentation realmRep = ExportUtils.exportRealm(session, realm, true, true);
                var options = new ExportOptions();
                OrganizationProvider organizationProvider =
                        session.getProvider(OrganizationProvider.class);

                KeycloakOrgsRealmRepresentation keycloakOrgsRepresentation;
                var mapper = new ObjectMapper();
                try {
                    var json = mapper.writeValueAsString(realmRep);
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

                writeToFile(keycloakOrgsRepresentation);
            }

        });
    }

    @Override
    public void close() {
    }

    private ObjectMapper getObjectMapper() {
        ObjectMapper streamSerializer = ObjectMapperResolver.createStreamSerializer();
        streamSerializer.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        streamSerializer.enable(SerializationFeature.INDENT_OUTPUT);
        return streamSerializer;
    }

    private void writeToFile(Object reps) throws IOException {
        FileOutputStream stream = new FileOutputStream(this.file);
        getObjectMapper().writeValue(stream, reps);
    }

    public ExportProvider withRealmName(String realmName) {
        this.realmName = realmName;
        return this;
    }
}
