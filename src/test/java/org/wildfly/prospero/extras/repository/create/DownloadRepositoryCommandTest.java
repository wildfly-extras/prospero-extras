package org.wildfly.prospero.extras.repository.create;

import org.apache.maven.settings.Activation;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Settings;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DownloadRepositoryCommandTest {

    @Test
    public void nullSettingsGivesNoProperties() {
        assertThat(DownloadRepositoryCommand.getDefaultProfileProperties(null)).isEmpty();
    }

    @Test
    public void onlyActiveByDefaultProfilesAreRead() {
        final Settings settings = new Settings();
        settings.addProfile(profile("active", true, "aether.transport.http.connectTimeout", "1000"));
        settings.addProfile(profile("inactive", false, "aether.transport.http.requestTimeout", "2000"));
        settings.addProfile(profile("no-activation", null, "aether.transport.http.connectionMaxTtl", "3000"));

        final Map<String, String> properties = DownloadRepositoryCommand.getDefaultProfileProperties(settings);

        assertThat(properties).containsExactly(Map.entry("aether.transport.http.connectTimeout", "1000"));
    }

    private static Profile profile(String id, Boolean activeByDefault, String key, String value) {
        final Profile profile = new Profile();
        profile.setId(id);
        profile.addProperty(key, value);
        if (activeByDefault != null) {
            final Activation activation = new Activation();
            activation.setActiveByDefault(activeByDefault);
            profile.setActivation(activation);
        }
        return profile;
    }
}
