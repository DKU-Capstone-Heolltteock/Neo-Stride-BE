package com.neostride.server.auth.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserAdministrationPort {
	List<AdminUserAccount> searchAccounts(String query, String status, int limit);

	Optional<AdminUserAccount> findAccount(long userId);

	AdminUserAccount suspendAccount(long userId, long operatorAccountId, String reason, LocalDateTime suspendedUntil);

	AdminUserAccount restoreAccount(long userId);

	List<Long> activeUserIds(int limit);

	long countAccounts(String status);
}
