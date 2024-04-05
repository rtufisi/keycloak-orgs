package io.phasetwo.service.exportimport.dir;

import com.google.auto.service.AutoService;
import org.keycloak.Config;
import org.keycloak.exportimport.ExportImportConfig;
import org.keycloak.exportimport.ExportProvider;
import org.keycloak.exportimport.ExportProviderFactory;
import org.keycloak.exportimport.UsersExportStrategy;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import java.util.List;

@AutoService(ExportProviderFactory.class)
public class KeycloakOrgsDirExportProviderFactory implements ExportProviderFactory {
    public static final String PROVIDER_ID = "dir";
    public static final String DIR = "dir";
    public static final String REALM_NAME = "realmName";
    public static final String USERS_EXPORT_STRATEGY = "usersExportStrategy";
    public static final String USERS_PER_FILE = "usersPerFile";
    private Config.Scope config;

    public KeycloakOrgsDirExportProviderFactory() {
    }

    public ExportProvider create(KeycloakSession session) {
        String dir = System.getProperty("keycloak.migration.dir", this.config.get("dir"));
        String realmName = System.getProperty("keycloak.migration.realmName", this.config.get("realmName"));
        String usersExportStrategy = System.getProperty("keycloak.migration.usersExportStrategy", this.config.get("usersExportStrategy", ExportImportConfig.DEFAULT_USERS_EXPORT_STRATEGY.toString()));
        String usersPerFile = System.getProperty("keycloak.migration.usersPerFile", this.config.get("usersPerFile", String.valueOf(ExportImportConfig.DEFAULT_USERS_PER_FILE)));

        var provider = new KeycloakOrgsDirExportProvider(session);
        return provider
                .withDir(dir)
                .withRealmName(realmName)
                .withUsersExportStrategy(Enum.valueOf(UsersExportStrategy.class, usersExportStrategy.toUpperCase()))
                .withUsersPerFile(Integer.parseInt(usersPerFile.trim()));
    }

    public void init(Config.Scope config) {
        this.config = config;
    }

    public void postInit(KeycloakSessionFactory factory) {
    }

    public void close() {
    }

    public String getId() {
        return "dir";
    }

    public List<ProviderConfigProperty> getConfigMetadata() {
        return ProviderConfigurationBuilder.create().property().name("realmName").type("string").helpText("Realm to export").add().property().name("dir").type("string").helpText("Directory to export to").add().property().name("usersExportStrategy").type("string").helpText("Users export strategy").defaultValue(ExportImportConfig.DEFAULT_USERS_EXPORT_STRATEGY).add().property().name("usersPerFile").type("int").helpText("Users per exported file").defaultValue(ExportImportConfig.DEFAULT_USERS_PER_FILE).add().build();
    }
}
