package com.example.platformtriage.detection;

/**
 * Normalized view of service endpoints.
 */
public record EndpointsView(
    String serviceName,
    int readyAddresses,
    int notReadyAddresses
) {
    public boolean hasNoReadyAddresses() {
        return readyAddresses == 0;
    }
}
