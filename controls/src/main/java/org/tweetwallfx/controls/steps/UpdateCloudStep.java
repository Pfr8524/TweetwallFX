/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2022 TweetWallFX
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
package org.tweetwallfx.controls.steps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Transition;
import javafx.geometry.Bounds;
import javafx.scene.text.Text;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.tweetwallfx.controls.Word;
import org.tweetwallfx.controls.WordleLayout;
import org.tweetwallfx.controls.WordleSkin;
import org.tweetwallfx.controls.dataprovider.TagCloudDataProvider;
import org.tweetwallfx.stepengine.api.DataProvider;
import org.tweetwallfx.stepengine.api.Step;
import org.tweetwallfx.stepengine.api.StepEngine.MachineContext;
import org.tweetwallfx.stepengine.api.config.StepEngineSettings;
import org.tweetwallfx.transitions.LocationTransition;

public class UpdateCloudStep implements Step {

    private UpdateCloudStep() {
        // prevent external instantiation
    }

    private static final Logger LOGGER = LogManager.getLogger(UpdateCloudStep.class);

    @Override
    public java.time.Duration preferredStepDuration(final MachineContext context) {
        return java.time.Duration.ofSeconds(5);
    }

    @Override
    public void doStep(final MachineContext context) {
        List<Word> sortedWords = context.getDataProvider(TagCloudDataProvider.class).getWords();
        if (sortedWords.isEmpty()) {
            return;
        }

        WordleSkin wordleSkin = (WordleSkin) context.get("WordleSkin");
        Bounds layoutBounds = wordleSkin.getPane().getLayoutBounds();
        List<Word> limitedWords = sortedWords.stream()
                .limit(wordleSkin.getDisplayCloudTags())
                .collect(Collectors.toList());
        List<Word> additionalTagCloudWords = context.getDataProvider(TagCloudDataProvider.class).getAdditionalTweetWords();

        double minWeight = limitedWords.stream().mapToDouble(Word::getWeight).min().orElse(-2d);

        if (null != additionalTagCloudWords) {
            additionalTagCloudWords.stream()
                    .map(word -> new Word(word.getText(), minWeight))
                    .forEach(limitedWords::add);
        }
        limitedWords.sort(Comparator.reverseOrder());

        WordleLayout.Configuration configuration = new WordleLayout.Configuration(limitedWords, wordleSkin.getFont(), wordleSkin.getFontSizeMax(), layoutBounds);
        if (null != wordleSkin.getLogo()) {
            configuration.setBlockedAreaBounds(wordleSkin.getLogo().getBoundsInParent());
        }
        if (null != wordleSkin.getSecondLogo()) {
            configuration.setBlockedAreaBounds(wordleSkin.getSecondLogo().getBoundsInParent());
        }

        WordleLayout cloudWordleLayout = WordleLayout.createWordleLayout(configuration);
        List<Word> unusedWords = wordleSkin.word2TextMap.keySet().stream()
                .filter(word -> !cloudWordleLayout.getWordLayoutInfo().containsKey(word))
                .toList();

        Duration defaultDuration = Duration.seconds(1.5);

        SequentialTransition morph = new SequentialTransition();

        List<Transition> fadeOutTransitions = new ArrayList<>();
        List<Transition> moveTransitions = new ArrayList<>();
        List<Transition> fadeInTransitions = new ArrayList<>();

        LOGGER.info("Unused words in cloud: " + unusedWords.stream().map(Word::getText).collect(Collectors.joining(", ")));

        unusedWords.forEach(word -> {
            Text textNode = wordleSkin.word2TextMap.remove(word);

            FadeTransition ft = new FadeTransition(defaultDuration, textNode);
            ft.setToValue(0);
            ft.setOnFinished(event
                    -> wordleSkin.getPane().getChildren().remove(textNode));
            fadeOutTransitions.add(ft);
        });

        ParallelTransition fadeOuts = new ParallelTransition();
        fadeOuts.getChildren().addAll(fadeOutTransitions);
        morph.getChildren().add(fadeOuts);

        List<Word> existingWords = cloudWordleLayout.getWordLayoutInfo().keySet().stream()
                .filter(wordleSkin.word2TextMap::containsKey)
                .toList();

        LOGGER.info("Existing words in cloud: " + existingWords.stream().map(Word::getText).collect(Collectors.joining(", ")));
        existingWords.forEach(word -> {
            Text textNode = wordleSkin.word2TextMap.get(word);
            cloudWordleLayout.fontSizeAdaption(textNode, word.getWeight());
            Bounds bounds = cloudWordleLayout.getWordLayoutInfo().get(word);

            moveTransitions.add(new LocationTransition(defaultDuration, textNode)
                    .withX(textNode.getLayoutX(), bounds.getMinX() + layoutBounds.getWidth() / 2d)
                    .withY(textNode.getLayoutY(), bounds.getMinY() + layoutBounds.getHeight() / 2d + bounds.getHeight() / 2d));
        });

        ParallelTransition moves = new ParallelTransition();
        moves.getChildren().addAll(moveTransitions);
        morph.getChildren().add(moves);

        List<Word> newWords = cloudWordleLayout.getWordLayoutInfo().keySet().stream()
                .filter(word -> !wordleSkin.word2TextMap.containsKey(word))
                .toList();

        List<Text> newTextNodes = new ArrayList<>();
        LOGGER.info("New words in cloud: " + newWords.stream().map(Word::getText).collect(Collectors.joining(", ")));
        newWords.forEach(word -> {
            Text textNode = cloudWordleLayout.createTextNode(word);
            wordleSkin.word2TextMap.put(word, textNode);

            Bounds bounds = cloudWordleLayout.getWordLayoutInfo().get(word);
            textNode.setLayoutX(bounds.getMinX() + layoutBounds.getWidth() / 2d);
            textNode.setLayoutY(bounds.getMinY() + layoutBounds.getHeight() / 2d + bounds.getHeight() / 2d);
            textNode.setOpacity(0);
            newTextNodes.add(textNode);
            FadeTransition ft = new FadeTransition(defaultDuration, textNode);
            ft.setToValue(1);
            fadeInTransitions.add(ft);
        });
        wordleSkin.getPane().getChildren().addAll(newTextNodes);

        ParallelTransition fadeIns = new ParallelTransition();
        fadeIns.getChildren().addAll(fadeInTransitions);
        morph.getChildren().add(fadeIns);

        morph.setOnFinished(e -> context.proceed());
        morph.play();
    }

    /**
     * Implementation of {@link Step.Factory} as Service implementation creating
     * {@link UpdateCloudStep}.
     */
    public static final class FactoryImpl implements Step.Factory {

        @Override
        public UpdateCloudStep create(final StepEngineSettings.StepDefinition stepDefinition) {
            return new UpdateCloudStep();
        }

        @Override
        public Class<UpdateCloudStep> getStepClass() {
            return UpdateCloudStep.class;
        }

        @Override
        public Collection<Class<? extends DataProvider>> getRequiredDataProviders(final StepEngineSettings.StepDefinition stepSettings) {
            return Arrays.asList(TagCloudDataProvider.class);
        }
    }
}
