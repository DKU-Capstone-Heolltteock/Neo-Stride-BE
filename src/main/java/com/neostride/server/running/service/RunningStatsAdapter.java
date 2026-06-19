package com.neostride.server.running.service;

import com.neostride.server.running.api.RunningAggregate;
import com.neostride.server.running.api.RunningStatsReader;
import com.neostride.server.running.repository.RunningRecordRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RunningStatsAdapter implements RunningStatsReader {
	private final RunningRecordRepository runningRecordRepository;

	public RunningStatsAdapter(RunningRecordRepository runningRecordRepository) {
		this.runningRecordRepository = runningRecordRepository;
	}

	@Override
	public Map<Long, RunningAggregate> summarizeByUsers(Collection<Long> userIds, LocalDate from, LocalDate to) {
		return runningRecordRepository.summarizeByUsers(userIds, from, to);
	}

	@Override
	public boolean isRecordOwnedByUser(long runningRecordId, long userId) {
		Long ownerUserId = runningRecordRepository.findOwnerUserId(runningRecordId);
		return ownerUserId != null && ownerUserId == userId;
	}
}
