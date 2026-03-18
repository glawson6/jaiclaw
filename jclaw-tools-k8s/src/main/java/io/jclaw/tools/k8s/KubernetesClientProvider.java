package io.jclaw.tools.k8s;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates and caches a {@link KubernetesClient} instance.
 * Auto-discovers kubeconfig from {@code ~/.kube/config} or in-cluster service account.
 */
public class KubernetesClientProvider {

    private static final Logger log = LoggerFactory.getLogger(KubernetesClientProvider.class);

    private volatile KubernetesClient client;

    public KubernetesClient getClient() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    log.info("Creating Kubernetes client (auto-discovering kubeconfig)");
                    client = new KubernetesClientBuilder().build();
                }
            }
        }
        return client;
    }

    public void close() {
        KubernetesClient c = client;
        if (c != null) {
            c.close();
            client = null;
        }
    }
}
