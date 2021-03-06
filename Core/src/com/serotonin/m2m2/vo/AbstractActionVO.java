/*
    Copyright (C) 2013 Deltamation Software All rights reserved.
    @author Terry Packer
 */
package com.serotonin.m2m2.vo;

import java.util.List;

import com.serotonin.ShouldNeverHappenException;
import com.serotonin.json.spi.JsonProperty;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.rt.event.type.AuditEventType;

/**
 * 
 * Class that needs an Enable/Disable Member
 * 
 * Copyright (C) 2013 Deltamation Software. All Rights Reserved.
 * @author Terry Packer
 *
 */
public abstract class AbstractActionVO<VO extends AbstractActionVO<VO>> extends AbstractVO<VO> {
    private static final long serialVersionUID = -1;
    
    @JsonProperty
    protected boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /* (non-Javadoc)
     * @see com.serotonin.m2m2.util.ChangeComparable#addProperties(java.util.List)
     */
    @Override
    public void addProperties(List<TranslatableMessage> list) {
        super.addProperties(list);
        AuditEventType.addPropertyMessage(list, "common.enabled", enabled);
       
    }
    @Override
    public void addPropertyChanges(List<TranslatableMessage> list, VO from) {
        super.addPropertyChanges(list, from);
        AuditEventType.maybeAddPropertyChangeMessage(list, "common.enabled", from.enabled, enabled);
    }
    
    /**
     * Copies a vo
     * @return Copy of this vo
     */
    @SuppressWarnings("unchecked")
    public VO copy() {
        // TODO make sure this works
        try {
            return (VO) super.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new ShouldNeverHappenException(e);
        }
    }

}
