package dev.monelytics;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuditService {
  private final AuditRepository events;

  AuditService(AuditRepository events) {
    this.events = events;
  }

  @Transactional
  void record(UUID actor, String action, UUID resource, String detail) {
    AuditEvent event = new AuditEvent();
    event.actorId = actor;
    event.action = action;
    event.resourceId = resource;
    event.detail = detail;
    events.save(event);
  }
}
