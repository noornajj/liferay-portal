/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.seo.studio.airequestprocessor;

import java.net.URLEncoder;

import java.nio.charset.StandardCharsets;

import java.time.Instant;

import java.util.Optional;

import org.json.JSONArray;
import org.json.JSONObject;

import org.springframework.stereotype.Component;

/**
 * @author Noor Najjar
 */
@Component
public class ScanClient {

	public ScanClient(PortalClient portalClient) {
		_portalClient = portalClient;
	}

	public JSONObject createScan(
		String token, long domainId, String name, String scanType,
		Instant requestDate) {

		JSONObject body = new JSONObject(
		).put(
			"name", name
		).put(
			"r_seoStudioDomainToSEOStudioScans_seoStudioDomainId", domainId
		).put(
			"requestDate", requestDate.toString()
		).put(
			"scanScope", "entireDomain"
		).put(
			"scanType", scanType
		).put(
			"triggeredBy", "manual"
		);

		String responseBody = _portalClient.post(
			token, _BASE_PATH, body.toString());

		return new JSONObject(responseBody);
	}

	public Optional<JSONObject> findInFlightAIRequestProcessorScan(
		String token, long domainId) {

		String filter =
			"name eq 'aiRequestProcessor' and " +
				"r_seoStudioDomainToSEOStudioScans_seoStudioDomainId eq '" +
					domainId + "' and (state eq 'queued' or state eq " +
						"'running')";

		return _firstItem(_query(token, filter, null));
	}

	public Optional<JSONObject> findLastCompletedAIRequestProcessorScan(
		String token, long domainId) {

		String filter =
			"name eq 'aiRequestProcessor' and " +
				"r_seoStudioDomainToSEOStudioScans_seoStudioDomainId eq '" +
					domainId + "' and state eq 'completed'";

		return _firstItem(_query(token, filter, "requestDate:desc"));
	}

	public void updateScanState(
		String token, long scanId, String state, String errorMessage) {

		JSONObject body = new JSONObject(
		).put(
			"state", state
		);

		if (errorMessage != null) {
			body.put("errorMessage", errorMessage);
		}

		_portalClient.patch(
			token, _BASE_PATH + "/" + scanId, body.toString());
	}

	private Optional<JSONObject> _firstItem(JSONObject response) {
		JSONArray items = response.optJSONArray("items");

		if ((items == null) || items.isEmpty()) {
			return Optional.empty();
		}

		return Optional.of(items.getJSONObject(0));
	}

	private JSONObject _query(String token, String filter, String sort) {
		StringBuilder path = new StringBuilder(_BASE_PATH);

		path.append("?filter=");
		path.append(URLEncoder.encode(filter, StandardCharsets.UTF_8));
		path.append("&pageSize=1");

		if (sort != null) {
			path.append("&sort=");
			path.append(URLEncoder.encode(sort, StandardCharsets.UTF_8));
		}

		return new JSONObject(
			_portalClient.get(token, path.toString()));
	}

	private static final String _BASE_PATH = "/o/c/seostudioscans";

	private final PortalClient _portalClient;

}