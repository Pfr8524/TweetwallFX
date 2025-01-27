/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018-2022 TweetWallFX
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.tweetwallfx.google.vision;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.ImageAnnotatorSettings;
import com.google.cloud.vision.v1.ImageSource;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.tweetwallfx.cache.Cache;
import org.tweetwallfx.cache.CacheManagerProvider;
import org.tweetwallfx.config.Configuration;
import org.tweetwallfx.google.GoogleSettings;
import org.tweetwallfx.google.vision.CloudVisionSettings.FeatureType;

/**
 * Caches the content analysis performed via Google Vision API via the medias
 * URI.
 */
public final class GoogleVisionCache {

    private static final Logger LOG = LogManager.getLogger(GoogleVisionCache.class);
    private static final GoogleSettings GOOGLE_SETTINGS = Configuration.getInstance()
            .getConfigTyped(GoogleSettings.CONFIG_KEY, GoogleSettings.class);
    /**
     * Cache instance.
     */
    public static final GoogleVisionCache INSTANCE = new GoogleVisionCache();
    private final ImageAnnotatorSettings imageAnnotatorSettings;
    private final Cache<String, ImageContentAnalysis> cache;
    private volatile ImageAnnotatorClient client;

    private GoogleVisionCache() {
        try {
            final GoogleCredentials credentials;

            if (null != GOOGLE_SETTINGS.credentialBase64()) {
                final ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(GOOGLE_SETTINGS.credentialBase64()));
                try {
                    credentials = GoogleCredentials.fromStream(bais);
                } catch (final IOException ex) {
                    throw new IOException("Failed loading Google Credentials from BASE64 encoded credential data", ex);
                }
            } else if (null != GOOGLE_SETTINGS.credentialFilePath()) {
                try (final FileInputStream fis = new FileInputStream(GOOGLE_SETTINGS.credentialFilePath())) {
                    credentials = GoogleCredentials.fromStream(fis);
                } catch (final IOException ex) {
                    throw new IOException("Failed loading Google Credentials from '" + GOOGLE_SETTINGS.credentialFilePath() + "'", ex);
                }
            } else {
                credentials = null;
            }

            this.imageAnnotatorSettings = null == credentials
                    ? null
                    : ImageAnnotatorSettings.newBuilder().setCredentialsProvider(() -> credentials).build();
        } catch (final IOException ex) {
            LOG.error(ex, ex);
            throw new IllegalStateException("Failed loading ImageAnnotatorSettings with google credentials", ex);
        }

        cache = CacheManagerProvider.getCache(
                "googleVision",
                String.class,
                ImageContentAnalysis.class);
    }

    private static Feature.Type convertFeatureType(final FeatureType featureType) {
        return switch (featureType) {
            case CROP_HINTS -> Feature.Type.CROP_HINTS;
            case DOCUMENT_TEXT_DETECTION -> Feature.Type.DOCUMENT_TEXT_DETECTION;
            case FACE_DETECTION -> Feature.Type.FACE_DETECTION;
            case IMAGE_PROPERTIES -> Feature.Type.IMAGE_PROPERTIES;
            case LABEL_DETECTION -> Feature.Type.LABEL_DETECTION;
            case LANDMARK_DETECTION -> Feature.Type.LANDMARK_DETECTION;
            case LOGO_DETECTION -> Feature.Type.LOGO_DETECTION;
            case OBJECT_LOCALIZATION -> Feature.Type.OBJECT_LOCALIZATION;
            case SAFE_SEARCH_DETECTION -> Feature.Type.SAFE_SEARCH_DETECTION;
            case TEXT_DETECTION -> Feature.Type.TEXT_DETECTION;
            case WEB_DETECTION -> Feature.Type.WEB_DETECTION;
        };
    }

    public Map<String, ImageContentAnalysis> getCachedOrLoad(final Stream<String> imageUris) throws IOException {
        final Map<String, ImageContentAnalysis> result = new HashMap<>();

        imageUris.distinct()
                .forEach(imageUri
                        -> result.put(
                        imageUri,
                        cache.get(imageUri)));

        result.putAll(
                load(result.entrySet().stream()
                        .filter(e -> null == e.getValue())
                        .map(Map.Entry::getKey)));

        return result;
    }

    private Map<String, ImageContentAnalysis> load(final Stream<String> imageUris) throws IOException {
        if (null == getClient()) {
            return Collections.emptyMap();
        }

        final List<AnnotateImageRequest> requests = imageUris
                .filter(Objects::nonNull)
                .distinct()
                .map(this::createImageRequest)
                .peek(air -> LOG.info("Prepared {}", air))
                .toList();

        if (requests.isEmpty()) {
            return Collections.emptyMap();
        }

        LOG.info("Executing analysis for {} AnnotateImageRequests", requests.size());
        final BatchAnnotateImagesResponse batchResponse = getClient().batchAnnotateImages(requests);
        final Iterator<AnnotateImageResponse> itResponse = batchResponse.getResponsesList().iterator();
        final Iterator<AnnotateImageRequest> itRequest = requests.iterator();
        final Map<String, ImageContentAnalysis> result = new LinkedHashMap<>(requests.size());

        while (itRequest.hasNext() && itResponse.hasNext()) {
            final AnnotateImageRequest request = itRequest.next();
            final AnnotateImageResponse response = itResponse.next();

            final String uri = request.getImage().getSource().getImageUri();
            final ImageContentAnalysis ica = ImageContentAnalysis.of(response);
            LOG.info("Image('{}') was evaluated as {}", uri, ica);
            result.put(uri, ica);
            cache.put(uri, ica);
        }

        if (itRequest.hasNext()) {
            throw new IllegalStateException("There are still annotate Responses available!");
        } else if (itRequest.hasNext()) {
            throw new IllegalStateException("There are still annotate Requests available!");
        } else {
            return Collections.unmodifiableMap(result);
        }
    }

    private AnnotateImageRequest createImageRequest(final String imageUri) {
        final AnnotateImageRequest.Builder builder = AnnotateImageRequest.newBuilder()
                .setImage(Image.newBuilder()
                        .setSource(ImageSource.newBuilder()
                                .setImageUri(imageUri)));

        GOOGLE_SETTINGS.cloudVision().featureTypes().stream()
                .map(GoogleVisionCache::convertFeatureType)
                .forEach(builder.addFeaturesBuilder()::setType);

        return builder.build();
    }

    private ImageAnnotatorClient getClient() throws IOException {
        if (null == client && null != imageAnnotatorSettings) {
            synchronized (this) {
                if (null == client) {
                    client = createClient();
                }
            }
        }

        return client;
    }

    private ImageAnnotatorClient createClient() throws IOException {
        return ImageAnnotatorClient.create(imageAnnotatorSettings);
    }
}
