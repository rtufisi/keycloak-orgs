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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.phasetwo.service.datastore.representation.KeycloakOrgsRealmRepresentation;
import io.phasetwo.service.model.OrganizationProvider;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.common.util.CollectionUtil;
import org.keycloak.exportimport.AbstractFileBasedImportProvider;
import org.keycloak.exportimport.Strategy;
import org.keycloak.exportimport.util.ExportImportSessionTask;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.util.JsonSerialization;
import org.keycloak.exportimport.util.ImportUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static io.phasetwo.service.util.ImportUtils.addInvitations;
import static io.phasetwo.service.util.ImportUtils.addMembers;
import static io.phasetwo.service.util.ImportUtils.createOrganization;
import static io.phasetwo.service.util.ImportUtils.createOrganizationIdp;
import static io.phasetwo.service.util.ImportUtils.createOrganizationRoles;

public class KeycloakOrgsSingleFileImportProvider extends AbstractFileBasedImportProvider {

    private static final Logger logger = Logger.getLogger(KeycloakOrgsSingleFileImportProvider.class);
    private final KeycloakSessionFactory factory;

    private final File file;
    private final Strategy strategy;

    // Allows to cache representation per provider to avoid parsing them twice
    protected Map<String, KeycloakOrgsRealmRepresentation> realmReps;

    public KeycloakOrgsSingleFileImportProvider(KeycloakSessionFactory factory, File file, Strategy strategy) {
        this.factory = factory;
        this.file = file;
        this.strategy = strategy;
    }

    public void importModel() throws IOException {
        logger.infof("Full importing from file %s", this.file.getAbsolutePath());
        checkRealmReps();
        var keycloakRealm = realmReps.values()
                .stream()
                .map(RealmRepresentation.class::cast)
                .toList();

        KeycloakModelUtils.runJobInTransaction(this.factory, new ExportImportSessionTask() {
            protected void runExportImportTask(KeycloakSession session) {
                ImportUtils.importRealms(session, keycloakRealm, strategy);
            }
        });
    }

    @Override
    public boolean isMasterRealmExported() throws IOException {
        checkRealmReps();
        return (realmReps.containsKey(Config.getAdminRealm()));
    }

    protected void checkRealmReps() throws IOException {
        if (realmReps == null) {
            InputStream is = parseFile(file);
            realmReps = getRealmsFromStream(JsonSerialization.mapper, is);
        }
    }

    private Map<String, KeycloakOrgsRealmRepresentation> getRealmsFromStream(ObjectMapper mapper, InputStream is) throws IOException {
        Map<String, KeycloakOrgsRealmRepresentation> result = new HashMap();
        JsonFactory factory = mapper.getFactory();
        JsonParser parser = factory.createParser(is);

        try {
            parser.nextToken();
            if (parser.getCurrentToken() == JsonToken.START_ARRAY) {
                parser.nextToken();
                List<KeycloakOrgsRealmRepresentation> realmReps = new ArrayList();

                while (parser.getCurrentToken() == JsonToken.START_OBJECT) {
                    KeycloakOrgsRealmRepresentation realmRep = parser.readValueAs(KeycloakOrgsRealmRepresentation.class);
                    parser.nextToken();
                    if (Config.getAdminRealm().equals(realmRep.getRealm())) {
                        realmReps.add(0, realmRep);
                    } else {
                        realmReps.add(realmRep);
                    }
                }

                Iterator var12 = realmReps.iterator();

                while (var12.hasNext()) {
                    KeycloakOrgsRealmRepresentation realmRep = (KeycloakOrgsRealmRepresentation) var12.next();
                    result.put(realmRep.getRealm(), realmRep);
                }
            } else if (parser.getCurrentToken() == JsonToken.START_OBJECT) {
                KeycloakOrgsRealmRepresentation realmRep = parser.readValueAs(KeycloakOrgsRealmRepresentation.class);
                result.put(realmRep.getRealm(), realmRep);
            }
        } finally {
            parser.close();
        }

        return result;
    }


    @Override
    public void close() {

    }
}
