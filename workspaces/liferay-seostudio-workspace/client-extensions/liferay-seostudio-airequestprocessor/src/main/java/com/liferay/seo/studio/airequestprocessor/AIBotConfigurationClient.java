/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.seo.studio.airequestprocessor;

import java.net.URLEncoder;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import org.springframework.stereotype.Component;

/**
 * @author Noor Najjar
 */
@Component
public class AIBotConfigurationClient {

	public AIBotConfigurationClient(PortalClient portalClient) {
		_portalClient = portalClient;
	}

	public List<String> listEnabledAgentNames(String token, long domainId) {
		String filter =
			"r_seoStudioDomainToSEOStudioAIBotConfigurations_" +
				"seoStudioDomainId eq '" + domainId + "' and enabled eq true";

		String path =
			"/o/c/seostudioaibotconfigurations?filter=" +
				URLEncoder.encode(filter, StandardCharsets.UTF_8) +
					"&pageSize=200";

		JSONObject response = new JSONObject(
			_portalClient.get(token, path));

		JSONArray items = response.optJSONArray("items");

		List<String> agentNames = new ArrayList<>();

		if (items == null) {
			return agentNames;
		}

		for (int i = 0; i < items.length(); i++) {
			JSONObject item = items.getJSONObject(i);

			agentNames.add(item.getString("agentName"));
		}

		return agentNames;
	}

	private final PortalClient _portalClient;

}