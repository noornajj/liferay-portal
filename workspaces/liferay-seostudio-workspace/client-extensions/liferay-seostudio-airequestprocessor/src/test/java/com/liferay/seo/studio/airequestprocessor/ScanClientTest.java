/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.seo.studio.airequestprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URLEncoder;

import java.nio.charset.StandardCharsets;

import java.time.Instant;

import java.util.Optional;

import org.json.JSONObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * @author Noor Najjar
 */
public class ScanClientTest {

	@BeforeEach
	public void setUp() {
		_restTemplate = new RestTemplate();
		_mockRestServiceServer = MockRestServiceServer.bindTo(
			_restTemplate
		).build();
		_scanClient = new ScanClient(
			new PortalClient("localhost:8080", "http", _restTemplate));
	}

	@Test
	public void testCreateScanPostsBodyAndReturnsCreatedScan() {
		_mockRestServiceServer.expect(
			requestTo("http://localhost:8080/o/c/seostudioscans")
		).andExpect(
			method(HttpMethod.POST)
		).andExpect(
			content().json(
				"{\"name\":\"aiRequestProcessor\"," +
					"\"r_seoStudioDomainToSEOStudioScans_seoStudioDomainId\":" +
						"42," +
					"\"requestDate\":\"2026-05-20T12:00:00Z\"," +
					"\"scanScope\":\"entireDomain\"," +
					"\"scanType\":\"incremental\"," +
					"\"triggeredBy\":\"manual\"}")
		).andRespond(
			withSuccess(
				"{\"id\":7,\"name\":\"aiRequestProcessor\",\"state\":" +
					"\"queued\"}",
				MediaType.APPLICATION_JSON));

		JSONObject scan = _scanClient.createScan(
			"token", 42, "aiRequestProcessor", "incremental",
			Instant.parse("2026-05-20T12:00:00Z"));

		assertEquals(7L, scan.getLong("id"));
		assertEquals("queued", scan.getString("state"));

		_mockRestServiceServer.verify();
	}

	@Test
	public void testFindInFlightReturnsEmptyWhenNoItems() {
		_mockRestServiceServer.expect(
			requestTo(_expectedFindInFlightUrl(42))
		).andRespond(
			withSuccess(
				"{\"items\":[],\"totalCount\":0}",
				MediaType.APPLICATION_JSON));

		Optional<JSONObject> scan =
			_scanClient.findInFlightAIRequestProcessorScan("token", 42);

		assertTrue(scan.isEmpty());

		_mockRestServiceServer.verify();
	}

	@Test
	public void testFindInFlightReturnsFirstItemWhenPresent() {
		_mockRestServiceServer.expect(
			requestTo(_expectedFindInFlightUrl(42))
		).andRespond(
			withSuccess(
				"{\"items\":[{\"id\":9,\"state\":\"running\"}]," +
					"\"totalCount\":1}",
				MediaType.APPLICATION_JSON));

		Optional<JSONObject> scan =
			_scanClient.findInFlightAIRequestProcessorScan("token", 42);

		assertTrue(scan.isPresent());
		assertEquals(9L, scan.get().getLong("id"));

		_mockRestServiceServer.verify();
	}

	@Test
	public void testFindLastCompletedSortsByRequestDateDesc() {
		String filter =
			"name eq 'aiRequestProcessor' and " +
				"r_seoStudioDomainToSEOStudioScans_seoStudioDomainId eq '42'" +
					" and state eq 'completed'";

		String url =
			"http://localhost:8080/o/c/seostudioscans?filter=" +
				URLEncoder.encode(filter, StandardCharsets.UTF_8) +
					"&pageSize=1&sort=" +
						URLEncoder.encode(
							"requestDate:desc", StandardCharsets.UTF_8);

		_mockRestServiceServer.expect(
			requestTo(url)
		).andRespond(
			withSuccess(
				"{\"items\":[{\"id\":5,\"requestDate\":" +
					"\"2026-05-19T00:00:00Z\"}]}",
				MediaType.APPLICATION_JSON));

		Optional<JSONObject> scan =
			_scanClient.findLastCompletedAIRequestProcessorScan("token", 42);

		assertTrue(scan.isPresent());
		assertEquals(5L, scan.get().getLong("id"));

		_mockRestServiceServer.verify();
	}

	@Test
	public void testUpdateScanStateOmitsErrorMessageWhenNull() {
		_mockRestServiceServer.expect(
			requestTo("http://localhost:8080/o/c/seostudioscans/7")
		).andExpect(
			method(HttpMethod.PATCH)
		).andExpect(
			content().json("{\"state\":\"completed\"}")
		).andRespond(
			withSuccess()
		);

		_scanClient.updateScanState("token", 7, "completed", null);

		_mockRestServiceServer.verify();
	}

	@Test
	public void testUpdateScanStateSendsErrorMessageWhenPresent() {
		_mockRestServiceServer.expect(
			requestTo("http://localhost:8080/o/c/seostudioscans/7")
		).andExpect(
			method(HttpMethod.PATCH)
		).andExpect(
			content().json(
				"{\"state\":\"failed\",\"errorMessage\":\"parse error\"}")
		).andRespond(
			withSuccess()
		);

		_scanClient.updateScanState("token", 7, "failed", "parse error");

		_mockRestServiceServer.verify();
	}

	private String _expectedFindInFlightUrl(long domainId) {
		String filter =
			"name eq 'aiRequestProcessor' and " +
				"r_seoStudioDomainToSEOStudioScans_seoStudioDomainId eq '" +
					domainId + "' and (state eq 'queued' or state eq " +
						"'running')";

		return "http://localhost:8080/o/c/seostudioscans?filter=" +
			URLEncoder.encode(filter, StandardCharsets.UTF_8) + "&pageSize=1";
	}

	private MockRestServiceServer _mockRestServiceServer;
	private RestTemplate _restTemplate;
	private ScanClient _scanClient;

}