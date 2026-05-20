/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.seo.studio.airequestprocessor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

/**
 * @author Noor Najjar
 */
public class AIRequestAggregator {

	public AIRequestAggregator(Collection<String> enabledAgentNames) {
		_enabledAgentNames = enabledAgentNames;
	}

	public void accept(JSONObject parsedLog) {
		String userAgent = parsedLog.optString("userAgent", "");

		if (userAgent.isEmpty()) {
			return;
		}

		String agentName = null;

		for (String name : _enabledAgentNames) {
			if (userAgent.contains(name)) {
				agentName = name;

				break;
			}
		}

		if (agentName == null) {
			return;
		}

		String requestPath = parsedLog.optString("requestPath", "");

		if (requestPath.isEmpty()) {
			return;
		}

		String pageURL = _stripHostAndQuery(requestPath);

		String requestTime = parsedLog.optString("requestTime", "");

		if (requestTime.isEmpty()) {
			return;
		}

		LocalDate requestDate = Instant.parse(
			requestTime
		).atZone(
			ZoneOffset.UTC
		).toLocalDate();

		AggregationKey aggregationKey = new AggregationKey(
			agentName, pageURL, requestDate);

		_counts.merge(aggregationKey, 1, Integer::sum);
	}

	public Map<AggregationKey, Integer> build() {
		return Map.copyOf(_counts);
	}

	private String _stripHostAndQuery(String requestPath) {
		int schemeIndex = requestPath.indexOf("://");

		String path = requestPath;

		if (schemeIndex != -1) {
			int pathStartIndex = requestPath.indexOf('/', schemeIndex + 3);

			if (pathStartIndex == -1) {
				path = "/";
			}
			else {
				path = requestPath.substring(pathStartIndex);
			}
		}

		int queryIndex = path.indexOf('?');

		if (queryIndex == -1) {
			return path;
		}

		return path.substring(0, queryIndex);
	}

	private final Map<AggregationKey, Integer> _counts = new HashMap<>();
	private final Collection<String> _enabledAgentNames;

}