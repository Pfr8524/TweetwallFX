/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 TweetWallFX
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
package org.tweetwallfx.conference.stepengine.dataprovider;

import javafx.scene.image.Image;
import org.tweetwallfx.stepengine.api.DataProvider;
import org.tweetwallfx.stepengine.api.config.StepEngineSettings;
import static org.tweetwallfx.util.Nullable.valueOrDefault;

public class TrackImageDataProvider implements DataProvider {

    private final Config config;

    private TrackImageDataProvider(final Config config) {
        this.config = config;
    }

    public Image getImage(final String url) {
        return new Image(
                TrackImageCache.INSTANCE.getCachedOrLoad(url).getInputStream(),
                config.profileWidth(),
                config.profileHeight(),
                config.preserveRation(),
                config.smooth());
    }

    public static class FactoryImpl implements DataProvider.Factory {

        @Override
        public TrackImageDataProvider create(final StepEngineSettings.DataProviderSetting dataProviderSetting) {
            return new TrackImageDataProvider(dataProviderSetting.getConfig(Config.class));
        }

        @Override
        public Class<TrackImageDataProvider> getDataProviderClass() {
            return TrackImageDataProvider.class;
        }
    }

    private static record Config(
            Integer profileWidth,
            Integer profileHeight,
            Boolean preserveRation,
            Boolean smooth) {

        @SuppressWarnings("unused")
        public Config(
                final Integer profileWidth,
                final Integer profileHeight,
                final Boolean preserveRation,
                final Boolean smooth) {
            this.profileWidth = valueOrDefault(profileWidth, 32);
            this.profileHeight = valueOrDefault(profileHeight, 32);
            this.preserveRation = valueOrDefault(preserveRation, true);
            this.smooth = valueOrDefault(smooth, true);
        }
    }
}
