/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.seo.studio.model;

import com.liferay.portal.kernel.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * @author Brooke Dalton
 */
public class CrawlHit {

	public CrawlHit(JSONObject jsonObject) {
		_canonicalURL = jsonObject.optString("canonicalURL", null);
		_url = jsonObject.optString("url", null);

		JSONArray linksJSONArray = jsonObject.optJSONArray("links");

		if (linksJSONArray != null) {
			for (Object linkObject : linksJSONArray) {
				if (linkObject instanceof String) {
					_links.add((String)linkObject);
				}
			}
		}

		_imagesJSONArray = _parseImages(jsonObject.optString("fullHtml", null));
	}

	public String getCanonicalURL() {
		return _canonicalURL;
	}

	public JSONArray getImages() {
		return _imagesJSONArray;
	}

	public List<String> getLinks() {
		return _links;
	}

	public String getURL() {
		return _url;
	}

	private String _attributeValue(Matcher attributeMatcher) {
		String doubleQuoted = attributeMatcher.group(3);

		if (doubleQuoted != null) {
			return doubleQuoted;
		}

		String singleQuoted = attributeMatcher.group(4);

		if (singleQuoted != null) {
			return singleQuoted;
		}

		String unquoted = attributeMatcher.group(5);

		if (unquoted != null) {
			return unquoted;
		}

		return "";
	}

	private JSONArray _parseImages(String html) {
		JSONArray imagesJSONArray = new JSONArray();

		if ((html == null) || html.isBlank()) {
			return imagesJSONArray;
		}

		Matcher imageMatcher = _imagePattern.matcher(html);

		while (imageMatcher.find()) {
			String attributes = imageMatcher.group(1);

			String alt = "";
			boolean altPresent = false;
			String src = "";

			Matcher attributeMatcher = _attributePattern.matcher(attributes);

			while (attributeMatcher.find()) {
				String name = StringUtil.toLowerCase(attributeMatcher.group(1));

				if (name.equals("alt")) {
					alt = _attributeValue(attributeMatcher);
					altPresent = true;
				}
				else if (name.equals("src")) {
					src = _attributeValue(attributeMatcher);
				}
			}

			imagesJSONArray.put(
				new JSONObject(
				).put(
					"alt", alt
				).put(
					"altPresent", altPresent
				).put(
					"src", src
				));
		}

		return imagesJSONArray;
	}

	private static final Pattern _attributePattern = Pattern.compile(
		"([a-zA-Z_:][-a-zA-Z0-9_:.]*)" +
			"(\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'=<>`]+)))?");
	private static final Pattern _imagePattern = Pattern.compile(
		"<img\\b([^>]*)>", Pattern.CASE_INSENSITIVE);

	private final String _canonicalURL;
	private final JSONArray _imagesJSONArray;
	private final List<String> _links = new ArrayList<>();
	private final String _url;

}