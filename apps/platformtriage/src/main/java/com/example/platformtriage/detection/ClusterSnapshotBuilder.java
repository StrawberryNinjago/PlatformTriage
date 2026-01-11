package com.example.platformtriage.detection;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.util.StringUtils;

import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ContainerState;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentCondition;
import io.kubernetes.client.openapi.models.V1EndpointSubset;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Service;

/**
 * Builds a ClusterSnapshot from Kubernetes API types.
 * 
 * This is the adapter layer between K8s client types and our normalized view types.
 * It extracts only the fields needed by detectors, making them testable.
 */
public class ClusterSnapshotBuilder {
    
    public ClusterSnapshot build(
        List<V1Pod> pods,
        Map<String, V1Deployment> deployments,
        List<CoreV1Event> events,
        List<V1Service> services,
        Map<String, V1Endpoints> endpointsByService
    ) {
        return new ClusterSnapshot(
            pods.stream().map(this::toPodView).toList(),
            deployments.values().stream().map(this::toDeploymentView).toList(),
            events.stream().map(this::toEventView).toList(),
            services.stream().map(this::toServiceView).toList(),
            endpointsByService.entrySet().stream()
                .map(e -> toEndpointsView(e.getKey(), e.getValue()))
                .toList()
        );
    }
    
    private PodView toPodView(V1Pod pod) {
        String name = pod.getMetadata() != null ? pod.getMetadata().getName() : "unknown";
        String phase = pod.getStatus() != null ? pod.getStatus().getPhase() : null;
        boolean ready = isPodReady(pod);
        
        String reason = null;
        int restarts = 0;
        
        if (pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null) {
            for (V1ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
                if (cs.getRestartCount() != null) {
                    restarts += cs.getRestartCount();
                }
                
                V1ContainerState state = cs.getState();
                if (state != null && state.getWaiting() != null && StringUtils.hasText(state.getWaiting().getReason())) {
                    reason = state.getWaiting().getReason();
                    break;
                }
                if (state != null && state.getTerminated() != null && StringUtils.hasText(state.getTerminated().getReason())) {
                    reason = state.getTerminated().getReason();
                }
            }
        }
        
        if (!StringUtils.hasText(reason) && pod.getStatus() != null) {
            reason = pod.getStatus().getReason();
        }
        
        return new PodView(name, phase, reason, ready, restarts);
    }
    
    private boolean isPodReady(V1Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getConditions() == null) {
            return false;
        }
        return pod.getStatus().getConditions().stream()
            .anyMatch(c -> "Ready".equals(c.getType()) && "True".equalsIgnoreCase(c.getStatus()));
    }
    
    private DeploymentView toDeploymentView(V1Deployment deployment) {
        String name = deployment.getMetadata().getName();
        int desired = (deployment.getSpec() != null && deployment.getSpec().getReplicas() != null) 
            ? deployment.getSpec().getReplicas() : 0;
        int ready = (deployment.getStatus() != null && deployment.getStatus().getReadyReplicas() != null) 
            ? deployment.getStatus().getReadyReplicas() : 0;
        
        List<DeploymentView.DeploymentCondition> conditions = new ArrayList<>();
        if (deployment.getStatus() != null && deployment.getStatus().getConditions() != null) {
            for (V1DeploymentCondition c : deployment.getStatus().getConditions()) {
                conditions.add(new DeploymentView.DeploymentCondition(
                    c.getType(),
                    c.getStatus(),
                    c.getReason()
                ));
            }
        }
        
        return new DeploymentView(name, desired, ready, conditions);
    }
    
    private EventView toEventView(CoreV1Event event) {
        V1ObjectReference ref = event.getInvolvedObject();
        
        EventView.InvolvedObject involvedObject = null;
        if (ref != null) {
            involvedObject = new EventView.InvolvedObject(
                ref.getKind(),
                ref.getName(),
                ref.getNamespace()
            );
        }
        
        return new EventView(
            event.getType(),
            event.getReason(),
            event.getMessage(),
            eventTimestamp(event),
            involvedObject
        );
    }
    
    private OffsetDateTime eventTimestamp(CoreV1Event event) {
        if (event.getEventTime() != null) {
            return parseOffsetDateTime(event.getEventTime().toString());
        }
        if (event.getLastTimestamp() != null) {
            return parseOffsetDateTime(event.getLastTimestamp().toString());
        }
        if (event.getFirstTimestamp() != null) {
            return parseOffsetDateTime(event.getFirstTimestamp().toString());
        }
        return OffsetDateTime.now();
    }
    
    private OffsetDateTime parseOffsetDateTime(String s) {
        if (!StringUtils.hasText(s)) {
            return OffsetDateTime.now();
        }
        try {
            return OffsetDateTime.parse(s);
        } catch (Exception e) {
            try {
                return Instant.parse(s).atOffset(ZoneOffset.UTC);
            } catch (Exception e2) {
                return OffsetDateTime.now();
            }
        }
    }
    
    private ServiceView toServiceView(V1Service service) {
        String name = service.getMetadata() != null ? service.getMetadata().getName() : "unknown";
        String type = (service.getSpec() != null) ? service.getSpec().getType() : null;
        Map<String, String> selector = (service.getSpec() != null && service.getSpec().getSelector() != null)
            ? service.getSpec().getSelector()
            : Map.of();
        
        return new ServiceView(name, type, selector);
    }
    
    private EndpointsView toEndpointsView(String serviceName, V1Endpoints endpoints) {
        int ready = countReadyAddresses(endpoints);
        int notReady = countNotReadyAddresses(endpoints);
        return new EndpointsView(serviceName, ready, notReady);
    }
    
    private int countReadyAddresses(V1Endpoints eps) {
        if (eps == null || eps.getSubsets() == null) {
            return 0;
        }
        int count = 0;
        for (V1EndpointSubset s : eps.getSubsets()) {
            if (s.getAddresses() != null) {
                count += s.getAddresses().size();
            }
        }
        return count;
    }
    
    private int countNotReadyAddresses(V1Endpoints eps) {
        if (eps == null || eps.getSubsets() == null) {
            return 0;
        }
        int count = 0;
        for (V1EndpointSubset s : eps.getSubsets()) {
            if (s.getNotReadyAddresses() != null) {
                count += s.getNotReadyAddresses().size();
            }
        }
        return count;
    }
}
