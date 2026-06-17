package com.neostride.server.running.api;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

public interface RunningStatsReader {
	Map<Long, RunningAggregate> summarizeByUsers(Collection<Long> userIds, LocalDate from, LocalDate to);
}
