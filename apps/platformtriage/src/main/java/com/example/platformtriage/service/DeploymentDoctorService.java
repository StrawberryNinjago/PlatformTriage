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
import com.example.platformtriage.model.dto.Evidence;
import com.example.platformtriage.model.dto.Finding;
import com.example.platformtriage.model.dto.Health;
import com.example.platformtriage.model.dto.Objects;
import com.example.platformtriage.model.dto.PodInfo;
import com.example.platformtriage.model.dto.ServiceInfo;
import com.example.platformtriage.model.dto.Target;
import com.example.platformtriage.model.dto.Workload;
import com.example.platformtriage.model.enums.FailureCode;
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
    private final RestartBaselineStore restartBaselineStore;

    public DeploymentDoctorService(ApiClient client, RestartBaselineStore restartBaselineStore) {
        this.coreV1 = new CoreV1Api(client);
        this.appsV1 = new AppsV1Api(client);
        this.restartBaselineStore = restartBaselineStore;
    }

    public DeploymentSummaryResponse getSummary(
            String namespace,
            String selector,
            String release,
            int limitEvents
    ) {
        // ==================== QUERY FAILURE HANDLING ====================
        // Wrap the entire query phase to detect input/platform query failures
        // This is a first-class failure category (tooling/query failures)
        try {
            return executeQuery(namespace, selector, release, limitEvents);
        } catch (IllegalArgumentException e) {
            // Selector/release validation failed or namespace invalid
            return buildQueryInvalidResponse(namespace, selector, release, e.getMessage());
        } catch (ApiException e) {
            // Kubernetes API returned error (400/422 = bad request, invalid selector syntax)
            if (e.getCode() == 400 || e.getCode() == 422) {
                return buildQueryInvalidResponse(namespace, selector, release,
                        "Kubernetes API rejected query: " + e.getMessage());
            }
            // Other API errors (403, 404, 500) - re-throw for generic error handling
            throw new IllegalStateException("Failed to query Kubernetes: " + e.getResponseBody(), e);
        }
    }

    /**
     * Execute the actual query logic (extracted from getSummary for error
     * handling).
     */
    private DeploymentSummaryResponse executeQuery(
            String namespace,
            String selector,
            String release,
            int limitEvents
    ) throws ApiException {
        String effectiveSelector = buildEffectiveSelector(selector, release);

        // Core objects
        List<V1Pod> pods = listPodsOrThrow(namespace, effectiveSelector);
        Map<String, V1Deployment> deployments = listDeploymentsBySelector(namespace, effectiveSelector);

        // ==================== FIX 3: UNKNOWN SHORT-CIRCUIT ====================
        // If no pods AND no deployments, return UNKNOWN immediately
        if (pods.isEmpty() && deployments.isEmpty()) {
            Finding noMatchingObjectsFinding = new Finding(
                    FailureCode.NO_MATCHING_OBJECTS,
                    "No matching objects",
                    "No pods or deployments matched the provided selector/release in this namespace.",
                    List.of(new Evidence("Namespace", namespace)),
                    List.of(
                            "Verify the selector or release parameter is correct.",
                            "Check that resources exist in the namespace: kubectl get pods,deployments -n " + namespace,
                            "Confirm you're connected to the correct cluster and namespace."
                    )
            );

            return new DeploymentSummaryResponse(
                    OffsetDateTime.now(),
                    new Target(namespace, effectiveSelector, release),
                    new Health(OverallStatus.UNKNOWN, "0/0", Map.of(
                            "running", 0,
                            "pending", 0,
                            "crashLoop", 0,
                            "imagePullBackOff", 0,
                            "notReady", 0
                    )),
                    List.of(noMatchingObjectsFinding),
                    noMatchingObjectsFinding, // primaryFailure is set for UNKNOWN
                    null, // topWarning: N/A when no objects found
                    null, // primaryFailureDebug: N/A for short-circuit (not ranked)
                    new Objects(List.of(), List.of(), List.of(), List.of(), List.of())
            );
        }

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

        // Findings - Platform Failure Taxonomy (MVP 8 codes + Risk signals)
        List<Finding> findings = new ArrayList<>();

        // Run all detection rules - ERROR severity (failures)
        findings.addAll(detectBadConfig(podInfos, relatedEvents));
        findings.addAll(detectExternalSecretResolutionFailed(podInfos, relatedEvents));
        findings.addAll(detectImagePullFailed(podInfos, relatedEvents));
        findings.addAll(detectReadinessCheckFailed(podInfos, relatedEvents));
        findings.addAll(detectCrashLoop(podInfos, relatedEvents, backoffPods));
        findings.addAll(detectServiceSelectorMismatch(services, endpointsByService, pods));
        findings.addAll(detectInsufficientResources(podInfos, relatedEvents));
        findings.addAll(detectRbacDenied(relatedEvents));

        // Run risk signal detection - WARN severity (advisory)
        // Compute restart deltas (only warn on NEW restarts since last load)
        java.time.Instant now = java.time.Instant.now();
        restartBaselineStore.evictExpired(now);
        RestartBaselineStore.ScopeKey scopeKey
                = new RestartBaselineStore.ScopeKey(namespace, effectiveSelector, release);
        Map<String, Integer> restartDeltas = podInfos.stream()
                .collect(Collectors.toMap(
                        PodInfo::name,
                        p -> restartBaselineStore.deltaAndUpdate(scopeKey, p.name(), p.restarts(), now)
                ));
        findings.addAll(detectPodRestarts(podInfos, restartDeltas));
        findings.addAll(detectPodSandboxRecycle(relatedEvents));

        // Legacy findings (for backward compatibility)
        findings.addAll(findingsFromDeployments(deployments.values()));

        // NOTE: NO_MATCHING_OBJECTS check moved to top (short-circuit)
        findings = normalizeFindings(findings);
        OverallStatus overall = computeOverall(findings);
        Finding primaryFailure = selectPrimaryFailure(findings, overall);
        Finding topWarning = selectTopWarning(findings);
        String deploymentsReady = computeDeploymentsReadyString(deployments.values());

        return new DeploymentSummaryResponse(
                OffsetDateTime.now(),
                new Target(namespace, effectiveSelector, release),
                new Health(overall, deploymentsReady, breakdown),
                findings,
                primaryFailure, // The highest-priority finding for primary decision
                topWarning, // The highest-priority warning-level finding
                null, // TODO: Add primaryFailureDebug from ranker
                new Objects(workloadInfos, podInfos, relatedEvents, serviceInfos, endpointsInfos)
        );
    }

    // -------------------- query failure short-circuit --------------------
    /**
     * Build a FAIL response when the query itself is invalid. This is a
     * first-class failure category: tooling/query failures.
     *
     * CONTRACT: - overall = FAIL (not UNKNOWN, because the tool itself failed)
     * - primaryFailure = QUERY_INVALID - findings = single QUERY_INVALID
     * finding - objects = empty (cannot partially render)
     *
     * This prevents confusion and maintains trust in the tool.
     */
    private DeploymentSummaryResponse buildQueryInvalidResponse(
            String namespace,
            String selector,
            String release,
            String errorMessage
    ) {
        // Build evidence from the invalid inputs
        List<Evidence> evidence = new ArrayList<>();
        evidence.add(new Evidence("Namespace", namespace));
        if (StringUtils.hasText(selector)) {
            evidence.add(new Evidence("Selector", selector));
        }
        if (StringUtils.hasText(release)) {
            evidence.add(new Evidence("Release", release));
        }
        evidence.add(new Evidence("Error", errorMessage));

        // Determine next steps based on error type
        List<String> nextSteps = buildQueryInvalidNextSteps(errorMessage, selector, release);

        Finding queryInvalidFinding = new Finding(
                FailureCode.QUERY_INVALID,
                "Invalid query parameters",
                "The triage query could not be executed due to invalid input parameters or Kubernetes API rejection. "
                + "This indicates a problem with the query itself, not the workload.",
                evidence,
                nextSteps
        );

        return new DeploymentSummaryResponse(
                OffsetDateTime.now(),
                new Target(namespace, selector, release),
                new Health(OverallStatus.FAIL, "0/0", Map.of(
                        "running", 0,
                        "pending", 0,
                        "crashLoop", 0,
                        "imagePullBackOff", 0,
                        "notReady", 0
                )),
                List.of(queryInvalidFinding),
                queryInvalidFinding, // primaryFailure is set for FAIL
                null, // topWarning: N/A when query fails
                null, // primaryFailureDebug: N/A for short-circuit (not ranked)
                new Objects(List.of(), List.of(), List.of(), List.of(), List.of())
        );
    }

    /**
     * Build actionable next steps based on the query error.
     */
    private List<String> buildQueryInvalidNextSteps(String errorMessage, String selector, String release) {
        String msg = errorMessage != null ? errorMessage.toLowerCase() : "";

        List<String> steps = new ArrayList<>();

        // Selector-specific guidance
        if (msg.contains("selector") || msg.contains("label") || StringUtils.hasText(selector)) {
            steps.add("Verify label selector format follows Kubernetes syntax: key=value or key in (value1,value2)");
            steps.add("Avoid trailing '=' or malformed expressions like 'app=' or '=value'");
            steps.add("Test selector with kubectl: kubectl get pods -l \""
                    + (StringUtils.hasText(selector) ? selector : "<selector>") + "\" -n "
                    + (StringUtils.hasText(msg) ? msg.substring(0, Math.min(20, msg.length())) : "namespace"));
            steps.add("Common valid examples: app=my-app, tier=frontend, env!=prod");
        } else if (msg.contains("namespace")) {
            steps.add("Verify the namespace exists: kubectl get namespace");
            steps.add("Check for typos in the namespace parameter");
            steps.add("Confirm you have access to the namespace (RBAC permissions)");
        } else if (StringUtils.hasText(selector) == false && StringUtils.hasText(release) == false) {
            steps.add("Provide either 'selector' or 'release' parameter");
            steps.add("Example: ?namespace=cart&selector=app=cart-app");
            steps.add("Example: ?namespace=cart&release=cart-v1");
        } else {
            steps.add("Check the query parameters are valid Kubernetes identifiers");
            steps.add("Verify namespace, selector, or release parameters are correctly formatted");
            steps.add("Test query parameters with kubectl commands first");
        }

        steps.add("Review error details in evidence section above");

        return steps;
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

    /**
     * List pods, allowing ApiException to propagate for query failure handling.
     */
    private List<V1Pod> listPodsOrThrow(String namespace, String selector) throws ApiException {
        V1PodList list = coreV1.listNamespacedPod(namespace).labelSelector(selector).execute();
        return list.getItems() == null ? List.of() : list.getItems();
    }

    private Map<String, V1Deployment> listDeploymentsBySelector(String namespace, String selector) throws ApiException {
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
            // Query failures (400, 422) should propagate for QUERY_INVALID handling
            if (e.getCode() == 400 || e.getCode() == 422) {
                throw e;
            }
            // RBAC (403) or not found (404): proceed with pods-only view
            // This is not a query failure, just limited permissions
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

    // -------------------- Platform Failure Taxonomy Detection Rules --------------------
    /**
     * A. BAD_CONFIG Trigger: CreateContainerConfigError | Events:
     * secret/configmap not found (non-CSI)
     */
    private List<Finding> detectBadConfig(List<PodInfo> pods, List<EventInfo> events) {
        List<Evidence> evidence = new ArrayList<>();

        // Pattern 1: Pod container state waiting reason: CreateContainerConfigError
        pods.stream()
                .filter(p -> "CreateContainerConfigError".equalsIgnoreCase(p.reason()))
                .forEach(p -> evidence.add(new Evidence("Pod", p.name())));

        // Pattern 2: Events with secret/configmap not found (but NOT CSI-related)
        events.stream()
                .filter(e -> "Warning".equalsIgnoreCase(e.type()))
                .filter(e -> {
                    String msg = e.message() != null ? e.message().toLowerCase() : "";
                    String reason = e.reason() != null ? e.reason() : "";

                    // Look for secret/configmap errors but exclude CSI driver errors
                    boolean hasConfigError = msg.contains("secret") && msg.contains("not found")
                            || msg.contains("configmap") && msg.contains("not found")
                            || msg.contains("mountvolume.setup failed")
                            || "FailedMount".equalsIgnoreCase(reason);

                    // Exclude if it's CSI-related (those are EXTERNAL_SECRET_RESOLUTION_FAILED)
                    boolean isCsiRelated = msg.contains("secrets-store.csi")
                            || msg.contains("secretproviderclass")
                            || msg.contains("keyvault")
                            || msg.contains("key vault");

                    return hasConfigError && !isCsiRelated;
                })
                .forEach(e -> evidence.add(new Evidence("Event", e.involvedObjectName(), e.message())));

        if (evidence.isEmpty()) {
            return List.of();
        }

        return List.of(new Finding(
                FailureCode.BAD_CONFIG,
                "Bad configuration",
                "Pod cannot start due to missing or invalid Kubernetes configuration (Secret/ConfigMap/volume references).",
                evidence,
                List.of(
                        "Verify referenced Secret/ConfigMap exists in the namespace: kubectl get secrets,configmaps -n <namespace>",
                        "Verify key names in Secret/ConfigMap match the keys referenced in pod spec.",
                        "Check volumeMount names match volume definitions.",
                        "If using Helm: verify values rendered to expected resource names.",
                        "Review pod spec for typos in secret/configmap references."
                )
        ));
    }

    /**
     * B. EXTERNAL_SECRET_RESOLUTION_FAILED Trigger: CSI mount failures |
     * SecretProviderClass errors | Key Vault access issues
     */
    private List<Finding> detectExternalSecretResolutionFailed(List<PodInfo> pods, List<EventInfo> events) {
        List<Evidence> evidence = new ArrayList<>();

        // Pattern 1: Pods stuck in CreateContainerConfigError (may be Key Vault related)
        List<String> configErrorPods = pods.stream()
                .filter(p -> "CreateContainerConfigError".equalsIgnoreCase(p.reason()))
                .map(PodInfo::name)
                .toList();

        // Pattern 2: CSI driver mount failures from events
        boolean hasCsiEvidence = events.stream()
                .filter(e -> "Warning".equalsIgnoreCase(e.type()))
                .filter(e -> {
                    String reason = e.reason() != null ? e.reason() : "";
                    String message = e.message() != null ? e.message().toLowerCase() : "";

                    return ("FailedMount".equalsIgnoreCase(reason)
                            || "FailedAttachVolume".equalsIgnoreCase(reason)
                            || "MountVolume.SetUp failed".equalsIgnoreCase(reason))
                            && (message.contains("secrets-store.csi.k8s.io")
                            || message.contains("secretproviderclass")
                            || message.contains("keyvault")
                            || message.contains("key vault")
                            || message.contains("azure") && message.contains("vault")
                            || message.contains("failed to mount")
                            || message.contains("permission denied")
                            || message.contains("forbidden"));
                })
                .peek(e -> evidence.add(new Evidence("Event", e.involvedObjectName(), e.message())))
                .findAny()
                .isPresent();

        // Only add pod evidence if we have CSI-related events
        if (hasCsiEvidence) {
            configErrorPods.forEach(podName -> evidence.add(new Evidence("Pod", podName)));
        }

        if (evidence.isEmpty()) {
            return List.of();
        }

        return List.of(new Finding(
                FailureCode.EXTERNAL_SECRET_RESOLUTION_FAILED,
                "External secret mount failed (CSI / Key Vault)",
                "Pod cannot mount external secrets via SecretProviderClass; container will not start.",
                evidence,
                List.of(
                        "Confirm SecretProviderClass exists in the same namespace: kubectl get secretproviderclass -n <namespace>",
                        "Verify Key Vault name/URI and object names match exactly (case-sensitive).",
                        "Verify workload identity/managed identity has 'Get' permission on secrets in Key Vault.",
                        "Check tenant ID and client ID match in federated identity binding.",
                        "Confirm CSI driver is installed: kubectl get pods -n kube-system | grep csi-secrets-store",
                        "Check pod service account is correctly annotated for workload identity."
                )
        ));
    }

    /**
     * C. IMAGE_PULL_FAILED Trigger: ImagePullBackOff | ErrImagePull | Events
     * with pull failures
     */
    private List<Finding> detectImagePullFailed(List<PodInfo> pods, List<EventInfo> events) {
        List<Evidence> evidence = new ArrayList<>();

        // Pattern 1: Pod waiting reasons
        pods.stream()
                .filter(p -> "ImagePullBackOff".equalsIgnoreCase(p.reason())
                || "ErrImagePull".equalsIgnoreCase(p.reason()))
                .forEach(p -> evidence.add(new Evidence("Pod", p.name())));

        // Pattern 2: Events with image pull failures
        events.stream()
                .filter(e -> "Warning".equalsIgnoreCase(e.type()))
                .filter(e -> {
                    String reason = e.reason() != null ? e.reason() : "";
                    String message = e.message() != null ? e.message().toLowerCase() : "";

                    return "Failed".equalsIgnoreCase(reason) && message.contains("pull")
                            || message.contains("manifest unknown")
                            || message.contains("unauthorized")
                            || message.contains("image pull")
                            || "ErrImagePull".equalsIgnoreCase(reason)
                            || "ImagePullBackOff".equalsIgnoreCase(reason);
                })
                .forEach(e -> evidence.add(new Evidence("Event", e.involvedObjectName(), e.message())));

        if (evidence.isEmpty()) {
            return List.of();
        }

        return List.of(new Finding(
                FailureCode.IMAGE_PULL_FAILED,
                "Image pull failed",
                "Container image cannot be pulled (authentication, missing tag, or registry access issue).",
                evidence,
                List.of(
                        "Verify image tag exists in the registry.",
                        "Verify imagePullSecrets are configured if using a private registry.",
                        "Check network/egress policy allows access to the registry.",
                        "Confirm registry URL is correct (typos in image name/tag).",
                        "If using ACR: verify AKS has pull permissions via managed identity or service principal.",
                        "Test image pull manually: docker pull <image:tag>"
                )
        ));
    }

    /**
     * D. READINESS_CHECK_FAILED Trigger: Pod Running but Ready=False for >60s |
     * Readiness probe failures
     */
    private List<Finding> detectReadinessCheckFailed(List<PodInfo> pods, List<EventInfo> events) {
        List<Evidence> evidence = new ArrayList<>();

        // Pattern 1: Pods running but not ready
        pods.stream()
                .filter(p -> "Running".equalsIgnoreCase(p.phase()) && !p.ready())
                .forEach(p -> evidence.add(new Evidence("Pod", p.name())));

        // Pattern 2: Readiness probe failure events
        events.stream()
                .filter(e -> "Warning".equalsIgnoreCase(e.type()))
                .filter(e -> {
                    String message = e.message() != null ? e.message().toLowerCase() : "";
                    return message.contains("readiness probe failed")
                            || message.contains("liveness probe failed");
                })
                .forEach(e -> evidence.add(new Evidence("Event", e.involvedObjectName(), e.message())));

        if (evidence.isEmpty()) {
            return List.of();
        }

        return List.of(new Finding(
                FailureCode.READINESS_CHECK_FAILED,
                "Readiness check failed",
                "Pods are running but never become Ready (readiness probe or application health check failing).",
                evidence,
                List.of(
                        "Verify readiness probe path/port matches the application endpoint.",
                        "Check application logs for startup errors: kubectl logs <pod> -n <namespace>",
                        "Verify dependencies are reachable (database, cache, external APIs).",
                        "Confirm service port mapping matches container port.",
                        "Test readiness endpoint manually: kubectl exec <pod> -- wget -O- localhost:<port>/<path>",
                        "Check if initialDelaySeconds is too short for app startup time."
                )
        ));
    }

    /**
     * E. CRASH_LOOP Trigger: CrashLoopBackOff | Restarts > 0 | BackOff events
     */
    private List<Finding> detectCrashLoop(List<PodInfo> pods, List<EventInfo> events, Set<String> backoffPods) {
        List<Evidence> evidence = new ArrayList<>();

        // Pattern 1: Pods with CrashLoopBackOff reason or in backoff set
        pods.stream()
                .filter(p -> "CrashLoopBackOff".equalsIgnoreCase(p.reason())
                || backoffPods.contains(p.name()))
                .forEach(p -> evidence.add(new Evidence("Pod", p.name(),
                "Restarts: " + p.restarts() + ", Reason: " + p.reason())));

        // Pattern 2: BackOff events
        events.stream()
                .filter(e -> "Warning".equalsIgnoreCase(e.type()))
                .filter(e -> "BackOff".equalsIgnoreCase(e.reason())
                || (e.message() != null && e.message().contains("Back-off restarting failed container")))
                .forEach(e -> evidence.add(new Evidence("Event", e.involvedObjectName(), e.message())));

        if (evidence.isEmpty()) {
            return List.of();
        }

        return List.of(new Finding(
                FailureCode.CRASH_LOOP,
                "Crash loop detected",
                "Containers are repeatedly crashing (CrashLoopBackOff, OOM, or non-zero exit codes).",
                evidence,
                List.of(
                        "Inspect last termination reason: kubectl describe pod <pod> -n <namespace>",
                        "Check logs from previous container instance: kubectl logs <pod> -n <namespace> --previous",
                        "Look for OOMKilled (out of memory): increase memory limits if needed.",
                        "Validate required environment variables are set correctly.",
                        "Check for application startup errors or missing dependencies.",
                        "Review exit codes: 137 = OOMKilled, 143 = SIGTERM, others = app error"
                )
        ));
    }

    /**
     * F. SERVICE_SELECTOR_MISMATCH Trigger: Service exists, but endpoints = 0
     * while pods exist
     */
    private List<Finding> detectServiceSelectorMismatch(List<V1Service> services,
            Map<String, V1Endpoints> endpointsByService,
            List<V1Pod> pods) {
        List<Evidence> evidence = new ArrayList<>();

        for (V1Service svc : services) {
            String name = svc.getMetadata() != null ? svc.getMetadata().getName() : null;
            if (!StringUtils.hasText(name)) {
                continue;
            }

            V1Endpoints eps = endpointsByService.get(name);
            int ready = countReadyAddresses(eps);
            int notReady = countNotReadyAddresses(eps);

            // Service has no ready endpoints, but pods exist
            if (ready == 0 && !pods.isEmpty()) {
                evidence.add(new Evidence("Service", name,
                        "0 ready endpoints (" + notReady + " not ready)"));
                evidence.add(new Evidence("Endpoints", name));
            }
        }

        if (evidence.isEmpty()) {
            return List.of();
        }

        return List.of(new Finding(
                FailureCode.SERVICE_SELECTOR_MISMATCH,
                "Service selector mismatch",
                "Service has zero endpoints due to label/selector mismatch or pods not ready.",
                evidence,
                List.of(
                        "Compare Service selector labels vs pod labels: kubectl describe service <service> -n <namespace>",
                        "Check pod labels: kubectl get pods --show-labels -n <namespace>",
                        "If pods exist but not Ready, fix readiness issues first.",
                        "Verify service is in the same namespace as the pods.",
                        "Test label matching: kubectl get pods -l <selector> -n <namespace>"
                )
        ));
    }

    /**
     * G. INSUFFICIENT_RESOURCES Trigger: Pod Pending + Unschedulable | Events:
     * Insufficient cpu/memory
     */
    private List<Finding> detectInsufficientResources(List<PodInfo> pods, List<EventInfo> events) {
        List<Evidence> evidence = new ArrayList<>();

        // Pattern 1: Pods stuck in Pending
        pods.stream()
                .filter(p -> "Pending".equalsIgnoreCase(p.phase()))
                .forEach(p -> evidence.add(new Evidence("Pod", p.name(), "Phase: Pending")));

        // Pattern 2: Scheduling failure events
        events.stream()
                .filter(e -> "Warning".equalsIgnoreCase(e.type()))
                .filter(e -> {
                    String reason = e.reason() != null ? e.reason() : "";
                    String message = e.message() != null ? e.message().toLowerCase() : "";

                    return "FailedScheduling".equalsIgnoreCase(reason)
                            || message.contains("insufficient cpu")
                            || message.contains("insufficient memory")
                            || message.contains("unschedulable")
                            || message.contains("quota exceeded")
                            || message.contains("evicted")
                            || message.contains("taint");
                })
                .forEach(e -> evidence.add(new Evidence("Event", e.involvedObjectName(), e.message())));

        if (evidence.isEmpty()) {
            return List.of();
        }

        return List.of(new Finding(
                FailureCode.INSUFFICIENT_RESOURCES,
                "Insufficient resources",
                "Pod scheduling is blocked or evicted due to insufficient CPU/memory, node capacity, or quotas.",
                evidence,
                List.of(
                        "Check resource requests/limits vs node capacity: kubectl describe nodes",
                        "Check namespace resource quotas: kubectl get resourcequotas -n <namespace>",
                        "Check node taints/tolerations: kubectl describe nodes | grep Taint",
                        "View pending pod scheduling issues: kubectl describe pod <pod> -n <namespace>",
                        "Consider reducing resource requests or scaling cluster nodes.",
                        "Check for pod disruption budgets that may block evictions."
                )
        ));
    }

    /**
     * H. RBAC_DENIED Trigger: API responses with Forbidden | RBAC denied events
     */
    private List<Finding> detectRbacDenied(List<EventInfo> events) {
        List<Evidence> evidence = new ArrayList<>();

        // Pattern: Events indicating RBAC/Forbidden errors
        events.stream()
                .filter(e -> "Warning".equalsIgnoreCase(e.type()))
                .filter(e -> {
                    String message = e.message() != null ? e.message().toLowerCase() : "";
                    return message.contains("forbidden")
                            || message.contains("rbac")
                            || message.contains("unauthorized")
                            || message.contains("access denied")
                            || message.contains("permission denied");
                })
                .forEach(e -> evidence.add(new Evidence("Event", e.involvedObjectName(), e.message())));

        if (evidence.isEmpty()) {
            return List.of();
        }

        return List.of(new Finding(
                FailureCode.RBAC_DENIED,
                "RBAC permission denied",
                "Tool or workload is denied by Kubernetes RBAC for required operations.",
                evidence,
                List.of(
                        "Confirm triage service account has list/get/watch permissions for required resources.",
                        "Check workload service account permissions: kubectl describe serviceaccount <sa> -n <namespace>",
                        "Review ClusterRole/Role bindings: kubectl get clusterrolebindings,rolebindings -n <namespace>",
                        "Test access: kubectl auth can-i <verb> <resource> --as=system:serviceaccount:<namespace>:<sa>",
                        "Check pod security policies or admission controllers that may block operations."
                )
        ));
    }

    // -------------------- Risk Signal Detection (WARN severity) --------------------
    /**
     * RISK SIGNAL: POD_RESTARTS_DETECTED
     *
     * Detects restarts SINCE LAST LOAD (not cumulative since pod creation).
     * Uses RestartBaselineStore to track delta.
     *
     * Why delta matters: - Kubernetes restart count is cumulative (never
     * resets) - Without delta tracking, pods warn forever after first restart -
     * With delta tracking, we only warn on NEW restarts
     *
     * @param pods List of pods
     * @param restartDeltas Map of podName -> delta restarts since last load
     */
    private List<Finding> detectPodRestarts(List<PodInfo> pods, Map<String, Integer> restartDeltas) {
        List<Evidence> evidence = new ArrayList<>();
        int totalDelta = 0;
        int totalRestarts = 0; // Track cumulative restarts for first-load scenarios

        // ⭐ THRESHOLD: Only warn on first load if restarts >= this value
        // Rationale: 0-2 lifetime restarts = normal operations (deployments, rolling updates)
        // 3+ restarts = instability signal worth investigating
        // This balances demo clarity (Healthy App = PASS, Restart Warning = WARN) with production use
        final int FIRST_LOAD_THRESHOLD = 3;

        // Pattern: Pod is Running AND Ready AND has significant restarts
        for (PodInfo p : pods) {
            if (!"Running".equalsIgnoreCase(p.phase()) || !p.ready()) {
                continue;
            }

            int delta = restartDeltas.getOrDefault(p.name(), 0);
            int absoluteRestarts = p.restarts();

            // ⭐ DEMO FIX: Warn if:
            // 1. Delta > 0 (new restarts since last load - always warn)
            // 2. First load AND restarts >= threshold (demo scenario detection)
            // This balances demo visibility with production false-positive reduction
            boolean shouldWarn = delta > 0 || (delta == 0 && absoluteRestarts >= FIRST_LOAD_THRESHOLD);

            if (!shouldWarn) {
                continue;
            }

            totalDelta += delta;
            totalRestarts += absoluteRestarts;

            // Build evidence message showing delta + total
            String evidenceMsg;
            if (delta > 0) {
                evidenceMsg = "+" + delta + " since last LOAD (total=" + absoluteRestarts + ")";
            } else {
                // First load scenario: show cumulative restarts
                evidenceMsg = "Total restarts: " + absoluteRestarts + " (first load - establishing baseline)";
            }

            if (StringUtils.hasText(p.reason())) {
                evidenceMsg += " - Last reason: " + p.reason();
            }

            evidence.add(new Evidence("Pod", p.name(), evidenceMsg));
        }

        if (evidence.isEmpty()) {
            return List.of();
        }

        // Build explanation
        String explanation;
        if (totalDelta > 0) {
            // Delta-based explanation (subsequent loads)
            if (evidence.size() == 1) {
                explanation = "Pod restarted " + totalDelta + " time" + (totalDelta > 1 ? "s" : "")
                        + " since last LOAD but is currently running.";
            } else {
                explanation = evidence.size() + " pods restarted " + totalDelta + " total times "
                        + "since last LOAD but are currently running.";
            }
        } else {
            // Cumulative-based explanation (first load)
            if (evidence.size() == 1) {
                explanation = "Pod has " + totalRestarts + " restart" + (totalRestarts > 1 ? "s" : "")
                        + " but is currently running.";
            } else {
                explanation = evidence.size() + " pods have " + totalRestarts + " total restarts "
                        + "but are currently running.";
            }
        }

        explanation += " This may indicate transient crashes, config reloads, or unstable startup behavior.";

        return List.of(new Finding(
                FailureCode.POD_RESTARTS_DETECTED,
                "Pod restarts detected",
                explanation,
                evidence,
                List.of(
                        "Review pod logs for crash patterns: kubectl logs <pod> -n <namespace> --previous",
                        "Check if restarts correlate with deployments or config changes.",
                        "Verify readiness/liveness probe settings are appropriate for startup time.",
                        "Look for OOM events (exit code 137): kubectl describe pod <pod> -n <namespace>",
                        "Consider if restarts are expected (e.g., app restarts on config reload)."
                )
        ));
    }

    /**
     * RISK SIGNAL: POD_SANDBOX_RECYCLE Trigger: SandboxChanged events (pod
     * sandbox changed, will be killed and re-created) This is a strong early
     * smell of instability
     */
    private List<Finding> detectPodSandboxRecycle(List<EventInfo> events) {
        List<Evidence> evidence = new ArrayList<>();

        // Pattern: SandboxChanged events
        events.stream()
                .filter(e -> "SandboxChanged".equalsIgnoreCase(e.reason()))
                .forEach(e -> evidence.add(new Evidence("Event", e.involvedObjectName(), e.message())));

        if (evidence.isEmpty()) {
            return List.of();
        }

        return List.of(new Finding(
                FailureCode.POD_SANDBOX_RECYCLE,
                "Pod sandbox recycled",
                "Pod sandbox changed and pod will be killed and re-created. "
                + "This may indicate node-level issues, runtime problems, or network policy changes.",
                evidence,
                List.of(
                        "Check node health: kubectl describe node <node>",
                        "Review container runtime logs on the node.",
                        "Check for network policy or CNI changes.",
                        "Look for node resource pressure or eviction events.",
                        "Verify pod security policies or admission webhooks."
                )
        ));
    }

    /**
     * Legacy deployment findings (for backward compatibility)
     */
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
                        FailureCode.ROLLOUT_STUCK,
                        "Deployment rollout stuck",
                        "Deployment " + name + " rollout appears stuck (ProgressDeadlineExceeded).",
                        List.of(new Evidence("Deployment", name)),
                        List.of(
                                "Check deployment status: kubectl describe deployment " + name,
                                "Review replica set status: kubectl get rs -n <namespace>",
                                "Check for pod-level issues preventing rollout.",
                                "Consider rolling back: kubectl rollout undo deployment/" + name
                        )
                ));
            } else if (desired > 0 && ready == 0) {
                out.add(new Finding(
                        FailureCode.NO_READY_PODS,
                        "No ready pods",
                        "Deployment " + name + " has 0 ready replicas out of " + desired + ".",
                        List.of(new Evidence("Deployment", name)),
                        List.of(
                                "Check pod status: kubectl get pods -n <namespace>",
                                "Inspect deployment events: kubectl describe deployment " + name,
                                "Review pod-level failures (image pull, crash loop, config errors)."
                        )
                ));
            }
        }

        return out;
    }

    /**
     * Normalize findings by removing redundant ones. Taxonomy-based findings
     * are mutually exclusive at the primary level, but we may have multiple
     * findings of different types.
     */
    private List<Finding> normalizeFindings(List<Finding> findings) {
        boolean hasCrashLoop = findings.stream().anyMatch(f -> f.code() == FailureCode.CRASH_LOOP);

        return findings.stream()
                // If both CRASH_LOOP and READINESS_CHECK_FAILED exist, crash is more critical
                .filter(f -> !(hasCrashLoop && f.code() == FailureCode.READINESS_CHECK_FAILED))
                .toList();
    }

    /**
     * ==================== PRIMARY FAILURE SELECTION ====================
     * Select the primary failure using priority ordering.
     *
     * CONTRACT RULE: - primaryFailure is set ONLY when overall == FAIL or
     * overall == UNKNOWN - Otherwise, primaryFailure = null - Warnings must
     * NEVER populate primaryFailure
     *
     * This prevents scary red UIs when everything is actually working fine.
     *
     * Priority order (from spec): 1. EXTERNAL_SECRET_RESOLUTION_FAILED 2.
     * BAD_CONFIG 3. IMAGE_PULL_FAILED 4. INSUFFICIENT_RESOURCES 5. RBAC_DENIED
     * 6. CRASH_LOOP 7. READINESS_CHECK_FAILED 8. SERVICE_SELECTOR_MISMATCH
     */
    private Finding selectPrimaryFailure(List<Finding> findings, OverallStatus overall) {
        // Contract enforcement: primaryFailure only for FAIL or UNKNOWN
        if (overall != OverallStatus.FAIL && overall != OverallStatus.UNKNOWN) {
            return null;
        }

        if (findings.isEmpty()) {
            return null;
        }

        // Select highest priority ERROR-severity finding
        // (WARN findings have lower priority and won't be selected here)
        return findings.stream()
                .filter(f -> f.severity() == Severity.ERROR || f.severity() == Severity.HIGH)
                .min(Comparator.comparingInt(Finding::getPriority))
                .orElse(null);
    }

    /**
     * ==================== TOP WARNING SELECTION ==================== Select
     * the top warning using priority ordering.
     *
     * CONTRACT RULE: - topWarning is the highest priority finding with severity
     * == WARN or MED - topWarning can be present even when overall == PASS (if
     * warnings exist) - topWarning is independent of primaryFailure
     *
     * This gives UI a stable "top warning" to display without re-implementing
     * prioritization.
     */
    private Finding selectTopWarning(List<Finding> findings) {
        if (findings.isEmpty()) {
            return null;
        }

        // Select highest priority WARN-severity finding
        return findings.stream()
                .filter(f -> f.severity() == Severity.WARN || f.severity() == Severity.MED)
                .min(Comparator.comparingInt(Finding::getPriority))
                .orElse(null);
    }

    // -------------------- status helpers --------------------
    private OverallStatus computeOverall(List<Finding> findings) {
        // Check for NO_MATCHING_OBJECTS first - cannot assess if nothing found
        boolean noMatching = findings.stream()
                .anyMatch(f -> f.code() == FailureCode.NO_MATCHING_OBJECTS);
        if (noMatching) {
            return OverallStatus.UNKNOWN;
        }

        // Check for ERROR or legacy HIGH severity
        // These are actual failures that should fail overall
        boolean hasError = findings.stream().anyMatch(f
                -> f.severity() == Severity.ERROR || f.severity() == Severity.HIGH);
        if (hasError) {
            return OverallStatus.FAIL;
        }

        // IMPORTANT: WARN findings do NOT fail overall (overall != FAIL)
        // They are risk signals / advisory findings
        // But they DO set overall = WARN (not PASS)
        // Check both WARN and legacy MED severity
        // 
        // ⭐ This catches POD_RESTARTS_DETECTED (Severity.WARN) and surfaces it properly
        // Ensures "Restart Warning" demo shows WARN, not PASS
        boolean hasWarning = findings.stream().anyMatch(f
                -> f.severity() == Severity.WARN || f.severity() == Severity.MED);
        if (hasWarning) {
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
