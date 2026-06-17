package com.neostride.server.running.api;

import java.math.BigDecimal;

public record RunningAggregate(
		long userId,
		BigDecimal totalDistanceKm,
		long runCount
) {}
