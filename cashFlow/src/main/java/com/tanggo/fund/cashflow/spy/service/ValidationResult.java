package com.tanggo.fund.cashflow.spy.service;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据验证结果
 */
@Data
public class ValidationResult {
    
    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    
    public void addError(String error) {
        errors.add(error);
    }
    
    public void addWarning(String warning) {
        warnings.add(warning);
    }
    
    public boolean isValid() {
        return errors.isEmpty();
    }
    
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}