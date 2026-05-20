/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.seo.studio.airequestprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URLEncoder;

import java.nio.charset.StandardCharsets;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * @author Noor Najjar
 */
public class AIBotConfigurationClientTest {

	@BeforeEach
	public void setUp() {
		_restTemplate = new RestTemplate();
		_mockRestServiceServer = MockRestServiceServer.bindTo(
			_restTemplate
		).build();
		_aiBotConfigurationClient = new AIBotConfigurationClient(
			new PortalClient("localhost:8080", "http", _restTemplate));
	}

	@Test
	public void testReturnsAgentNamesFromItems() {
		String filter =
			"r_seoStudioDomainToSEOStudioAIBotConfigurations_" +
				"seoStudioDomainId eq '42' and enabled eq true";

		String url =
			"http://localhost:8080/o/c/seostudioaibotconfigurations?filter=" +
				URLEncoder.encode(filter, StandardCharsets.UTF_8) +
					"&pageSize=200";

		_mockRestServiceServer.expect(
			requestTo(url)
		).andRespond(
			withSuccess(
				"{\"items\":[" +
					"{\"agentName\":\"GPTBot\",\"enabled\":true}," +
					"{\"agentName\":\"ClaudeBot\",\"enabled\":true}" +
					"],\"totalCount\":2}",
				MediaType.APPLICATION_JSON));

		List<String> agentNames =
			_aiBotConfigurationClient.listEnabledAgentNames("token", 42);

		assertEquals(List.of("GPTBot", "ClaudeBot"), agentNames);

		_mockRestServiceServer.verify();
	}

	@Test
	public void testReturnsEmptyListWhenNoItems() {
		_mockRestServiceServer.expect(
			requestTo(_anyMatchingUrl())
		).andRespond(
			withSuccess(
				"{\"items\":[],\"totalCount\":0}",
				MediaType.APPLICATION_JSON));

		List<String> agentNames =
			_aiBotConfigurationClient.listEnabledAgentNames("token", 42);

		assertTrue(agentNames.isEmpty());

		_mockRestServiceServer.verify();
	}

	private String _anyMatchingUrl() {
		String filter =
			"r_seoStudioDomainToSEOStudioAIBotConfigurations_" +
				"seoStudioDomainId eq '42' and enabled eq true";

		return "http://localhost:8080/o/c/seostudioaibotconfigurations" +
			"?filter=" +
				URLEncoder.encode(filter, StandardCharsets.UTF_8) +
					"&pageSize=200";
	}

	private AIBotConfigurationClient _aiBotConfigurationClient;
	private MockRestServiceServer _mockRestServiceServer;
	private RestTemplate _restTemplate;

}