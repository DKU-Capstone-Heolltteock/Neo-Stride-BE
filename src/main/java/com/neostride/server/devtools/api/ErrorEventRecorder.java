package com.neostride.server.devtools.api;

public interface ErrorEventRecorder {
	void record(String method, String path, int statusCode, String errorType, String messageSummary, String requestId);
}
