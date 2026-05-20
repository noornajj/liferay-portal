/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.seo.studio.airequestprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URLEncoder;

import java.nio.charset.StandardCharsets;

import java.time.LocalDate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * @author Noor Najjar
 */
public class AIRequestClientTest {

	@BeforeEach
	public void setUp() {
		_restTemplate = new RestTemplate();
		_mockRestServiceServer = MockRestServiceServer.bindTo(
			_restTemplate
		).build();
		_aiRequestClient = new AIRequestClient(
			new PortalClient("localhost:8080", "http", _restTemplate));
	}

	@Test
	public void testBatchCreatePostsOneRequestPerEntry() {
		Map<AggregationKey, Integer> counts = new LinkedHashMap<>();

		counts.put(
			new AggregationKey(
				"GPTBot", "/foo", LocalDate.of(2026, 5, 20)),
			5);
		counts.put(
			new AggregationKey(
				"ClaudeBot", "/bar", LocalDate.of(2026, 5, 20)),
			3);

		_mockRestServiceServer.expect(
			requestTo("http://localhost:8080/o/c/seostudioairequests")
		).andExpect(
			method(HttpMethod.POST)
		).andExpect(
			content().json(
				"{\"agentName\":\"GPTBot\",\"count\":5," +
					"\"pageURL\":\"/foo\"," +
					"\"r_seoStudioDomainToSEOStudioAIRequests_" +
						"seoStudioDomainId\":42," +
					"\"r_seoStudioScanToSEOStudioAIRequests_" +
						"seoStudioScanId\":7," +
					"\"requestDate\":\"2026-05-20\"}")
		).andRespond(
			withSuccess()
		);
		_mockRestServiceServer.expect(
			requestTo("http://localhost:8080/o/c/seostudioairequests")
		).andExpect(
			method(HttpMethod.POST)
		).andExpect(
			content().json(
				"{\"agentName\":\"ClaudeBot\",\"count\":3," +
					"\"pageURL\":\"/bar\"," +
					"\"r_seoStudioDomainToSEOStudioAIRequests_" +
						"seoStudioDomainId\":42," +
					"\"r_seoStudioScanToSEOStudioAIRequests_" +
						"seoStudioScanId\":7," +
					"\"requestDate\":\"2026-05-20\"}")
		).andRespond(
			withSuccess()
		);

		_aiRequestClient.batchCreate("token", 7, 42, counts);

		_mockRestServiceServer.verify();
	}

	@Test
	public void testDeleteByIdSendsDelete() {
		_mockRestServiceServer.expect(
			requestTo("http://localhost:8080/o/c/seostudioairequests/99")
		).andExpect(
			method(HttpMethod.DELETE)
		).andRespond(
			withSuccess()
		);

		_aiRequestClient.deleteById("token", 99);

		_mockRestServiceServer.verify();
	}

	@Test
	public void testFindStaleRequestIdsCollectsAcrossPages() {
		String filter =
			"r_seoStudioDomainToSEOStudioAIRequests_seoStudioDomainId eq " +
				"'42' and requestDate lt '2025-05-20'";

		_mockRestServiceServer.expect(
			requestTo(_pageUrl(filter, 1))
		).andRespond(
			withSuccess(
				"{\"items\":[" + _idItems(1, 200) + "],\"totalCount\":350}",
				MediaType.APPLICATION_JSON));
		_mockRestServiceServer.expect(
			requestTo(_pageUrl(filter, 2))
		).andRespond(
			withSuccess(
				"{\"items\":[" + _idItems(201, 350) + "],\"totalCount\":350}",
				MediaType.APPLICATION_JSON));

		List<Long> ids = _aiRequestClient.findStaleRequestIds(
			"token", 42, LocalDate.of(2025, 5, 20));

		assertEquals(350, ids.size());
		assertEquals(1L, ids.get(0));
		assertEquals(350L, ids.get(349));

		_mockRestServiceServer.verify();
	}

	private String _idItems(long startInclusive, long endInclusive) {
		StringBuilder sb = new StringBuilder();

		for (long id = startInclusive; id <= endInclusive; id++) {
			if (id > startInclusive) {
				sb.append(',');
			}

			sb.append("{\"id\":");
			sb.append(id);
			sb.append('}');
		}

		return sb.toString();
	}

	private String _pageUrl(String filter, int page) {
		return "http://localhost:8080/o/c/seostudioairequests?filter=" +
			URLEncoder.encode(filter, StandardCharsets.UTF_8) +
				"&pageSize=200&page=" + page;
	}

	private AIRequestClient _aiRequestClient;
	private MockRestServiceServer _mockRestServiceServer;
	private RestTemplate _restTemplate;

}