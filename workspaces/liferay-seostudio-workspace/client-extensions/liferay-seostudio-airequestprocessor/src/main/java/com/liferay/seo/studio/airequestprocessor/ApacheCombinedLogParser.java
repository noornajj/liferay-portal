/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.seo.studio.airequestprocessor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.io.input.ReversedLinesFileReader;

import org.json.JSONObject;

import org.springframework.stereotype.Component;

/**
 * @author Noor Najjar
 */
@Component
public class ApacheCombinedLogParser implements LogParser {

	@Override
	public String formatKey() {
		return "apache-combined";
	}

	@Override
	public Stream<JSONObject> parse(
			InputStream inputStream, Instant lowerBound)
		throws InvalidLogFormatException {

		Path tempFile = null;
		ReversedLinesFileReader reversedLinesFileReader;

		try {
			tempFile = Files.createTempFile(
				"airequestprocessor-", ".log");

			tempFile.toFile(
			).deleteOnExit();

			Files.copy(
				inputStream, tempFile,
				StandardCopyOption.REPLACE_EXISTING);

			reversedLinesFileReader = ReversedLinesFileReader.builder(
			).setPath(
				tempFile
			).setCharset(
				StandardCharsets.UTF_8
			).get();
		}
		catch (IOException ioException) {
			if (tempFile != null) {
				_deleteQuietly(tempFile);
			}

			throw new UncheckedIOException(ioException);
		}

		JSONObject firstEntry;

		try {
			firstEntry = _parseLine(reversedLinesFileReader, lowerBound);

			if (firstEntry == null) {
				_closeAndDelete(reversedLinesFileReader, tempFile);

				return Stream.empty();
			}
		}
		catch (InvalidLogFormatException invalidLogFormatException) {
			_closeAndDelete(reversedLinesFileReader, tempFile);

			throw invalidLogFormatException;
		}

		Path closeTempFile = tempFile;

		Stream<JSONObject> remainder = Stream.generate(
			() -> {
				try {
					return _parseLine(
						reversedLinesFileReader, lowerBound);
				}
				catch (InvalidLogFormatException
						invalidLogFormatException) {

					throw new UncheckedIOException(
						invalidLogFormatException);
				}
			}
		).takeWhile(
			Objects::nonNull
		);

		return Stream.concat(
			Stream.of(firstEntry), remainder
		).onClose(
			() -> _closeAndDelete(reversedLinesFileReader, closeTempFile)
		);
	}

	private void _closeAndDelete(
		ReversedLinesFileReader reversedLinesFileReader, Path tempFile) {

		try {
			reversedLinesFileReader.close();
		}
		catch (IOException ioException) {
		}

		_deleteQuietly(tempFile);
	}

	private void _deleteQuietly(Path tempFile) {
		try {
			Files.deleteIfExists(tempFile);
		}
		catch (IOException ioException) {
		}
	}

	private boolean _isBelowLowerBound(String line, Instant lowerBound) {
		Matcher matcher = _TIMESTAMP_PATTERN.matcher(line);

		if (!matcher.lookingAt()) {
			return false;
		}

		try {
			Instant instant = ZonedDateTime.parse(
				matcher.group(1), _DATE_FORMATTER
			).toInstant();

			return instant.isBefore(lowerBound);
		}
		catch (DateTimeParseException dateTimeParseException) {
			return false;
		}
	}

	private JSONObject _parseLine(
			ReversedLinesFileReader reversedLinesFileReader,
			Instant lowerBound)
		throws InvalidLogFormatException {

		while (true) {
			String line;

			try {
				line = reversedLinesFileReader.readLine();
			}
			catch (IOException ioException) {
				throw new UncheckedIOException(ioException);
			}

			if (line == null) {
				return null;
			}

			if (line.isBlank()) {
				continue;
			}

			if (_isBelowLowerBound(line, lowerBound)) {
				return null;
			}

			Matcher matcher = _PATTERN.matcher(line);

			if (!matcher.matches()) {
				throw new InvalidLogFormatException(
					line, "does not match Apache Combined regex");
			}

			String request = matcher.group(5);

			String[] requestParts = request.split(" ");

			if (requestParts.length != 3) {
				throw new InvalidLogFormatException(
					line,
					"request must be \"<method> <path> <protocol>\"");
			}

			String requestTime;

			try {
				ZonedDateTime zonedDateTime = ZonedDateTime.parse(
					matcher.group(4), _DATE_FORMATTER);

				requestTime = zonedDateTime.toInstant(
				).toString();
			}
			catch (DateTimeParseException dateTimeParseException) {
				throw new InvalidLogFormatException(
					line,
					"unparseable timestamp: " +
						dateTimeParseException.getMessage());
			}

			return new JSONObject(
			).put(
				"requestPath", requestParts[1]
			).put(
				"requestTime", requestTime
			).put(
				"userAgent", matcher.group(9)
			);
		}
	}

	private static final DateTimeFormatter _DATE_FORMATTER =
		DateTimeFormatter.ofPattern(
			"dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH);

	private static final Pattern _PATTERN = Pattern.compile(
		"^(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+\\[([^\\]]+)\\]\\s+" +
			"\"((?:[^\"\\\\]|\\\\.)*)\"\\s+(\\d{3})\\s+(\\S+)\\s+" +
				"\"((?:[^\"\\\\]|\\\\.)*)\"\\s+" +
					"\"((?:[^\"\\\\]|\\\\.)*)\"\\s*$");

	private static final Pattern _TIMESTAMP_PATTERN = Pattern.compile(
		"^\\S+\\s+\\S+\\s+\\S+\\s+\\[([^\\]]+)\\]");

}