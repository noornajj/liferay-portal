/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.seo.studio.airequestprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import org.junit.jupiter.api.Test;

/**
 * @author Noor Najjar
 */
public class AIRequestAggregatorTest {

	@Test
	public void testCaseSensitiveMatchDropsLowercase() {
		AIRequestAggregator aggregator = new AIRequestAggregator(
			List.of("GPTBot"));

		aggregator.accept(
			_entry("Mozilla gptbot/1.0", "/foo", "2026-05-20T12:00:00Z"));

		assertTrue(
			aggregator.build(
			).isEmpty());
	}

	@Test
	public void testDifferentAgentsAreSeparateKeys() {
		AIRequestAggregator aggregator = new AIRequestAggregator(
			List.of("GPTBot", "ClaudeBot"));

		aggregator.accept(
			_entry("GPTBot", "/foo", "2026-05-20T12:00:00Z"));
		aggregator.accept(
			_entry("ClaudeBot", "/foo", "2026-05-20T12:00:00Z"));

		Map<AggregationKey, Integer> counts = aggregator.build();

		assertEquals(2, counts.size());
		assertEquals(
			1,
			counts.get(
				new AggregationKey(
					"GPTBot", "/foo", LocalDate.of(2026, 5, 20))));
		assertEquals(
			1,
			counts.get(
				new AggregationKey(
					"ClaudeBot", "/foo", LocalDate.of(2026, 5, 20))));
	}

	@Test
	public void testDifferentDatesAreSeparateKeys() {
		AIRequestAggregator aggregator = new AIRequestAggregator(
			List.of("GPTBot"));

		aggregator.accept(
			_entry("GPTBot", "/foo", "2026-05-20T12:00:00Z"));
		aggregator.accept(
			_entry("GPTBot", "/foo", "2026-05-21T12:00:00Z"));

		assertEquals(2, aggregator.build().size());
	}

	@Test
	public void testDifferentPathsAreSeparateKeys() {
		AIRequestAggregator aggregator = new AIRequestAggregator(
			List.of("GPTBot"));

		aggregator.accept(
			_entry("GPTBot", "/foo", "2026-05-20T12:00:00Z"));
		aggregator.accept(
			_entry("GPTBot", "/bar", "2026-05-20T12:00:00Z"));

		assertEquals(2, aggregator.build().size());
	}

	@Test
	public void testEmptyInputProducesEmptyMap() {
		AIRequestAggregator aggregator = new AIRequestAggregator(
			List.of("GPTBot"));

		assertTrue(
			aggregator.build(
			).isEmpty());
	}

	@Test
	public void testEmptyUserAgentDropsEntry() {
		AIRequestAggregator aggregator = new AIRequestAggregator(
			List.of("GPTBot"));

		aggregator.accept(_entry("", "/foo", "2026-05-20T12:00:00Z"));

		assertTrue(
			aggregator.build(
			).isEmpty());
	}

	@Test
	public void testFirstBotMatchWinsWhenUserAgentContainsTwo() {
		AIRequestAggregator aggregator = new AIRequestAggregator(
			Arrays.asList("ClaudeBot", "GPTBot"));

		aggregator.accept(
			_entry(
				"Mozilla (compatible; ClaudeBot/1.0; GPTBot/1.0)",
				"/foo", "2026-05-20T12:00:00Z"));

		Map<AggregationKey, Integer> counts = aggregator.build();

		assertEquals(1, counts.size());
		assertEquals(
			1,
			counts.get(
				new AggregationKey(
					"ClaudeBot", "/foo", LocalDate.of(2026, 5, 20))));
	}

	@Test
	public void testMissingUserAgentDropsEntry() {
		AIRequestAggregator aggregator = new AIRequestAggregator(
			List.of("GPTBot"));

		JSONObject entry = new JSONObject(
		).put(
			"requestPath", "/foo"
		).put(
			"requestTime", "2026-05-20T12:00:00Z"
		);

		aggregator.accept(entry);

		assertTrue(
			aggregator.build(
			).isEmpty());
	}

	@Test
	public void testNoBotMatchDropsEntry() {
		AIRequestAggregator aggregator = new AIRequestAggregator(
			List.of("GPTBot"));

		aggregator.accept(
			_entry(
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64)", "/foo",
				"2026-05-20T12:00:00Z"));

		assertTrue(
			aggregator.build(
			).isEmpty());
	}

	@Test
	public void testNoEnabledAgentsDropsEntry() {
		AIRequestAggregator aggregator = new AIRequestAggregator(
			Collections.emptyList());

		aggregator.accept(
			_entry("GPTBot", "/foo", "2026-05-20T12:00:00Z"));

		assertTrue(
			aggregator.build(
			).isEmpty());
	}

	@Test
	public void testStripsAbsoluteUrlHost() {
		AIRequestAggregator aggregator = new AIRequestAggregator(
			List.of("GPTBot"));

		aggregator.accept(
			_entry(
				"GPTBot", "http://example.com/foo/bar",
				"2026-05-20T12:00:00Z"));

		Map<AggregationKey, Integer> counts = aggregator.build();

		AggregationKey key = new AggregationKey(
			"GPTBot", "/foo/bar", LocalDate.of(2026, 5, 20));

		assertEquals(1, counts.get(key));
	}

	@Test
	public void testStripsQueryString() {
		AIRequestAggregator aggregator = new AIRequestAggregator(
			List.of("GPTBot"));

		aggregator.accept(
			_entry(
				"GPTBot", "/foo?bar=baz&qux=quux",
				"2026-05-20T12:00:00Z"));

		Map<AggregationKey, Integer> counts = aggregator.build();

		AggregationKey key = new AggregationKey(
			"GPTBot", "/foo", LocalDate.of(2026, 5, 20));

		assertEquals(1, counts.get(key));
	}

	@Test
	public void testSumsCollisions() {
		AIRequestAggregator aggregator = new AIRequestAggregator(
			List.of("GPTBot"));

		aggregator.accept(
			_entry("GPTBot", "/foo", "2026-05-20T12:00:00Z"));
		aggregator.accept(
			_entry("GPTBot", "/foo", "2026-05-20T15:30:45Z"));
		aggregator.accept(
			_entry("GPTBot", "/foo", "2026-05-20T23:59:59Z"));

		Map<AggregationKey, Integer> counts = aggregator.build();

		assertEquals(1, counts.size());
		assertEquals(
			3,
			counts.get(
				new AggregationKey(
					"GPTBot", "/foo", LocalDate.of(2026, 5, 20))));
	}

	@Test
	public void testUtcDayBoundary() {

		// 2026-05-20T23:59:59Z and 2026-05-21T00:00:00Z are different
		// UTC days even though they are one second apart.

		AIRequestAggregator aggregator = new AIRequestAggregator(
			List.of("GPTBot"));

		aggregator.accept(
			_entry("GPTBot", "/foo", "2026-05-20T23:59:59Z"));
		aggregator.accept(
			_entry("GPTBot", "/foo", "2026-05-21T00:00:00Z"));

		Map<AggregationKey, Integer> counts = aggregator.build();

		assertEquals(2, counts.size());
		assertEquals(
			1,
			counts.get(
				new AggregationKey(
					"GPTBot", "/foo", LocalDate.of(2026, 5, 20))));
		assertEquals(
			1,
			counts.get(
				new AggregationKey(
					"GPTBot", "/foo", LocalDate.of(2026, 5, 21))));
	}

	private JSONObject _entry(
		String userAgent, String requestPath, String requestTime) {

		return new JSONObject(
		).put(
			"requestPath", requestPath
		).put(
			"requestTime", requestTime
		).put(
			"userAgent", userAgent
		);
	}

}