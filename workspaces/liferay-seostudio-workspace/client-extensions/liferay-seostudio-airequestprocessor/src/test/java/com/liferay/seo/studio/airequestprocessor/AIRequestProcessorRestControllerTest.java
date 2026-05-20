/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.seo.studio.airequestprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;

import java.nio.charset.StandardCharsets;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author Noor Najjar
 */
public class AIRequestProcessorRestControllerTest {

	@BeforeEach
	public void setUp() throws Exception {
		_aiBotConfigurationClient = mock(AIBotConfigurationClient.class);
		_aiRequestClient = mock(AIRequestClient.class);
		_jwt = mock(Jwt.class);
		_logParser = mock(LogParser.class);
		_multipartFile = mock(MultipartFile.class);
		_scanClient = mock(ScanClient.class);

		when(
			_jwt.getTokenValue()
		).thenReturn(
			"test-token"
		);

		when(
			_logParser.formatKey()
		).thenReturn(
			"apache-combined"
		);

		when(
			_multipartFile.getInputStream()
		).thenReturn(
			new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8))
		);

		_controller = new AIRequestProcessorRestController(
			_aiBotConfigurationClient, _aiRequestClient, List.of(_logParser),
			_scanClient);
	}

	@Test
	public void testFlipsScanToFailedWhenAggregationThrows()
		throws Exception {

		when(
			_scanClient.findInFlightAIRequestProcessorScan(
				anyString(), anyLong())
		).thenReturn(
			java.util.Optional.empty()
		);
		when(
			_scanClient.findLastCompletedAIRequestProcessorScan(
				anyString(), anyLong())
		).thenReturn(
			java.util.Optional.empty()
		);
		when(
			_aiBotConfigurationClient.listEnabledAgentNames(
				anyString(), anyLong())
		).thenReturn(
			List.of("GPTBot")
		);
		when(
			_scanClient.createScan(
				anyString(), anyLong(), anyString(), anyString(), any())
		).thenReturn(
			new JSONObject().put("id", 77L)
		);

		RuntimeException boom = new RuntimeException("boom");

		when(
			_logParser.parse(any(), any())
		).thenThrow(
			boom
		);

		RuntimeException thrown = assertThrows(
			RuntimeException.class,
			() -> _controller.post(_jwt, 42L, "apache-combined", _multipartFile));

		assertEquals("boom", thrown.getMessage());

		verify(
			_scanClient
		).updateScanState(
			"test-token", 77L, "failed", "boom"
		);
	}

	@Test
	public void testHappyPathReturnsScanIdAndCallsClientsInOrder()
		throws Exception {

		when(
			_scanClient.findInFlightAIRequestProcessorScan(
				anyString(), anyLong())
		).thenReturn(
			java.util.Optional.empty()
		);
		when(
			_scanClient.findLastCompletedAIRequestProcessorScan(
				anyString(), anyLong())
		).thenReturn(
			java.util.Optional.empty()
		);
		when(
			_aiBotConfigurationClient.listEnabledAgentNames(
				anyString(), anyLong())
		).thenReturn(
			List.of("GPTBot")
		);
		when(
			_scanClient.createScan(
				anyString(), anyLong(), anyString(), anyString(), any())
		).thenReturn(
			new JSONObject().put("id", 77L)
		);
		when(
			_logParser.parse(any(), any())
		).thenReturn(
			java.util.stream.Stream.of(
				new JSONObject(
				).put(
					"userAgent", "GPTBot"
				).put(
					"requestPath", "/foo"
				).put(
					"requestTime", "2026-05-20T12:00:00Z"
				))
		);
		when(
			_aiRequestClient.findStaleRequestIds(
				anyString(), anyLong(), any())
		).thenReturn(
			Collections.emptyList()
		);

		ResponseEntity<String> response = _controller.post(
			_jwt, 42L, "apache-combined", _multipartFile);

		assertEquals(200, response.getStatusCodeValue());

		JSONObject body = new JSONObject(response.getBody());

		assertEquals(77L, body.getLong("scanId"));

		InOrder inOrder = inOrder(
			_scanClient, _aiBotConfigurationClient, _logParser,
			_aiRequestClient);

		inOrder.verify(_scanClient).findInFlightAIRequestProcessorScan(
			"test-token", 42L);
		inOrder.verify(_scanClient).findLastCompletedAIRequestProcessorScan(
			"test-token", 42L);
		inOrder.verify(_aiBotConfigurationClient).listEnabledAgentNames(
			"test-token", 42L);
		inOrder.verify(_scanClient).createScan(
			eq("test-token"), eq(42L), eq("aiRequestProcessor"),
			eq("incremental"), any(Instant.class));
		inOrder.verify(_logParser).parse(any(), any());
		inOrder.verify(_aiRequestClient).batchCreate(
			eq("test-token"), eq(77L), eq(42L), any());
		inOrder.verify(_scanClient).updateScanState(
			"test-token", 77L, "completed", null);
		inOrder.verify(_aiRequestClient).findStaleRequestIds(
			eq("test-token"), eq(42L), any(LocalDate.class));
	}

	@Test
	public void testLowerBoundFallsBackTo365DaysWhenNoCompletedScan()
		throws Exception {

		when(
			_scanClient.findInFlightAIRequestProcessorScan(
				anyString(), anyLong())
		).thenReturn(
			java.util.Optional.empty()
		);
		when(
			_scanClient.findLastCompletedAIRequestProcessorScan(
				anyString(), anyLong())
		).thenReturn(
			java.util.Optional.empty()
		);
		when(
			_aiBotConfigurationClient.listEnabledAgentNames(
				anyString(), anyLong())
		).thenReturn(
			Collections.emptyList()
		);
		when(
			_scanClient.createScan(
				anyString(), anyLong(), anyString(), anyString(), any())
		).thenReturn(
			new JSONObject().put("id", 7L)
		);
		when(
			_logParser.parse(any(), any())
		).thenReturn(
			java.util.stream.Stream.empty()
		);
		when(
			_aiRequestClient.findStaleRequestIds(
				anyString(), anyLong(), any())
		).thenReturn(
			Collections.emptyList()
		);

		Instant before = Instant.now().minus(365, ChronoUnit.DAYS);

		_controller.post(_jwt, 42L, "apache-combined", _multipartFile);

		Instant after = Instant.now().minus(365, ChronoUnit.DAYS);

		ArgumentCaptor<Instant> lowerBoundCaptor = ArgumentCaptor.forClass(
			Instant.class);

		verify(
			_logParser
		).parse(any(), lowerBoundCaptor.capture());

		Instant captured = lowerBoundCaptor.getValue();

		assertTrue(
			!captured.isBefore(before) && !captured.isAfter(after),
			"lowerBound should be approximately now-365d, got " + captured);
	}

	@Test
	public void testLowerBoundUsesLastCompletedScanRequestDate()
		throws Exception {

		when(
			_scanClient.findInFlightAIRequestProcessorScan(
				anyString(), anyLong())
		).thenReturn(
			java.util.Optional.empty()
		);
		when(
			_scanClient.findLastCompletedAIRequestProcessorScan(
				anyString(), anyLong())
		).thenReturn(
			java.util.Optional.of(
				new JSONObject(
				).put(
					"id", 5L
				).put(
					"requestDate", "2026-05-15T08:30:00Z"
				))
		);
		when(
			_aiBotConfigurationClient.listEnabledAgentNames(
				anyString(), anyLong())
		).thenReturn(
			Collections.emptyList()
		);
		when(
			_scanClient.createScan(
				anyString(), anyLong(), anyString(), anyString(), any())
		).thenReturn(
			new JSONObject().put("id", 7L)
		);
		when(
			_logParser.parse(any(), any())
		).thenReturn(
			java.util.stream.Stream.empty()
		);
		when(
			_aiRequestClient.findStaleRequestIds(
				anyString(), anyLong(), any())
		).thenReturn(
			Collections.emptyList()
		);

		_controller.post(_jwt, 42L, "apache-combined", _multipartFile);

		verify(
			_logParser
		).parse(any(), eq(Instant.parse("2026-05-15T08:30:00Z")));
	}

	@Test
	public void testPostCompletePruneDeletesStaleIds() throws Exception {
		when(
			_scanClient.findInFlightAIRequestProcessorScan(
				anyString(), anyLong())
		).thenReturn(
			java.util.Optional.empty()
		);
		when(
			_scanClient.findLastCompletedAIRequestProcessorScan(
				anyString(), anyLong())
		).thenReturn(
			java.util.Optional.empty()
		);
		when(
			_aiBotConfigurationClient.listEnabledAgentNames(
				anyString(), anyLong())
		).thenReturn(
			Collections.emptyList()
		);
		when(
			_scanClient.createScan(
				anyString(), anyLong(), anyString(), anyString(), any())
		).thenReturn(
			new JSONObject().put("id", 7L)
		);
		when(
			_logParser.parse(any(), any())
		).thenReturn(
			java.util.stream.Stream.empty()
		);

		List<Long> staleIds = new ArrayList<>();

		staleIds.add(101L);
		staleIds.add(102L);
		staleIds.add(103L);

		when(
			_aiRequestClient.findStaleRequestIds(
				anyString(), anyLong(), any())
		).thenReturn(
			staleIds
		);

		_controller.post(_jwt, 42L, "apache-combined", _multipartFile);

		verify(_aiRequestClient).deleteById("test-token", 101L);
		verify(_aiRequestClient).deleteById("test-token", 102L);
		verify(_aiRequestClient).deleteById("test-token", 103L);
	}

	@Test
	public void testPreCheckThrowsConcurrentScanExceptionWhenInFlightExists()
		throws Exception {

		when(
			_scanClient.findInFlightAIRequestProcessorScan(
				anyString(), anyLong())
		).thenReturn(
			java.util.Optional.of(
				new JSONObject().put("id", 99L))
		);

		ConcurrentScanException concurrentScanException = assertThrows(
			ConcurrentScanException.class,
			() -> _controller.post(_jwt, 42L, "apache-combined", _multipartFile));

		assertEquals(99L, concurrentScanException.getConflictingScanId());

		verify(
			_scanClient, never()
		).createScan(anyString(), anyLong(), anyString(), anyString(), any());
	}

	@Test
	public void testUnsupportedFormatThrowsIllegalArgumentException() {
		when(
			_scanClient.findInFlightAIRequestProcessorScan(
				anyString(), anyLong())
		).thenReturn(
			java.util.Optional.empty()
		);

		IllegalArgumentException illegalArgumentException = assertThrows(
			IllegalArgumentException.class,
			() -> _controller.post(_jwt, 42L, "unknown-format", _multipartFile));

		assertTrue(
			illegalArgumentException.getMessage(
			).contains("unknown-format"));

		verify(
			_scanClient, never()
		).createScan(anyString(), anyLong(), anyString(), anyString(), any());
	}

	private AIBotConfigurationClient _aiBotConfigurationClient;
	private AIRequestClient _aiRequestClient;
	private AIRequestProcessorRestController _controller;
	private Jwt _jwt;
	private LogParser _logParser;
	private MultipartFile _multipartFile;
	private ScanClient _scanClient;

}