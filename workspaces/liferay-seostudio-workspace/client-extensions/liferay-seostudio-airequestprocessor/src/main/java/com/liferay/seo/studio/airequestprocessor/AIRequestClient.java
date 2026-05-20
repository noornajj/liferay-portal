/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.seo.studio.airequestprocessor;

import java.net.URLEncoder;

import java.nio.charset.StandardCharsets;

import java.time.LocalDate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import org.springframework.stereotype.Component;

/**
 * @author Noor Najjar
 */
@Component
public class AIRequestClient {

	public AIRequestClient(PortalClient portalClient) {
		_portalClient = portalClient;
	}

	public void batchCreate(
		String token, long scanId, long domainId,
		Map<AggregationKey, Integer> counts) {

		for (Map.Entry<AggregationKey, Integer> entry : counts.entrySet()) {
			AggregationKey key = entry.getKey();

			JSONObject body = new JSONObject(
			).put(
				"agentName", key.agentName()
			).put(
				"count", entry.getValue()
			).put(
				"pageURL", key.pageURL()
			).put(
				"r_seoStudioDomainToSEOStudioAIRequests_seoStudioDomainId",
				domainId
			).put(
				"r_seoStudioScanToSEOStudioAIRequests_seoStudioScanId", scanId
			).put(
				"requestDate", key.requestDate().toString()
			);

			_portalClient.post(token, _BASE_PATH, body.toString());
		}
	}

	public void deleteById(String token, long id) {
		_portalClient.delete(token, _BASE_PATH + "/" + id);
	}

	public List<Long> findStaleRequestIds(
		String token, long domainId, LocalDate cutoffDate) {

		List<Long> ids = new ArrayList<>();

		String filter =
			"r_seoStudioDomainToSEOStudioAIRequests_seoStudioDomainId eq '" +
				domainId + "' and requestDate lt '" + cutoffDate + "'";

		int page = 1;

		while (true) {
			String path =
				_BASE_PATH + "?filter=" +
					URLEncoder.encode(filter, StandardCharsets.UTF_8) +
						"&pageSize=200&page=" + page;

			JSONObject response = new JSONObject(
				_portalClient.get(token, path));

			JSONArray items = response.optJSONArray("items");

			if ((items == null) || items.isEmpty()) {
				break;
			}

			for (int i = 0; i < items.length(); i++) {
				ids.add(items.getJSONObject(i).getLong("id"));
			}

			if (items.length() < 200) {
				break;
			}

			page++;
		}

		return ids;
	}

	private static final String _BASE_PATH = "/o/c/seostudioairequests";

	private final PortalClient _portalClient;

}