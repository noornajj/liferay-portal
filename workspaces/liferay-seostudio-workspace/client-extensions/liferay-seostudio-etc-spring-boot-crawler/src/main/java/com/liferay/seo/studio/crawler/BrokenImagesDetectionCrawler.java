/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.seo.studio.crawler;

import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.seo.studio.model.CrawlHit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.time.Duration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import org.springframework.stereotype.Component;

/**
 * @author Noor Najjar
 */
@Component
public class BrokenImagesDetectionCrawler extends BaseDetectionCrawler {

	@Override
	public void detect(
			long accountEntryId, List<CrawlHit> crawlHits, URI hostname,
			long seoStudioScanId)
		throws Exception {

		Map<String, Set<String>> pagesByImageURLMap =
			_collectPagesByImageURLMap(crawlHits);

		if (pagesByImageURLMap.isEmpty()) {
			if (_log.isInfoEnabled()) {
				_log.info(
					"No images were detected for scan " + seoStudioScanId);
			}

			return;
		}

		Set<String> brokenImageURLs = _findBrokenImageURLs(
			pagesByImageURLMap.keySet());

		List<String> pageURLs = _affectedPageURLs(
			pagesByImageURLMap, brokenImageURLs);

		if (ListUtil.isEmpty(pageURLs)) {
			if (_log.isInfoEnabled()) {
				_log.info(
					"No broken image URLs were detected for scan " +
						seoStudioScanId);
			}

			return;
		}

		Map<String, Long> pageIdsByURLMap = ensurePages(
			seoStudioScanId, accountEntryId, pageURLs);

		writeInsights(
			_BROKEN_IMAGE_URLS_JSON_OBJECT, seoStudioScanId, accountEntryId,
			pageURLs, pageIdsByURLMap);
	}

	private List<String> _affectedPageURLs(
		Map<String, Set<String>> pagesByImageURLMap,
		Set<String> brokenImageURLs) {

		Set<String> pageURLs = new LinkedHashSet<>();

		for (String brokenImageURL : brokenImageURLs) {
			Set<String> brokenImagePageURLs = pagesByImageURLMap.get(
				brokenImageURL);

			if (brokenImagePageURLs != null) {
				pageURLs.addAll(brokenImagePageURLs);
			}
		}

		return new ArrayList<>(pageURLs);
	}

	private Map<String, Set<String>> _collectPagesByImageURLMap(
		List<CrawlHit> crawlHits) {

		Map<String, Set<String>> pagesByImageURLMap = new LinkedHashMap<>();

		for (CrawlHit crawlHit : crawlHits) {
			String pageURL = getPageURL(crawlHit);

			if (pageURL == null) {
				continue;
			}

			JSONArray imagesJSONArray = crawlHit.getImages();

			for (int i = 0; i < imagesJSONArray.length(); i++) {
				JSONObject imageJSONObject = imagesJSONArray.getJSONObject(i);

				String imageURL = _resolveImageURL(
					pageURL, imageJSONObject.getString("src"));

				if (imageURL == null) {
					continue;
				}

				Set<String> imagePageURLs = pagesByImageURLMap.computeIfAbsent(
					imageURL, imageURLKey -> new LinkedHashSet<>());

				imagePageURLs.add(pageURL);
			}
		}

		return pagesByImageURLMap;
	}

	private Set<String> _findBrokenImageURLs(Set<String> imageURLs) {
		Set<String> brokenImageURLs = ConcurrentHashMap.newKeySet();

		ExecutorService executorService = Executors.newFixedThreadPool(
			Math.min(_MAX_CONCURRENCY, imageURLs.size()));

		try {
			List<Future<?>> futures = new ArrayList<>();

			for (String imageURL : imageURLs) {
				Future<?> future = executorService.submit(
					() -> {
						if (_isBroken(imageURL)) {
							brokenImageURLs.add(imageURL);
						}
					});

				futures.add(future);
			}

			for (Future<?> future : futures) {
				try {
					future.get();
				}
				catch (Exception exception) {
					if (_log.isWarnEnabled()) {
						_log.warn("Unable to check an image URL", exception);
					}
				}
			}
		}
		finally {
			executorService.shutdown();
		}

		return brokenImageURLs;
	}

	private boolean _isBroken(String imageURL) {
		try {
			HttpRequest httpRequest = HttpRequest.newBuilder(
				URI.create(imageURL)
			).method(
				"HEAD", HttpRequest.BodyPublishers.noBody()
			).timeout(
				_REQUEST_TIMEOUT
			).build();

			HttpResponse<Void> httpResponse = _httpClient.send(
				httpRequest, HttpResponse.BodyHandlers.discarding());

			int statusCode = httpResponse.statusCode();

			if ((statusCode == 405) || (statusCode == 501)) {
				httpRequest = HttpRequest.newBuilder(
					URI.create(imageURL)
				).header(
					"Range", "bytes=0-0"
				).timeout(
					_REQUEST_TIMEOUT
				).build();

				httpResponse = _httpClient.send(
					httpRequest, HttpResponse.BodyHandlers.discarding());

				statusCode = httpResponse.statusCode();
			}

			if (statusCode >= 400) {
				return true;
			}

			return false;
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug("Unable to reach image URL " + imageURL, exception);
			}

			return true;
		}
	}

	private String _resolveImageURL(String pageURL, String src) {
		if (src.isBlank() || src.startsWith("data:")) {
			return null;
		}

		try {
			URI pageURI = URI.create(pageURL);

			URI imageURI = pageURI.resolve(src);

			return imageURI.toString();
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug("Unable to resolve image URL " + src, exception);
			}

			return null;
		}
	}

	private static final JSONObject _BROKEN_IMAGE_URLS_JSON_OBJECT =
		new JSONObject(
		).put(
			"category", "images"
		).put(
			"classification", "problem"
		).put(
			"description",
			StringBundler.concat(
				"One or more <img> tags on this page point to URLs that ",
				"return 404. Broken images leave visible gaps in the layout, ",
				"hurt user trust, and waste browser request budget on ",
				"resources that will not load.")
		).put(
			"insightType", "broken_image_urls"
		).put(
			"name", "brokenImageURLs"
		).put(
			"severity", "high"
		);

	private static final int _MAX_CONCURRENCY = 32;

	private static final Duration _REQUEST_TIMEOUT = Duration.ofSeconds(10);

	private static final Log _log = LogFactory.getLog(
		BrokenImagesDetectionCrawler.class);

	private final HttpClient _httpClient = HttpClient.newBuilder(
	).connectTimeout(
		_REQUEST_TIMEOUT
	).followRedirects(
		HttpClient.Redirect.NORMAL
	).build();

}