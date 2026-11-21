package io.github.sob1234509876.osc.server.service;

import io.github.sob1234509876.osc.server.component.OscServerPropertyComponent;
import io.jsonwebtoken.security.Jwks;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Service
public class OscServerOsaJwksService implements InitializingBean {

    @NonNull
    private OscServerPropertyComponent oscServerPropertyComponent;

    @NonNull
    private Map<@NonNull String, @NonNull PublicKey> verifyingKeys = new ConcurrentHashMap<>();

    @Override
    public void afterPropertiesSet() {
        refresh();
    }

    public void refresh() {
        var uri = oscServerPropertyComponent.getOsaBaseUri() + OscServerPropertyComponent.JWKS_PATH;
        var json = new RestTemplate().getForObject(uri, String.class);

        if (json == null)
            throw new RuntimeException("Empty JWKS response from " + uri);

        var keys = new HashMap<String, PublicKey>();

        for (var jwk : Jwks.setParser().build()
                .parse(json)
                .getKeys()) {
            if (jwk.getId() != null && jwk.toKey() instanceof PublicKey publicKey)
                keys.put(jwk.getId(), publicKey);
        }

        verifyingKeys.clear();
        verifyingKeys.putAll(keys);
        log.info("Loaded {} OSA public key(s) from {}", keys.size(), uri);
    }

    @NonNull
    public PublicKey getVerifyingKey(@NonNull String kid) {
        var key = verifyingKeys.get(kid);

        if (key == null) {
            refresh();
            key = verifyingKeys.get(kid);
        }

        if (key == null)
            throw new RuntimeException("No OSA public key for kid " + kid);

        return key;
    }
}
