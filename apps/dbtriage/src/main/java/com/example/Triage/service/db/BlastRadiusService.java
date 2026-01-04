package com.example.Triage.service.db;

import com.example.Triage.model.dto.BlastRadiusItem;
import com.example.Triage.model.dto.DriftItem;
import com.example.Triage.model.dto.DriftSection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service to generate blast radius analysis (likely symptoms) from drift items
 */
@Service
@Slf4j
public class BlastRadiusService {

    public List<BlastRadiusItem> generateBlastRadius(List<DriftSection> driftSections) {
        List<BlastRadiusItem> allItems = new ArrayList<>();

        for (DriftSection section : driftSections) {
            if (!section.availability().available()) {
                continue;
            }

            for (DriftItem item : section.driftItems()) {
                BlastRadiusItem radiusItem = analyzeSymptoms(item);
                if (radiusItem != null) {
                    allItems.add(radiusItem);
                }
            }
        }

        // Group similar low-value items
        return groupAndPrioritize(allItems);
    }
    
    /**
     * Group similar items and prioritize for display
     */
    private List<BlastRadiusItem> groupAndPrioritize(List<BlastRadiusItem> allItems) {
        List<BlastRadiusItem> result = new ArrayList<>();
        
        // Separate unique high-value items from generic ones
        List<BlastRadiusItem> uniqueItems = new ArrayList<>();
        List<BlastRadiusItem> genericIndexMismatches = new ArrayList<>();
        
        for (BlastRadiusItem item : allItems) {
            if ("Index definition mismatch".equals(item.driftType()) && 
                item.riskLevel().equals("Medium") &&
                item.likelySymptoms().stream().anyMatch(s -> s.contains("Different query execution plans"))) {
                genericIndexMismatches.add(item);
            } else {
                uniqueItems.add(item);
            }
        }
        
        // Add all unique items
        result.addAll(uniqueItems);
        
        // Group generic index mismatches if > 1
        if (genericIndexMismatches.size() > 1) {
            result.add(new BlastRadiusItem(
                    "Multiple indexes",
                    "Index definition mismatches",
                    String.format("%d indexes", genericIndexMismatches.size()),
                    "Performance",
                    "Medium",
                    List.of("Inconsistent query performance between environments",
                            "Different execution plans may cause timing differences"),
                    true,  // isGroupRepresentative
                    genericIndexMismatches.size()
            ));
        } else if (genericIndexMismatches.size() == 1) {
            result.add(genericIndexMismatches.get(0));
        }
        
        // Sort by risk: High, Medium, Low
        result.sort((a, b) -> {
            int riskCompare = getRiskOrder(a.riskLevel()) - getRiskOrder(b.riskLevel());
            if (riskCompare != 0) return riskCompare;
            return a.driftType().compareTo(b.driftType());
        });
        
        return result;
    }
    
    private int getRiskOrder(String risk) {
        return switch (risk) {
            case "High" -> 1;
            case "Medium" -> 2;
            case "Low" -> 3;
            default -> 4;
        };
    }

    private BlastRadiusItem analyzeSymptoms(DriftItem item) {
        List<String> symptoms = new ArrayList<>();
        String driftType = "";
        String driftSubtype = null;

        switch (item.category()) {
            case "Compatibility":
                if (item.attribute().equals("exists")) {
                    if (item.sourceValue().equals(true) && item.targetValue().equals(false)) {
                        // Missing in target
                        if (item.objectName().contains(".")) {
                            // Column missing
                            driftType = "Missing column";
                            driftSubtype = "Column removed";
                            symptoms.add("INSERT/UPDATE fails: column \"" + extractColumnName(item.objectName()) + "\" does not exist");
                            symptoms.add("SELECT fails if application queries this column");
                            symptoms.add("Application errors: NullPointerException or field mapping failures");
                        } else {
                            // Table missing
                            driftType = "Missing table";
                            driftSubtype = "Table removed";
                            symptoms.add("INSERT/UPDATE/DELETE fails: relation \"" + item.objectName() + "\" does not exist");
                            symptoms.add("SELECT queries fail completely");
                            symptoms.add("Application startup failures if table is accessed during initialization");
                        }
                    }
                } else if (item.attribute().equals("data_type")) {
                    driftType = "Column type mismatch";
                    driftSubtype = String.format("Type changed: %s → %s", item.sourceValue(), item.targetValue());
                    symptoms.add("INSERT/UPDATE fails: column type mismatch errors");
                    symptoms.add("Data truncation or precision loss");
                    symptoms.add("Application type casting errors");
                } else if (item.attribute().equals("is_nullable")) {
                    driftType = "Nullability mismatch";
                    driftSubtype = String.format("Nullable: %s → %s", item.sourceValue(), item.targetValue());
                    if (item.sourceValue().equals(true) && item.targetValue().equals(false)) {
                        symptoms.add("INSERT/UPDATE with NULL values fails: NOT NULL constraint violation");
                    } else {
                        symptoms.add("Unexpected NULL values may cause application logic errors");
                    }
                }
                break;

            case "Performance":
                if (item.attribute().equals("exists") && item.sourceValue().equals(true) && item.targetValue().equals(false)) {
                    driftType = "Missing index";
                    driftSubtype = "Index removed";
                    symptoms.add("Slow queries / table scans on " + extractTableName(item.objectName()));
                    symptoms.add("Query timeouts under load");
                    symptoms.add("CPU spikes during peak usage");
                    if (item.riskLevel() != null && item.riskLevel().equals("High")) {
                        symptoms.add("CRITICAL: This may be a unique or primary key index - data integrity at risk");
                    }
                } else if (item.attribute().equals("definition")) {
                    driftType = "Index definition mismatch";
                    // Try to determine what changed
                    driftSubtype = analyzeIndexDifference(item.sourceValue().toString(), item.targetValue().toString());
                    symptoms.add("Different query execution plans between environments");
                    symptoms.add("Inconsistent query performance");
                }
                break;

            default:
                return null;  // No symptoms for this category
        }

        if (symptoms.isEmpty()) {
            return null;
        }

        return new BlastRadiusItem(
                item.objectName(),
                driftType,
                driftSubtype,
                item.category(),
                item.riskLevel() != null ? item.riskLevel() : "Medium",
                symptoms,
                false,  // not a group representative
                1       // single item
        );
    }
    
    /**
     * Analyze what changed in an index definition
     */
    private String analyzeIndexDifference(String sourceDef, String targetDef) {
        if (sourceDef == null || targetDef == null) {
            return "Definition differs";
        }
        
        // Check for method changes
        if (sourceDef.contains(" USING btree ") && targetDef.contains(" USING gin ")) {
            return "Method differs: btree → gin";
        }
        if (sourceDef.contains(" USING gin ") && targetDef.contains(" USING btree ")) {
            return "Method differs: gin → btree";
        }
        
        // Check for uniqueness changes
        boolean sourceUnique = sourceDef.toUpperCase().contains("UNIQUE");
        boolean targetUnique = targetDef.toUpperCase().contains("UNIQUE");
        if (sourceUnique != targetUnique) {
            return sourceUnique ? "Uniqueness removed" : "Uniqueness added";
        }
        
        // Check for WHERE clause (partial index)
        boolean sourcePartial = sourceDef.contains(" WHERE ");
        boolean targetPartial = targetDef.contains(" WHERE ");
        if (sourcePartial != targetPartial) {
            return sourcePartial ? "Partial predicate removed" : "Partial predicate added";
        }
        if (sourcePartial && targetPartial) {
            return "Partial predicate differs";
        }
        
        // Generic difference
        return "Definition differs";
    }

    private String extractTableName(String objectName) {
        if (objectName.contains(".")) {
            return objectName.substring(0, objectName.lastIndexOf("."));
        }
        return objectName;
    }

    private String extractColumnName(String objectName) {
        if (objectName.contains(".")) {
            return objectName.substring(objectName.lastIndexOf(".") + 1);
        }
        return objectName;
    }
}

