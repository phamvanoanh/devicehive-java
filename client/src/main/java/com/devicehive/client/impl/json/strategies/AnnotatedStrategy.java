package com.devicehive.client.impl.json.strategies;


import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;


public class AnnotatedStrategy implements ExclusionStrategy {

    private final JsonPolicyDef.Policy policy;

    public AnnotatedStrategy(JsonPolicyDef.Policy policy) {
        this.policy = policy;
    }

    @Override
    public boolean shouldSkipField(FieldAttributes f) {
        JsonPolicyDef policyAnnotation = f.getAnnotation(JsonPolicyDef.class);
        if (policyAnnotation == null) {
            // no policy annotation - filed should be skipped
            return true;
        }
        for (JsonPolicyDef.Policy definedPolicy : policyAnnotation.value()) {
            if (definedPolicy == policy) {
                // policy is found - field is to be included
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean shouldSkipClass(Class<?> clazz) {
        return false;
    }
}
