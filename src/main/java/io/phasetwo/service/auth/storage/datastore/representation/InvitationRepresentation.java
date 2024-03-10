package io.phasetwo.service.auth.storage.datastore.representation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class InvitationRepresentation {
    @JsonProperty("email")
    private @Email @Valid String email = null;
    @JsonProperty("inviterUsername")
    private @Valid String inviterUsername = null;

    @JsonProperty("roles")
    private @Valid List<String> roles = Lists.newArrayList();

    @JsonProperty("redirectUri")
    private String redirectUri;

    @JsonProperty("attributes")
    private @Valid Map<String, List<String>> attributes = Maps.newHashMap();
}
