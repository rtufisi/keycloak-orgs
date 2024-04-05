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

package io.phasetwo.service.exportimport.dir;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.phasetwo.service.datastore.representation.KeycloakOrgsRealmRepresentation;
import io.phasetwo.service.model.OrganizationProvider;
import io.phasetwo.service.resource.Converters;
import org.keycloak.exportimport.ExportOptions;
import org.keycloak.exportimport.util.ExportUtils;
import org.keycloak.exportimport.util.MultipleStepsExportProvider;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.platform.Platform;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.util.JsonSerialization;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class KeycloakOrgsDirExportProvider extends MultipleStepsExportProvider<KeycloakOrgsDirExportProvider> {
    private String dir;
    private File rootDirectory;

    public KeycloakOrgsDirExportProvider(KeycloakSession session) {
        super(session.getKeycloakSessionFactory());
    }

    private File getRootDirectory() {
        if (this.rootDirectory == null) {
            if (this.dir == null) {
                this.rootDirectory = new File(Platform.getPlatform().getTmpDirectory(), "keycloak-export");
            } else {
                this.rootDirectory = new File(this.dir);
            }

            this.rootDirectory.mkdirs();
            this.logger.infof("Exporting into directory %s", this.rootDirectory.getAbsolutePath());
        }

        return this.rootDirectory;
    }

    public static boolean recursiveDeleteDir(File dirPath) {
        if (dirPath.exists()) {
            File[] files = dirPath.listFiles();

            for(int i = 0; i < files.length; ++i) {
                if (files[i].isDirectory()) {
                    recursiveDeleteDir(files[i]);
                } else {
                    files[i].delete();
                }
            }
        }

        return dirPath.exists() ? dirPath.delete() : true;
    }

    public void writeRealm(String fileName, RealmRepresentation rep) throws IOException {
        File file = new File(this.getRootDirectory(), fileName);
        FileOutputStream is = new FileOutputStream(file);

        var representation =  KeycloakModelUtils.runJobInTransactionWithResult(this.factory, (session) -> {
            OrganizationProvider organizationProvider =
                    session.getProvider(OrganizationProvider.class);
            var realm = session.realms().getRealmByName(rep.getRealm());
            var options = new ExportOptions();

            KeycloakOrgsRealmRepresentation keycloakOrgsRepresentation;
            var mapper = new ObjectMapper();
            try {
                var json = mapper.writeValueAsString(rep);
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


        try {
            JsonSerialization.prettyMapper.writeValue(is, representation);
        } catch (Throwable var8) {
            try {
                is.close();
            } catch (Throwable var7) {
                var8.addSuppressed(var7);
            }

            throw var8;
        }

        is.close();
    }

    protected void writeUsers(String fileName, KeycloakSession session, RealmModel realm, List<UserModel> users) throws IOException {
        File file = new File(this.getRootDirectory(), fileName);
        FileOutputStream os = new FileOutputStream(file);
        ExportUtils.exportUsersToStream(session, realm, users, JsonSerialization.prettyMapper, os);
    }

    protected void writeFederatedUsers(String fileName, KeycloakSession session, RealmModel realm, List<String> users) throws IOException {
        File file = new File(this.getRootDirectory(), fileName);
        FileOutputStream os = new FileOutputStream(file);
        ExportUtils.exportFederatedUsersToStream(session, realm, users, JsonSerialization.prettyMapper, os);
    }

    public void close() {
    }

    public KeycloakOrgsDirExportProvider withDir(String dir) {
        this.dir = dir;
        return this;
    }
}
