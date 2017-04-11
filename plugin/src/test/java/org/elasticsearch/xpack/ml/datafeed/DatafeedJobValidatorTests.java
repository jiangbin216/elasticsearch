/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.ml.datafeed;

import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.AggregatorFactories;
import org.elasticsearch.search.aggregations.bucket.histogram.HistogramAggregationBuilder;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xpack.ml.job.config.AnalysisConfig;
import org.elasticsearch.xpack.ml.job.config.Detector;
import org.elasticsearch.xpack.ml.job.config.Job;
import org.elasticsearch.xpack.ml.job.messages.Messages;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

public class DatafeedJobValidatorTests extends ESTestCase {

    public void testValidate_GivenNonZeroLatency() {
        String errorMessage = Messages.getMessage(Messages.DATAFEED_DOES_NOT_SUPPORT_JOB_WITH_LATENCY);
        Job.Builder builder = buildJobBuilder("foo");
        AnalysisConfig.Builder ac = createAnalysisConfig();
        ac.setBucketSpan(TimeValue.timeValueSeconds(1800));
        ac.setLatency(TimeValue.timeValueSeconds(3600));
        builder.setAnalysisConfig(ac);
        Job job = builder.build(new Date());
        DatafeedConfig datafeedConfig = createValidDatafeedConfig().build();

        IllegalArgumentException e = ESTestCase.expectThrows(IllegalArgumentException.class,
                () -> DatafeedJobValidator.validate(datafeedConfig, job));

        assertEquals(errorMessage, e.getMessage());
    }

    public void testVerify_GivenZeroLatency() {
        Job.Builder builder = buildJobBuilder("foo");
        AnalysisConfig.Builder ac = createAnalysisConfig();
        ac.setBucketSpan(TimeValue.timeValueSeconds(1800));
        ac.setLatency(TimeValue.ZERO);
        builder.setAnalysisConfig(ac);
        Job job = builder.build(new Date());
        DatafeedConfig datafeedConfig = createValidDatafeedConfig().build();

        DatafeedJobValidator.validate(datafeedConfig, job);
    }

    public void testVerify_GivenNoLatency() {
        Job.Builder builder = buildJobBuilder("foo");
        AnalysisConfig.Builder ac = createAnalysisConfig();
        ac.setBucketSpan(TimeValue.timeValueSeconds(100));
        builder.setAnalysisConfig(ac);
        Job job = builder.build(new Date());
        DatafeedConfig datafeedConfig = createValidDatafeedConfig().build();

        DatafeedJobValidator.validate(datafeedConfig, job);
    }

    public void testVerify_GivenAggsAndNoSummaryCountField() throws IOException {
        String errorMessage = Messages.getMessage(Messages.DATAFEED_AGGREGATIONS_REQUIRES_JOB_WITH_SUMMARY_COUNT_FIELD,
                DatafeedConfig.DOC_COUNT);
        Job.Builder builder = buildJobBuilder("foo");
        AnalysisConfig.Builder ac = createAnalysisConfig();
        ac.setSummaryCountFieldName(null);
        ac.setBucketSpan(TimeValue.timeValueSeconds(1800));
        builder.setAnalysisConfig(ac);
        Job job = builder.build(new Date());
        DatafeedConfig datafeedConfig = createValidDatafeedConfigWithAggs(1800.0).build();

        IllegalArgumentException e = ESTestCase.expectThrows(IllegalArgumentException.class,
                () -> DatafeedJobValidator.validate(datafeedConfig, job));

        assertEquals(errorMessage, e.getMessage());
    }

    public void testVerify_GivenAggsAndEmptySummaryCountField() throws IOException {
        String errorMessage = Messages.getMessage(Messages.DATAFEED_AGGREGATIONS_REQUIRES_JOB_WITH_SUMMARY_COUNT_FIELD,
                DatafeedConfig.DOC_COUNT);
        Job.Builder builder = buildJobBuilder("foo");
        AnalysisConfig.Builder ac = createAnalysisConfig();
        ac.setSummaryCountFieldName("");
        ac.setBucketSpan(TimeValue.timeValueSeconds(1800));
        builder.setAnalysisConfig(ac);
        Job job = builder.build(new Date());
        DatafeedConfig datafeedConfig = createValidDatafeedConfigWithAggs(1800.0).build();

        IllegalArgumentException e = ESTestCase.expectThrows(IllegalArgumentException.class,
                () -> DatafeedJobValidator.validate(datafeedConfig, job));

        assertEquals(errorMessage, e.getMessage());
    }

    public void testVerify_GivenAggsAndSummaryCountField() throws IOException {
        Job.Builder builder = buildJobBuilder("foo");
        AnalysisConfig.Builder ac = createAnalysisConfig();
        ac.setSummaryCountFieldName("some_count");
        ac.setBucketSpan(TimeValue.timeValueSeconds(1800));
        builder.setAnalysisConfig(ac);
        Job job = builder.build(new Date());
        DatafeedConfig datafeedConfig = createValidDatafeedConfigWithAggs(900.0).build();
        DatafeedJobValidator.validate(datafeedConfig, job);
    }

    public void testVerify_GivenHistogramIntervalGreaterThanBucketSpan() throws IOException {
        Job.Builder builder = buildJobBuilder("foo");
        AnalysisConfig.Builder ac = createAnalysisConfig();
        ac.setSummaryCountFieldName("some_count");
        ac.setBucketSpan(TimeValue.timeValueSeconds(1800));
        builder.setAnalysisConfig(ac);
        Job job = builder.build(new Date());
        DatafeedConfig datafeedConfig = createValidDatafeedConfigWithAggs(1800001.0).build();

        IllegalArgumentException e = ESTestCase.expectThrows(IllegalArgumentException.class,
                () -> DatafeedJobValidator.validate(datafeedConfig, job));

        assertEquals("Aggregation interval [1800001ms] must be less than or equal to the bucket_span [1800000ms]", e.getMessage());
    }

    private static Job.Builder buildJobBuilder(String id) {
        Job.Builder builder = new Job.Builder(id);
        AnalysisConfig.Builder ac = createAnalysisConfig();
        builder.setAnalysisConfig(ac);
        return builder;
    }

    public static AnalysisConfig.Builder createAnalysisConfig() {
        Detector.Builder d1 = new Detector.Builder("info_content", "domain");
        d1.setOverFieldName("client");
        Detector.Builder d2 = new Detector.Builder("min", "field");
        AnalysisConfig.Builder ac = new AnalysisConfig.Builder(Arrays.asList(d1.build(), d2.build()));
        return ac;
    }

    private static DatafeedConfig.Builder createValidDatafeedConfigWithAggs(double interval) throws IOException {
        HistogramAggregationBuilder histogram = AggregationBuilders.histogram("time").interval(interval);
        DatafeedConfig.Builder datafeedConfig = createValidDatafeedConfig();
        datafeedConfig.setAggregations(new AggregatorFactories.Builder().addAggregator(histogram));
        return datafeedConfig;
    }

    private static DatafeedConfig.Builder createValidDatafeedConfig() {
        DatafeedConfig.Builder builder = new DatafeedConfig.Builder("my-datafeed", "my-job");
        builder.setIndexes(Collections.singletonList("myIndex"));
        builder.setTypes(Collections.singletonList("myType"));
        return builder;
    }
}
