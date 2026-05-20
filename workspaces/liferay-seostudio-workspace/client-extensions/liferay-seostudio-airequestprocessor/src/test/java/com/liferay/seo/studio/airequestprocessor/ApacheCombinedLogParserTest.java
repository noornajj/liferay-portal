/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.seo.studio.airequestprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UncheckedIOException;

import java.nio.charset.StandardCharsets;

import java.time.Instant;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.json.JSONObject;

import org.junit.jupiter.api.Test;

/**
 * @author Noor Najjar
 */
public class ApacheCombinedLogParserTest {

	@Test
	public void testEmptyInputReturnsEmptyStream() throws Exception {
		try (Stream<JSONObject> stream =
				_parser.parse(_stream(""), Instant.MIN)) {

			assertEquals(0, stream.count());
		}
	}

	@Test
	public void testFormatKeyIsApacheCombined() {
		assertEquals("apache-combined", _parser.formatKey());
	}

	@Test
	public void testMalformedNewestLineThrowsChecked() {
		String input = "not a log line at all\n";

		InvalidLogFormatException invalidLogFormatException = assertThrows(
			InvalidLogFormatException.class,
			() -> _parser.parse(_stream(input), Instant.MIN));

		assertEquals(
			"not a log line at all", invalidLogFormatException.getLine());
	}

	@Test
	public void testMalformedOlderLineThrowsUnchecked() throws Exception {
		String input = "still not a log line\n" + _GOLDEN_LINE + "\n";

		try (Stream<JSONObject> stream =
				_parser.parse(_stream(input), Instant.MIN)) {

			UncheckedIOException uncheckedIOException = assertThrows(
				UncheckedIOException.class,
				() -> stream.collect(Collectors.toList()));

			assertInstanceOf(
				InvalidLogFormatException.class,
				uncheckedIOException.getCause());

			InvalidLogFormatException cause =
				(InvalidLogFormatException)uncheckedIOException.getCause();

			assertEquals("still not a log line", cause.getLine());
		}
	}

	@Test
	public void testParsesAllLinesFromValidFixture() throws Exception {
		try (InputStream inputStream = _fixture("apache-combined-valid.log");
			Stream<JSONObject> stream =
				_parser.parse(inputStream, Instant.MIN)) {

			List<JSONObject> entries = stream.collect(Collectors.toList());

			assertEquals(4, entries.size());
		}
	}

	@Test
	public void testParsesEmptyUserAgent() throws Exception {
		String line =
			"127.0.0.1 - - [10/Oct/2000:13:55:36 +0000] " +
				"\"GET / HTTP/1.0\" 200 100 \"-\" \"\"";

		JSONObject entry = _parseOne(line);

		assertEquals("", entry.getString("userAgent"));
	}

	@Test
	public void testParsesEscapedQuoteInReferer() throws Exception {
		String line =
			"127.0.0.1 - - [10/Oct/2000:13:55:36 +0000] " +
				"\"GET / HTTP/1.0\" 200 100 " +
				"\"http://example.com/?q=\\\"hi\\\"\" \"Bot\"";

		JSONObject entry = _parseOne(line);

		assertEquals("Bot", entry.getString("userAgent"));
	}

	@Test
	public void testParsesEscapedQuoteInUserAgent() throws Exception {
		String line =
			"127.0.0.1 - - [10/Oct/2000:13:55:36 +0000] " +
				"\"GET / HTTP/1.0\" 200 100 \"-\" " +
				"\"Mozilla \\\"test\\\" Bot\"";

		JSONObject entry = _parseOne(line);

		assertEquals(
			"Mozilla \\\"test\\\" Bot", entry.getString("userAgent"));
	}

	@Test
	public void testParsesGoldenLine() throws Exception {
		JSONObject entry = _parseOne(_GOLDEN_LINE);

		assertEquals("/apache_pb.gif", entry.getString("requestPath"));
		assertEquals(
			"Mozilla/5.0 (compatible; GPTBot/1.0)",
			entry.getString("userAgent"));
		assertEquals(
			"2000-10-10T20:55:36Z", entry.getString("requestTime"));
	}

	@Test
	public void testParsesIPv6() throws Exception {
		String line =
			"2001:db8::1 - - [12/Oct/2000:15:30:45 +0000] " +
				"\"GET /foo HTTP/2.0\" 200 100 \"-\" \"ClaudeBot\"";

		JSONObject entry = _parseOne(line);

		assertEquals("/foo", entry.getString("requestPath"));
		assertEquals("ClaudeBot", entry.getString("userAgent"));
	}

	@Test
	public void testParsesNonAsciiPath() throws Exception {
		String line =
			"127.0.0.1 - - [10/Oct/2000:13:55:36 +0000] " +
				"\"GET /café HTTP/1.0\" 200 100 \"-\" \"Bot\"";

		JSONObject entry = _parseOne(line);

		assertEquals("/café", entry.getString("requestPath"));
	}

	@Test
	public void testParsesRequestPathStripsQueryString() throws Exception {
		String line =
			"127.0.0.1 - - [10/Oct/2000:13:55:36 +0000] " +
				"\"GET /foo?bar=baz HTTP/1.1\" 200 100 \"-\" \"Bot\"";

		JSONObject entry = _parseOne(line);

		assertEquals("/foo?bar=baz", entry.getString("requestPath"));
	}

	@Test
	public void testReturnsEntriesNewestFirst() throws Exception {
		String oldLine =
			"127.0.0.1 - - [10/Oct/2000:13:55:36 +0000] " +
				"\"GET /old HTTP/1.0\" 200 100 \"-\" \"OldBot\"";
		String newLine =
			"127.0.0.1 - - [11/Oct/2000:13:55:36 +0000] " +
				"\"GET /new HTTP/1.0\" 200 100 \"-\" \"NewBot\"";

		try (Stream<JSONObject> stream =
				_parser.parse(_stream(oldLine + "\n" + newLine), Instant.MIN)) {

			List<JSONObject> entries = stream.collect(Collectors.toList());

			assertEquals(2, entries.size());
			assertEquals("/new", entries.get(0).getString("requestPath"));
			assertEquals("/old", entries.get(1).getString("requestPath"));
		}
	}

	@Test
	public void testReUploadAllBelowBoundReturnsEmptyStream() throws Exception {
		Instant lowerBound = Instant.parse("2099-01-01T00:00:00Z");

		try (InputStream inputStream = _fixture("apache-combined-valid.log");
			Stream<JSONObject> stream =
				_parser.parse(inputStream, lowerBound)) {

			assertEquals(0, stream.count());
		}
	}

	@Test
	public void testShortCircuitsAtLowerBound() throws Exception {
		String belowBound =
			"127.0.0.1 - - [10/Oct/2000:00:00:00 +0000] " +
				"\"GET /below HTTP/1.0\" 200 100 \"-\" \"Bot\"";
		String aboveBound =
			"127.0.0.1 - - [10/Oct/2000:12:00:00 +0000] " +
				"\"GET /above HTTP/1.0\" 200 100 \"-\" \"Bot\"";

		Instant lowerBound = Instant.parse("2000-10-10T06:00:00Z");

		try (Stream<JSONObject> stream =
				_parser.parse(
					_stream(belowBound + "\n" + aboveBound), lowerBound)) {

			List<JSONObject> entries = stream.collect(Collectors.toList());

			assertEquals(1, entries.size());
			assertEquals("/above", entries.get(0).getString("requestPath"));
		}
	}

	@Test
	public void testSkipsBlankLines() throws Exception {
		String input = _GOLDEN_LINE + "\n\n\n" + _GOLDEN_LINE + "\n";

		try (Stream<JSONObject> stream =
				_parser.parse(_stream(input), Instant.MIN)) {

			List<JSONObject> entries = stream.collect(Collectors.toList());

			assertEquals(2, entries.size());
		}
	}

	@Test
	public void testToleratesTabSeparators() throws Exception {
		String line =
			"127.0.0.1\t-\t-\t[10/Oct/2000:13:55:36 +0000]\t" +
				"\"GET /foo HTTP/1.0\"\t200\t100\t\"-\"\t\"Bot\"";

		JSONObject entry = _parseOne(line);

		assertEquals("/foo", entry.getString("requestPath"));
		assertEquals("Bot", entry.getString("userAgent"));
	}

	@Test
	public void testToleratesTrailingWhitespace() throws Exception {
		String line =
			"127.0.0.1 - - [10/Oct/2000:13:55:36 +0000] " +
				"\"GET / HTTP/1.0\" 200 100 \"-\" \"Bot\"   \r";

		JSONObject entry = _parseOne(line);

		assertEquals("Bot", entry.getString("userAgent"));
	}

	@Test
	public void testTimestampConvertsToUtc() throws Exception {
		String line =
			"127.0.0.1 - - [10/Oct/2000:13:55:36 +0530] " +
				"\"GET / HTTP/1.0\" 200 100 \"-\" \"Bot\"";

		JSONObject entry = _parseOne(line);

		assertEquals("2000-10-10T08:25:36Z", entry.getString("requestTime"));
	}

	private InputStream _fixture(String name) {
		InputStream inputStream =
			ApacheCombinedLogParserTest.class.getResourceAsStream(
				"/logs/" + name);

		if (inputStream == null) {
			throw new IllegalStateException(
				"Missing test fixture: /logs/" + name);
		}

		return inputStream;
	}

	private JSONObject _parseOne(String line) throws Exception {
		try (Stream<JSONObject> stream =
				_parser.parse(_stream(line), Instant.MIN)) {

			return stream.findFirst(
			).orElseThrow(
				() -> new AssertionError("Parser returned no entries")
			);
		}
	}

	private InputStream _stream(String content) {
		return new ByteArrayInputStream(
			content.getBytes(StandardCharsets.UTF_8));
	}

	private static final String _GOLDEN_LINE =
		"203.0.113.10 - - [10/Oct/2000:13:55:36 -0700] " +
			"\"GET /apache_pb.gif HTTP/1.0\" 200 2326 " +
			"\"http://www.example.com/start.html\" " +
			"\"Mozilla/5.0 (compatible; GPTBot/1.0)\"";

	private final ApacheCombinedLogParser _parser =
		new ApacheCombinedLogParser();

}