package com.example.platformtriage.controller;

import com.example.common.export.ExportBundle;
import com.example.platformtriage.model.response.DeploymentSummaryResponse;
import com.example.platformtriage.service.DeploymentDoctorService;
import com.example.platformtriage.service.ExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/deployment")
public class DeploymentDoctorController {

  private static final Logger log = LoggerFactory.getLogger(DeploymentDoctorController.class);
  private final DeploymentDoctorService service;
  private final ExportService exportService;

  public DeploymentDoctorController(DeploymentDoctorService service, ExportService exportService) {
    this.service = service;
    this.exportService = exportService;
  }

  @GetMapping("/summary")
  public DeploymentSummaryResponse getSummary(
      @RequestParam String namespace,
      @RequestParam(required = false) String selector,
      @RequestParam(required = false) String release,
      @RequestParam(defaultValue = "50") int limitEvents
  ) {
    log.info("📋 Fetching deployment summary for namespace: {}, selector: {}, release: {}", 
        namespace, selector, release);
    try {
      DeploymentSummaryResponse response = service.getSummary(namespace, selector, release, limitEvents);
      log.info("✓ Successfully fetched deployment summary");
      return response;
    } catch (Exception e) {
      log.error("✗ Error fetching deployment summary: {}", e.getMessage(), e);
      throw e;
    }
  }

  @GetMapping("/diagnostics/export")
  public ResponseEntity<ExportBundle> exportDiagnostics(
      @RequestParam String namespace,
      @RequestParam(required = false) String selector,
      @RequestParam(required = false) String release,
      @RequestParam(defaultValue = "50") int limitEvents
  ) {
    log.info("📦 Exporting deployment diagnostics for namespace: {}, selector: {}, release: {}", 
        namespace, selector, release);
    try {
      // Get the deployment summary first
      DeploymentSummaryResponse summary = service.getSummary(namespace, selector, release, limitEvents);
      
      // Convert to export bundle
      ExportBundle exportBundle = exportService.createExportBundle(summary);
      
      log.info("✓ Successfully created export bundle");
      return ResponseEntity.ok(exportBundle);
    } catch (Exception e) {
      log.error("✗ Error exporting deployment diagnostics: {}", e.getMessage(), e);
      throw e;
    }
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> handleException(Exception e) {
    log.error("💥 Exception in DeploymentDoctorController: {}", e.getMessage(), e);
    
    String userMessage;
    if (e.getMessage() != null && e.getMessage().contains("kubeconfig")) {
      userMessage = "Kubernetes not configured. Please ensure kubectl is installed and ~/.kube/config is valid.";
    } else if (e.getMessage() != null && e.getMessage().contains("connect")) {
      userMessage = "Cannot connect to Kubernetes cluster. Please check your cluster connection.";
    } else if (e.getMessage() != null && e.getMessage().contains("Unauthorized")) {
      userMessage = "Unauthorized to access Kubernetes. Please check your credentials.";
    } else if (e.getMessage() != null && e.getMessage().contains("Forbidden")) {
      userMessage = "Forbidden: insufficient permissions to access namespace. Check RBAC settings.";
    } else {
      userMessage = "Failed to fetch deployment data: " + (e.getMessage() != null ? e.getMessage() : "Unknown error");
    }
    
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(Map.of(
            "error", "DeploymentSummaryError",
            "message", userMessage,
            "detail", e.getMessage() != null ? e.getMessage() : "No details available"
        ));
  }
}
