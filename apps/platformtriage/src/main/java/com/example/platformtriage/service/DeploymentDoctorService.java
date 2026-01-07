package com.example.platformtriage.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.platformtriage.model.dto.EndpointsInfo;
import com.example.platformtriage.model.dto.EventInfo;
import com.example.platformtriage.model.dto.Finding;
import com.example.platformtriage.model.dto.Health;
import com.example.platformtriage.model.dto.Objects;
import com.example.platformtriage.model.dto.PodInfo;
import com.example.platformtriage.model.dto.ServiceInfo;
import com.example.platformtriage.model.dto.Target;
import com.example.platformtriage.model.dto.Workload;
import com.example.platformtriage.model.enums.OverallStatus;
import com.example.platformtriage.model.enums.Severity;
import com.example.platformtriage.model.response.DeploymentSummaryResponse;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.CoreV1EventList;
import io.kubernetes.client.openapi.models.V1ContainerState;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentCondition;
import io.kubernetes.client.openapi.models.V1DeploymentList;
import io.kubernetes.client.openapi.models.V1EndpointSubset;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1ServicePort;

@Service
public class DeploymentDoctorService {

    // Cap number of events per involved object (prevents a single pod from dominating output)
    private final int PER_OBJECT_CAP = 3; // cart-app would become up to 3 × pods total events.

    private final CoreV1Api coreV1;
    private final AppsV1Api appsV1;

    public DeploymentDoctorService(ApiClient client) {
        this.coreV1 = new CoreV1Api(client);
        this.appsV1 = new AppsV1Api(client);
    }

    public DeploymentSummaryResponse getSummary(
            String namespace,
            String selector,
            String release,
            int limitEvents
    ) {
        String effectiveSelector = buildEffectiveSelector(selector, release);

        // Core objects
        List<V1Pod> pods = listPods(namespace, effectiveSelector);
        Map<String, V1Deployment> deployments = listDeploymentsBySelector(namespace, effectiveSelector);

        // Pre-compute names used for event filtering
        Set<String> podNames = pods.stream()
                .map(p -> p.getMetadata() != null ? p.getMetadata().getName() : null)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        Set<String> deployNames = deployments.values().stream()
                .map(d -> d.getMetadata() != null ? d.getMetadata().getName() : null)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        Set<String> replicaSetNames = pods.stream()
                .flatMap(p -> {
                    List<V1OwnerReference> refs = (p.getMetadata() != null) ? p.getMetadata().getOwnerReferences() : null;
                    return refs == null ? java.util.stream.Stream.empty() : refs.stream();
                })
                .filter(ref -> "ReplicaSet".equals(ref.getKind()) && StringUtils.hasText(ref.getName()))
                .map(V1OwnerReference::getName)
                .collect(Collectors.toSet());

        // Events: fetch wide (namespace), then filter to relevant, then sort/dedupe/warn-first/limit
        List<CoreV1Event> nsEvents = listEvents(namespace, limitEvents);

        // Events: fetch wide (namespace), then filter to relevant, then sort/dedupe, then
// Policy 3: guarantee up to W warnings first, then fill with newest normals up to limitEvents.
        List<CoreV1Event> related = nsEvents.stream()
                .filter(e -> e != null && e.getInvolvedObject() != null)
                .filter(e -> {
                    V1ObjectReference ref = e.getInvolvedObject();
                    String kind = ref.getKind();
                    String name = ref.getName();
                    if (!StringUtils.hasText(kind) || !StringUtils.hasText(name)) {
                        return false;
                    }
                    return ("Pod".equals(kind) && podNames.contains(name))
                            || ("Deployment".equals(kind) && deployNames.contains(name))
                            || ("ReplicaSet".equals(kind) && replicaSetNames.contains(name));
                })
                .sorted(Comparator.comparing(this::eventInstant).reversed()) // newest first
                .toList();

        // Deduplicate spam (type+reason+objectKind+objectName); keep newest due to newest-first ordering
        Map<String, CoreV1Event> dedup = new LinkedHashMap<>();
        for (CoreV1Event e : related) {
            V1ObjectReference ref = e.getInvolvedObject(); // should exist due to filtering above
            String key = safe(e.getType()) + "|" + safe(e.getReason()) + "|"
                    + safe(ref.getKind()) + "|" + safe(ref.getName());
            dedup.putIfAbsent(key, e);
        }
        List<CoreV1Event> dedupedRelated = new ArrayList<>(dedup.values());

        // ---- Policy 3 selection ----
        // W = how many warnings you *guarantee* (bounded by limitEvents).
        // Pick a sensible default; this works well in practice.
        int warningBudget = Math.min(limitEvents, Math.max(3, limitEvents / 2));

        List<CoreV1Event> warnings = dedupedRelated.stream()
                .filter(e -> "Warning".equalsIgnoreCase(e.getType()))
                .toList();

        List<CoreV1Event> normals = dedupedRelated.stream()
                .filter(e -> !"Warning".equalsIgnoreCase(e.getType()))
                .toList();

        // Take up to W warnings first, then fill remaining with normals, respecting global limitEvents
        List<CoreV1Event> selected = new ArrayList<>(limitEvents);

        int warningsToTake = Math.min(warningBudget, limitEvents);
        selected.addAll(warnings.stream().limit(warningsToTake).toList());

        int remaining = limitEvents - selected.size();
        if (remaining > 0) {
            selected.addAll(normals.stream().limit(remaining).toList());
        }

        // Map to DTOs (your EventInfo uses @JsonProperty("timestamp") already)
        List<EventInfo> relatedEvents = selected.stream().map(this::toEventInfo).toList();

        // Detect BackOff pods from events (CrashLoop-like even if pod reason flips between Error / CrashLoopBackOff)
        Set<String> backoffPods = relatedEvents.stream()
                .filter(e -> "Pod".equals(e.involvedObjectKind()))
                .filter(e -> podNames.contains(e.involvedObjectName()))
                .filter(e -> "Warning".equalsIgnoreCase(e.type()))
                .filter(e -> "BackOff".equalsIgnoreCase(e.reason()))
                .map(EventInfo::involvedObjectName)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        // Pod infos + breakdown
        List<PodInfo> podInfos = pods.stream().map(this::toPodInfo).toList();
        Map<String, Integer> breakdown = computePodBreakdown(podInfos, backoffPods);

        // Workloads
        List<Workload> workloadInfos = deployments.values().stream()
                .map(this::toWorkloadInfo)
                .toList();

        // Services/endpoints that actually target these pods
        List<V1Service> services = findServicesForPods(namespace, pods);

        Map<String, V1Endpoints> endpointsByService = services.stream()
                .map(s -> s.getMetadata() != null ? s.getMetadata().getName() : null)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toMap(n -> n, n -> readEndpoints(namespace, n)));

        List<ServiceInfo> serviceInfos = services.stream().map(this::toServiceInfo).toList();
        List<EndpointsInfo> endpointsInfos = endpointsByService.entrySet().stream()
                .map(e -> toEndpointsInfo(e.getKey(), e.getValue()))
                .toList();

        // Findings
        List<Finding> findings = new ArrayList<>();
        findings.addAll(findingsFromPods(podInfos));
        findings.addAll(findingsFromDeployments(deployments.values()));
        findings.addAll(findingsFromServicesAndEndpoints(services, endpointsByService));
        findings.addAll(findingsFromEventsForCrashLoop(relatedEvents, podNames));

        if (pods.isEmpty() && deployments.isEmpty()) {
            findings.add(new Finding(
                    Severity.INFO,
                    "NO_MATCHING_OBJECTS",
                    "No pods or deployments matched the provided selector/release in this namespace.",
                    null,
                    List.of("ns/" + namespace)
            ));
        }

        // Check for Key Vault / external secret resolution failures
        findings.addAll(findingsFromExternalSecrets(podInfos, relatedEvents));

        findings = normalizeFindings(findings);
        OverallStatus overall = computeOverall(findings);
        String deploymentsReady = computeDeploymentsReadyString(deployments.values());

        return new DeploymentSummaryResponse(
                OffsetDateTime.now(),
                new Target(namespace, effectiveSelector, release),
                new Health(overall, deploymentsReady, breakdown),
                findings,
                new Objects(workloadInfos, podInfos, relatedEvents, serviceInfos, endpointsInfos)
        );
    }

    // -------------------- selectors / list API --------------------
    private String buildEffectiveSelector(String selector, String release) {
        if (StringUtils.hasText(selector)) {
            return selector;
        }
        if (StringUtils.hasText(release)) {
            return "app.kubernetes.io/instance=" + release;
        }
        throw new IllegalArgumentException("Either 'selector' or 'release' must be provided.");
    }

    private List<V1Pod> listPods(String namespace, String selector) {
        try {
            V1PodList list = coreV1.listNamespacedPod(namespace).labelSelector(selector).execute();
            return list.getItems() == null ? List.of() : list.getItems();
        } catch (ApiException e) {
            throw new IllegalStateException("Failed to list pods: " + e.getResponseBody(), e);
        }
    }

    private Map<String, V1Deployment> listDeploymentsBySelector(String namespace, String selector) {
        try {
            V1DeploymentList list = appsV1.listNamespacedDeployment(namespace).labelSelector(selector).execute();
            if (list.getItems() == null) {
                return Map.of();
            }
            return list.getItems().stream().collect(Collectors.toMap(
                    d -> d.getMetadata().getName(),
                    d -> d
            ));
        } catch (ApiException e) {
            // RBAC may block deployment list; proceed with pods-only view
            return Map.of();
        }
    }

    private List<CoreV1Event> listEvents(String namespace, int limitEvents) {
        int fetchLimit = Math.max(limitEvents * 20, 200);

        try {
            CoreV1EventList list = coreV1.listNamespacedEvent(namespace).limit(fetchLimit).execute();
            if (list.getItems() == null) {
                return List.of();
            }

            return list.getItems().stream()
                    .filter(e -> e != null)
                    .sorted(Comparator.comparing(this::eventInstant).reversed())
                    .toList();
        } catch (ApiException e) {
            return List.of();
        }
    }

    // -------------------- mapping helpers --------------------
    private PodInfo toPodInfo(V1Pod p) {
        String name = p.getMetadata() != null ? p.getMetadata().getName() : "unknown";
        String phase = p.getStatus() != null ? p.getStatus().getPhase() : null;

        boolean ready = isPodReady(p);

        String reason = null;
        int restarts = 0;

        if (p.getStatus() != null && p.getStatus().getContainerStatuses() != null) {
            for (V1ContainerStatus cs : p.getStatus().getContainerStatuses()) {
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

        if (!StringUtils.hasText(reason) && p.getStatus() != null) {
            reason = p.getStatus().getReason();
        }

        return new PodInfo(name, phase, reason, ready, restarts);
    }

    private boolean isPodReady(V1Pod p) {
        if (p.getStatus() == null || p.getStatus().getConditions() == null) {
            return false;
        }
        return p.getStatus().getConditions().stream()
                .anyMatch(c -> "Ready".equals(c.getType()) && "True".equalsIgnoreCase(c.getStatus()));
    }

    private Workload toWorkloadInfo(V1Deployment d) {
        String name = d.getMetadata().getName();
        Integer desired = d.getSpec() != null ? d.getSpec().getReplicas() : null;
        Integer ready = d.getStatus() != null ? d.getStatus().getReadyReplicas() : null;

        String readyStr = (ready == null ? 0 : ready) + "/" + (desired == null ? 0 : desired);

        List<String> conds = new ArrayList<>();
        if (d.getStatus() != null && d.getStatus().getConditions() != null) {
            for (V1DeploymentCondition c : d.getStatus().getConditions()) {
                if (StringUtils.hasText(c.getType()) && StringUtils.hasText(c.getStatus())) {
                    String s = c.getType() + "=" + c.getStatus();
                    if (StringUtils.hasText(c.getReason())) {
                        s += " (" + c.getReason() + ")";
                    }
                    conds.add(s);
                }
            }
        }

        return new Workload(name, "Deployment", readyStr, conds);
    }

    private EventInfo toEventInfo(CoreV1Event e) {
        V1ObjectReference ref = e.getInvolvedObject();

        return new EventInfo(
                e.getType(),
                e.getReason(),
                e.getMessage(),
                ref != null ? ref.getKind() : null,
                ref != null ? ref.getName() : null,
                eventTimestamp(e) // <-- best-available timestamp
        );
    }

    private String eventTimestamp(CoreV1Event e) {
        if (e.getEventTime() != null) {
            return e.getEventTime().toString();
        }
        if (e.getLastTimestamp() != null) {
            return e.getLastTimestamp().toString();
        }
        if (e.getFirstTimestamp() != null) {
            return e.getFirstTimestamp().toString();
        }
        return null;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    // -------------------- breakdown --------------------
    private Map<String, Integer> computePodBreakdown(List<PodInfo> pods, Set<String> backoffPods) {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("running", 0);
        m.put("pending", 0);
        m.put("crashLoop", 0);
        m.put("imagePullBackOff", 0);
        m.put("notReady", 0);

        for (PodInfo p : pods) {
            String phase = p.phase();
            String reason = p.reason();

            if ("Running".equalsIgnoreCase(phase)) {
                m.put("running", m.get("running") + 1);
                if (!p.ready()) {
                    m.put("notReady", m.get("notReady") + 1);
                }
            } else if ("Pending".equalsIgnoreCase(phase)) {
                m.put("pending", m.get("pending") + 1);
            }

            // CrashLoop signal: explicit reason OR observed BackOff events
            if ("CrashLoopBackOff".equalsIgnoreCase(reason) || backoffPods.contains(p.name())) {
                m.put("crashLoop", m.get("crashLoop") + 1);
            }

            if ("ImagePullBackOff".equalsIgnoreCase(reason) || "ErrImagePull".equalsIgnoreCase(reason)) {
                m.put("imagePullBackOff", m.get("imagePullBackOff") + 1);
            }
        }

        return m;
    }

    // -------------------- services / endpoints --------------------
    private List<V1Service> findServicesForPods(String namespace, List<V1Pod> pods) {
        List<V1Service> all;
        try {
            V1ServiceList list = coreV1.listNamespacedService(namespace).execute();
            all = list.getItems() == null ? List.of() : list.getItems();
        } catch (ApiException e) {
            return List.of();
        }

        List<Map<String, String>> podLabels = pods.stream()
                .map(p -> p.getMetadata() != null ? p.getMetadata().getLabels() : null)
                .filter(m -> m != null && !m.isEmpty())
                .toList();

        return all.stream()
                .filter(svc -> svc.getSpec() != null && svc.getSpec().getSelector() != null && !svc.getSpec().getSelector().isEmpty())
                .filter(svc -> {
                    Map<String, String> sel = svc.getSpec().getSelector();
                    // matches if any pod has all selector kv pairs
                    return podLabels.stream().anyMatch(lbls
                            -> sel.entrySet().stream().allMatch(e -> e.getValue().equals(lbls.get(e.getKey())))
                    );
                })
                .toList();
    }

    private V1Endpoints readEndpoints(String namespace, String serviceName) {
        try {
            return coreV1.readNamespacedEndpoints(serviceName, namespace).execute();
        } catch (ApiException e) {
            return null;
        }
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

    private ServiceInfo toServiceInfo(V1Service svc) {
        String name = svc.getMetadata() != null ? svc.getMetadata().getName() : "unknown";
        String type = (svc.getSpec() != null) ? svc.getSpec().getType() : null;

        Map<String, String> selector = (svc.getSpec() != null && svc.getSpec().getSelector() != null)
                ? svc.getSpec().getSelector()
                : Map.of();

        List<String> ports = new ArrayList<>();
        if (svc.getSpec() != null && svc.getSpec().getPorts() != null) {
            for (V1ServicePort p : svc.getSpec().getPorts()) {
                String proto = StringUtils.hasText(p.getProtocol()) ? p.getProtocol() : "TCP";
                String s = String.valueOf(p.getPort());
                if (p.getTargetPort() != null) {
                    s += "->" + p.getTargetPort();
                }
                s += "/" + proto;
                ports.add(s);
            }
        }

        return new ServiceInfo(name, type, selector, ports);
    }

    private EndpointsInfo toEndpointsInfo(String serviceName, V1Endpoints eps) {
        int ready = countReadyAddresses(eps);
        int notReady = countNotReadyAddresses(eps);
        return new EndpointsInfo(serviceName, ready, notReady);
    }

    // -------------------- findings --------------------
    private List<Finding> findingsFromPods(List<PodInfo> pods) {
        List<Finding> out = new ArrayList<>();

        List<String> pull = pods.stream()
                .filter(p -> "ImagePullBackOff".equalsIgnoreCase(p.reason()) || "ErrImagePull".equalsIgnoreCase(p.reason()))
                .map(p -> "pod/" + p.name())
                .toList();
        if (!pull.isEmpty()) {
            out.add(new Finding(Severity.HIGH, "IMAGE_PULL",
                    pull.size() + " pod(s) failing to pull image (ImagePullBackOff/ErrImagePull).", null, pull));
        }

        List<String> badConfig = pods.stream()
                .filter(p -> "CreateContainerConfigError".equalsIgnoreCase(p.reason()))
                .map(p -> "pod/" + p.name())
                .toList();
        if (!badConfig.isEmpty()) {
            out.add(new Finding(Severity.HIGH, "BAD_CONFIG",
                    badConfig.size() + " pod(s) have CreateContainerConfigError (often missing secret/config).", null, badConfig));
        }

        List<String> notReady = pods.stream()
                .filter(p -> "Running".equalsIgnoreCase(p.phase()) && !p.ready())
                .map(p -> "pod/" + p.name())
                .toList();
        if (!notReady.isEmpty()) {
            out.add(new Finding(Severity.MED, "PODS_NOT_READY",
                    notReady.size() + " pod(s) running but not Ready.", null, notReady));
        }

        return out;
    }

    private List<Finding> findingsFromDeployments(Collection<V1Deployment> deployments) {
        List<Finding> out = new ArrayList<>();

        for (V1Deployment d : deployments) {
            String name = d.getMetadata().getName();
            int desired = (d.getSpec() != null && d.getSpec().getReplicas() != null) ? d.getSpec().getReplicas() : 0;
            int ready = (d.getStatus() != null && d.getStatus().getReadyReplicas() != null) ? d.getStatus().getReadyReplicas() : 0;

            boolean stuck = false;
            if (d.getStatus() != null && d.getStatus().getConditions() != null) {
                for (V1DeploymentCondition c : d.getStatus().getConditions()) {
                    if ("Progressing".equals(c.getType())
                            && "False".equalsIgnoreCase(c.getStatus())
                            && "ProgressDeadlineExceeded".equalsIgnoreCase(c.getReason())) {
                        stuck = true;
                    }
                }
            }

            if (stuck) {
                out.add(new Finding(
                        Severity.HIGH,
                        "ROLLOUT_STUCK",
                        "Deployment " + name + " rollout appears stuck (ProgressDeadlineExceeded).",
                        null,
                        List.of("deployment/" + name)
                ));
            } else if (desired > 0 && ready == 0) {
                out.add(new Finding(
                        Severity.HIGH,
                        "NO_READY_PODS",
                        "Deployment " + name + " has 0 ready replicas out of " + desired + ".",
                        null,
                        List.of("deployment/" + name)
                ));
            }
        }

        return out;
    }

    private List<Finding> findingsFromServicesAndEndpoints(List<V1Service> services, Map<String, V1Endpoints> endpointsByService) {
        List<Finding> out = new ArrayList<>();

        for (V1Service svc : services) {
            String name = svc.getMetadata() != null ? svc.getMetadata().getName() : null;
            if (!StringUtils.hasText(name)) {
                continue;
            }

            V1Endpoints eps = endpointsByService.get(name);
            int ready = countReadyAddresses(eps);

            if (ready == 0) {
                out.add(new Finding(
                        Severity.HIGH,
                        "SERVICE_NO_ENDPOINTS",
                        "Service " + name + " has 0 ready endpoints (selector mismatch, pods not Ready, or wrong namespace).",
                        List.of(
                                "Check that service selector labels match pod labels.",
                                "Verify pods are in Ready state.",
                                "Confirm service and pods are in the same namespace."
                        ),
                        List.of("service/" + name, "endpoints/" + name)
                ));
            }
        }

        return out;
    }

    private List<Finding> findingsFromEventsForCrashLoop(List<EventInfo> events, Set<String> podNames) {
        List<String> evidence = events.stream()
                .filter(e -> "Pod".equals(e.involvedObjectKind()))
                .filter(e -> podNames.contains(e.involvedObjectName()))
                .filter(e -> "Warning".equalsIgnoreCase(e.type()))
                .filter(e -> "BackOff".equalsIgnoreCase(e.reason()))
                .map(e -> "pod/" + e.involvedObjectName())
                .distinct()
                .toList();

        if (evidence.isEmpty()) {
            return List.of();
        }

        return List.of(new Finding(
                Severity.HIGH,
                "CRASH_LOOP",
                evidence.size() + " pod(s) restarting with BackOff (CrashLoop-like behavior).",
                null,
                evidence
        ));
    }

    private List<Finding> findingsFromExternalSecrets(List<PodInfo> pods, List<EventInfo> events) {
        List<Finding> out = new ArrayList<>();

        // Pattern 1: Pods stuck in CreateContainerConfigError (often Key Vault secret mount issues)
        List<String> configErrorPods = pods.stream()
                .filter(p -> "CreateContainerConfigError".equalsIgnoreCase(p.reason()))
                .map(p -> "pod/" + p.name())
                .toList();

        // Pattern 2: CSI driver mount failures from events
        List<String> csiMountFailures = events.stream()
                .filter(e -> "Warning".equalsIgnoreCase(e.type()))
                .filter(e -> {
                    String reason = e.reason();
                    String message = e.message();
                    return ("FailedMount".equalsIgnoreCase(reason)
                            || "FailedAttachVolume".equalsIgnoreCase(reason)
                            || "MountVolume.SetUp failed".equalsIgnoreCase(reason))
                            && (message != null && (message.contains("secrets-store.csi.k8s.io")
                            || message.contains("SecretProviderClass")
                            || message.contains("keyvault")
                            || message.contains("Key Vault")
                            || message.toLowerCase().contains("azure") && message.toLowerCase().contains("vault")
                            || message.contains("failed to mount")));
                })
                .map(e -> "event/" + e.reason() + ":" + e.involvedObjectName())
                .distinct()
                .toList();

        // Pattern 3: Secret/ConfigMap not found (if Key Vault syncs to K8s Secret)
        List<String> secretNotFound = events.stream()
                .filter(e -> "Warning".equalsIgnoreCase(e.type()))
                .filter(e -> {
                    String message = e.message();
                    return message != null && (message.contains("secret") && message.contains("not found")
                            || message.contains("configmap") && message.contains("not found")
                            || message.contains("couldn't find key")
                            || message.contains("key does not exist"));
                })
                .map(e -> "event/" + e.reason() + ":" + e.involvedObjectName())
                .distinct()
                .toList();

        // Combine evidence
        List<String> allEvidence = new ArrayList<>();
        allEvidence.addAll(configErrorPods);
        allEvidence.addAll(csiMountFailures);
        allEvidence.addAll(secretNotFound);

        if (!allEvidence.isEmpty()) {
            out.add(new Finding(
                    Severity.HIGH,
                    "EXTERNAL_SECRET_RESOLUTION_FAILED",
                    "Pod(s) cannot mount/load external secrets (Key Vault / CSI / SecretProviderClass).",
                    List.of(
                            "Check Key Vault name/URI in SecretProviderClass matches actual Key Vault.",
                            "Verify identity permissions (Workload Identity / managed identity) have 'Get' permission for secrets.",
                            "Confirm secret object names in Key Vault match exactly (case-sensitive).",
                            "Ensure SecretProviderClass is in the same namespace as the pod.",
                            "Check that the Azure tenant ID is correct if using Workload Identity."
                    ),
                    allEvidence.stream().distinct().toList()
            ));
        }

        return out;
    }

    private List<Finding> normalizeFindings(List<Finding> findings) {
        boolean hasCrashLoop = findings.stream().anyMatch(f -> "CRASH_LOOP".equals(f.code()));
        boolean hasNoReadyPods = findings.stream().anyMatch(f -> "NO_READY_PODS".equals(f.code()));

        return findings.stream()
                // If CRASH_LOOP exists, PODS_NOT_READY is usually redundant (keep it if you prefer)
                .filter(f -> !(hasCrashLoop && "PODS_NOT_READY".equals(f.code())))
                // If NO_READY_PODS exists, PODS_NOT_READY is redundant
                .filter(f -> !(hasNoReadyPods && "PODS_NOT_READY".equals(f.code())))
                .toList();
    }

    // -------------------- status helpers --------------------
    private OverallStatus computeOverall(List<Finding> findings) {
        boolean hasHigh = findings.stream().anyMatch(f -> f.severity() == Severity.HIGH);
        if (hasHigh) {
            return OverallStatus.FAIL;
        }

        boolean hasMed = findings.stream().anyMatch(f -> f.severity() == Severity.MED);
        if (hasMed) {
            return OverallStatus.WARN;
        }

        return OverallStatus.PASS;
    }

    private String computeDeploymentsReadyString(Collection<V1Deployment> deployments) {
        int desired = 0;
        int ready = 0;
        for (V1Deployment d : deployments) {
            desired += (d.getSpec() != null && d.getSpec().getReplicas() != null) ? d.getSpec().getReplicas() : 0;
            ready += (d.getStatus() != null && d.getStatus().getReadyReplicas() != null) ? d.getStatus().getReadyReplicas() : 0;
        }
        return ready + "/" + desired;
    }

    private Instant eventInstant(CoreV1Event e) {
        Instant i = parseInstantOrNull(e.getEventTime() != null ? e.getEventTime().toString() : null);
        if (i != null) {
            return i;
        }

        i = parseInstantOrNull(e.getLastTimestamp() != null ? e.getLastTimestamp().toString() : null);
        if (i != null) {
            return i;
        }

        i = parseInstantOrNull(e.getFirstTimestamp() != null ? e.getFirstTimestamp().toString() : null);
        if (i != null) {
            return i;
        }

        return Instant.EPOCH;
    }

    private Instant parseInstantOrNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return Instant.parse(s);
        } catch (Exception ignored) {
            try {
                return OffsetDateTime.parse(s).toInstant();
            } catch (Exception ignored2) {
                return null;
            }
        }
    }

}
