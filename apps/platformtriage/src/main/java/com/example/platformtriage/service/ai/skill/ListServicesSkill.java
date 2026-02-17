package com.example.platformtriage.service.ai.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.platformtriage.model.dto.EndpointsInfo;
import com.example.platformtriage.model.dto.ServiceInfo;
import com.example.platformtriage.service.ai.PlatformTriageSkill;
import com.example.platformtriage.service.ai.PlatformTriageSkillContext;
import com.example.platformtriage.service.ai.PlatformTriageSkillMetadata;
import com.example.platformtriage.service.ai.PlatformTriageSkillResult;
import com.example.platformtriage.service.ai.PlatformTriageTools;

@Component
public class ListServicesSkill implements PlatformTriageSkill {

    private static final int MAX_SERVICES_IN_RESPONSE = 25;

    @Override
    public PlatformTriageSkillMetadata metadata() {
        return new PlatformTriageSkillMetadata(
                PlatformTriageTools.LIST_SERVICES,
                "List services and endpoint readiness from the current summary.",
                true
        );
    }

    @Override
    public PlatformTriageSkillResult execute(PlatformTriageSkillContext context) {
        if (!context.hasSummary()) {
            return new PlatformTriageSkillResult(
                    "clarify",
                    "I can list services, but I need a deployment summary loaded first.",
                    List.of("No summary loaded."),
                    List.of("Load a summary first."),
                    List.of("Try: check namespace cart with selector app=cart-app"),
                    metadata().tool(),
                    false,
                    null
            );
        }

        List<ServiceInfo> services = context.summary().objects() == null
                || context.summary().objects().services() == null
                ? List.of()
                : context.summary().objects().services();

        if (services.isEmpty()) {
            return new PlatformTriageSkillResult(
                    "tool",
                    "No services matched the current selector scope.",
                    List.of(
                            "Namespace: " + context.summary().target().namespace(),
                            "Selector: " + context.summary().target().selector()
                    ),
                    List.of("Check that service discovery is expected for this workload."),
                    List.of("Load another namespace/selector for service details."),
                    metadata().tool(),
                    true,
                    services
            );
        }

        List<EndpointsInfo> endpoints = context.summary().objects() == null
                || context.summary().objects().endpoints() == null
                ? List.of()
                : context.summary().objects().endpoints();

        List<ServiceView> views = services.stream()
                .map(s -> toServiceView(s, lookupEndpoint(s.name(), endpoints)))
                .toList();

        List<ServiceView> limited = views.stream().limit(MAX_SERVICES_IN_RESPONSE).toList();
        List<String> keyFindings = new ArrayList<>();
        keyFindings.add("Namespace: " + context.summary().target().namespace());
        keyFindings.add("Services returned: " + limited.size() + " / " + views.size());

        for (ServiceView view : limited) {
            keyFindings.add(String.format(
                    "Service=%s, type=%s, ready=%d, notReady=%d",
                    view.name(),
                    view.type(),
                    view.readyAddresses(),
                    view.notReadyAddresses()
            ));
        }

        List<String> nextSteps = new ArrayList<>();
        nextSteps.add("Run pod summary and inspect pods for any readiness mismatch.");
        nextSteps.add("If ready=0 on a service, check deployment endpoints or target selectors.");
        nextSteps.add("Open events and look for Service type or endpoint-related warnings.");

        return new PlatformTriageSkillResult(
                "tool",
                "Services returned for current namespace selector scope.",
                keyFindings,
                nextSteps,
                List.of("Need details for a specific service or endpoint checks?"),
                metadata().tool(),
                true,
                limited
        );
    }

    private ServiceView toServiceView(ServiceInfo service, EndpointsInfo endpoint) {
        int ready = endpoint == null ? 0 : endpoint.readyAddresses();
        int notReady = endpoint == null ? 0 : endpoint.notReadyAddresses();
        String selectorText = service.selector().isEmpty()
                ? "-"
                : service.selector().entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .reduce((left, right) -> left + ", " + right)
                        .orElse("-");
        return new ServiceView(
                service.name(),
                service.type(),
                selectorText,
                service.ports(),
                ready,
                notReady
        );
    }

    private EndpointsInfo lookupEndpoint(String serviceName, List<EndpointsInfo> endpoints) {
        for (EndpointsInfo endpoint : endpoints) {
            if (serviceName.equals(endpoint.serviceName())) {
                return endpoint;
            }
        }
        return null;
    }

    private record ServiceView(
            String name,
            String type,
            String selector,
            List<String> ports,
            int readyAddresses,
            int notReadyAddresses
    ) {}
}
