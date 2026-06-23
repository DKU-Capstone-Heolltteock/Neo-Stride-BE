package com.neostride.server.auth.api;

import com.neostride.server.platform.web.CursorSupport.CursorPosition;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserAdministrationPort {
	List<AdminUserAccount> searchAccounts(String query, String status, int limit);

	List<AdminUserAccount> searchAccounts(String query, String status, CursorPosition cursor, LocalDateTime from, LocalDateTime to, int limit);

	Optional<AdminUserAccount> findAccount(long userId);

	AdminUserAccount suspendAccount(long userId, long operatorAccountId, String reason, LocalDateTime suspendedUntil);

	AdminUserAccount restoreAccount(long userId);

	List<Long> activeUserIds(int limit);

	long countAccounts(String status);
}
