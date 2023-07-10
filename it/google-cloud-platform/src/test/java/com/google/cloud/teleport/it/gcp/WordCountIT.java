/*
 * Copyright (C) 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.teleport.it.gcp;

import static com.google.cloud.teleport.it.truthmatchers.PipelineAsserts.assertThatPipeline;
import static com.google.cloud.teleport.it.truthmatchers.PipelineAsserts.assertThatResult;

import com.google.cloud.teleport.it.common.PipelineLauncher.LaunchConfig;
import com.google.cloud.teleport.it.common.PipelineLauncher.LaunchInfo;
import com.google.cloud.teleport.it.common.PipelineLauncher.Sdk;
import com.google.cloud.teleport.it.common.PipelineOperator.Result;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Count;
import org.apache.beam.sdk.transforms.Filter;
import org.apache.beam.sdk.transforms.FlatMapElements;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.TypeDescriptors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class WordCountIT extends IOLoadTestBase {

  @Rule public TestPipeline wcPipeline = TestPipeline.create();

  @Before
  public void setup() {
    buildPipeline();
  }

  @Test
  public void testWordCountDataflow() throws IOException {
    LaunchConfig options =
        LaunchConfig.builder("test-wordcount")
            .setSdk(Sdk.JAVA)
            .setPipeline(wcPipeline)
            .addParameter("runner", "DataflowRunner")
            .build();

    LaunchInfo launchInfo = pipelineLauncher.launch(PROJECT, REGION, options);
    assertThatPipeline(launchInfo).isRunning();
    Result result =
        pipelineOperator.waitUntilDone(createConfig(launchInfo, Duration.ofMinutes(20)));
    assertThatResult(result).isLaunchFinished();
  }

  /** Build WordCount pipeline. */
  private void buildPipeline() {
    wcPipeline
        .apply(TextIO.read().from("gs://apache-beam-samples/shakespeare/kinglear.txt"))
        .apply(
            FlatMapElements.into(TypeDescriptors.strings())
                .via((String line) -> Arrays.asList(line.split("[^\\p{L}]+"))))
        .apply(Filter.by((String word) -> !word.isEmpty()))
        .apply(Count.perElement())
        .apply(
            MapElements.into(TypeDescriptors.strings())
                .via(
                    (KV<String, Long> wordCount) ->
                        wordCount.getKey() + ": " + wordCount.getValue()))
        .apply(TextIO.write().to("wordcounts"));
  }
}
