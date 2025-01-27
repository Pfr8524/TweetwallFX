/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2022 TweetWallFX
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
package org.tweetwallfx.controls;

import java.util.Objects;

public final class Word implements Comparable<Word> {

    private final String text;
    private final double weight;

    public Word(final String text, final double weight) {
        this.text = text;
        this.weight = weight;
    }

    public String getText() {
        return text;
    }

    @Override
    public int compareTo(final Word o) {
        return Double.compare(weight, o.weight);
    }

    public double getWeight() {
        return weight;
    }

    @Override
    public String toString() {
        return "Word{" + "text=" + text + ", weight=" + weight + '}';
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.text.toLowerCase());
    }

    @Override
    public boolean equals(final Object obj) {
        return obj instanceof Word other
                && this.text.equalsIgnoreCase(other.text);
    }
}
