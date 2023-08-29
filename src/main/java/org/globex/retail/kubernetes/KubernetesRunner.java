package org.globex.retail.kubernetes;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@ApplicationScoped
public class KubernetesRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesRunner.class);

    @Inject
    KubernetesClient client;

    public int run() {

        String kafkaNamespace = System.getenv("KAFKA_NAMESPACE");
        String kafkaClientConnectionSecret = System.getenv().getOrDefault("KAFKA_CLIENT_CONNECTION_SECRET", "kafka-client-secret");
        if (kafkaNamespace == null || kafkaNamespace.isBlank()) {
            LOGGER.error("Environment variable 'KAFKA_NAMESPACE' for kafka namespace not set. Exiting...");
            return -1;
        }

        String namespace = System.getenv("NAMESPACE");
        if (namespace == null || namespace.isBlank()) {
            LOGGER.error("Environment variable 'NAMESPACE' for namespace not set. Exiting...");
            return -1;
        }

        String clientSecret = System.getenv().getOrDefault("CLIENT_SECRET", "kafka-secret");

        Secret kafkaSecret = client.secrets().inNamespace(kafkaNamespace).withName(kafkaClientConnectionSecret).get();
        if (kafkaSecret == null) {
            LOGGER.error("Secret " + kafkaClientConnectionSecret + " not found in namespace " + kafkaClientConnectionSecret);
            return -1;
        }

        String user = new String(Base64.getDecoder().decode(kafkaSecret.getData().get("clientId")));
        String password = new String(Base64.getDecoder().decode(kafkaSecret.getData().get("clientSecret")));
        String securityProtocol = new String(Base64.getDecoder().decode(kafkaSecret.getData().get("securityProtocol")));
        String saslMechanism = new String(Base64.getDecoder().decode(kafkaSecret.getData().get("saslMechanism")));

        Secret newSecret = new SecretBuilder().withNewMetadata().withName(clientSecret).endMetadata()
                .addToData("user", Base64.getEncoder().encodeToString(user.getBytes(StandardCharsets.UTF_8)))
                .addToData("password", Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)))
                .addToData("sasl.mechanism", Base64.getEncoder().encodeToString(saslMechanism.getBytes(StandardCharsets.UTF_8)))
                .addToData("protocol", Base64.getEncoder().encodeToString(securityProtocol.getBytes(StandardCharsets.UTF_8)))
                .build();
        client.secrets().inNamespace(namespace).resource(newSecret).createOrReplace();

        LOGGER.info("Secret " + clientSecret + " created in namespace " + namespace + ". Exiting.");

        return 0;
    }
}
