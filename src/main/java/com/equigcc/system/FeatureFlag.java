package com.equigcc.system;

import com.equigcc.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "feature_flag")
public class FeatureFlag extends BaseEntity {

    @Column(name = "flag_key", nullable = false, unique = true, length = 64)
    private String key;

    @Column(nullable = false)
    private boolean enabled;

    protected FeatureFlag() { }

    public FeatureFlag(String key, boolean enabled) {
        this.key = key;
        this.enabled = enabled;
    }

    public String getKey() { return key; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
