package com.jm.inventory.domain.audit;

import com.jm.inventory.domain.common.NotFoundException;
import java.util.UUID;

public class AuditLogEntryNotFoundException extends NotFoundException {

    public AuditLogEntryNotFoundException(UUID id) {
        super("Audit log entry " + id + " not found");
    }

    @Override
    public String code() {
        return "AUDIT_LOG_ENTRY_NOT_FOUND";
    }
}
