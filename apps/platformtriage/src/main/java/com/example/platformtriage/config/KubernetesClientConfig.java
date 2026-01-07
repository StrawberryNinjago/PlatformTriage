package com.example.platformtriage.config;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.openapi.Configuration;
import org.springframework.context.annotation.Bean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@org.springframework.context.annotation.Configuration
public class KubernetesClientConfig {

  private static final Logger log = LoggerFactory.getLogger(KubernetesClientConfig.class);

  @Bean
  public ApiClient kubernetesApiClient() {
    try {
      // Works in-cluster and locally (uses kubeconfig by default if not in cluster)
      ApiClient client = Config.defaultClient();
      // Optional: tune timeouts for cluster calls
      client.setReadTimeout(10_000);
      client.setConnectTimeout(5_000);
      client.setWriteTimeout(10_000);

      Configuration.setDefaultApiClient(client);
      log.info("✓ Kubernetes ApiClient initialized successfully");
      return client;
    } catch (Exception e) {
      log.error("✗ Failed to initialize Kubernetes ApiClient: {}", e.getMessage());
      log.error("  Kubernetes features will not be available.");
      log.error("  To enable: Configure kubectl and ensure ~/.kube/config is valid");
      throw new IllegalStateException("Failed to initialize Kubernetes ApiClient. " +
          "Ensure kubectl is configured and ~/.kube/config is accessible.", e);
    }
  }
}
