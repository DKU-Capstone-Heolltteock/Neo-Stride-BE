package com.neostride.server.platform.event;

import java.time.LocalDateTime;

public record UserSoftDeletedEvent(long userId, LocalDateTime deletedAt) {
}
