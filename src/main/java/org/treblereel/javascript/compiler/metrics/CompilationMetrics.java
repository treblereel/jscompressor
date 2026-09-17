/*
 * Copyright © 2025 Treblereel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.treblereel.javascript.compiler.metrics;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@ApplicationScoped
public class CompilationMetrics {

  private final AtomicInteger activeCompilations = new AtomicInteger();

  @Inject
  MeterRegistry registry;

  @PostConstruct
  void register() {
    Gauge.builder("jscompressor.compilation.active", activeCompilations, AtomicInteger::get)
        .description("Compilations currently being processed")
        .strongReference(true)
        .register(registry);
  }

  public Observation start(long inputSize) {
    activeCompilations.incrementAndGet();
    return new Observation(inputSize, Timer.start(registry));
  }

  public final class Observation implements AutoCloseable {

    private final long initialInputSize;
    private final Timer.Sample timer;
    private String outcome = "internal_error";
    private long inputSize;
    private long outputSize;
    private boolean closed;

    private Observation(long inputSize, Timer.Sample timer) {
      this.initialInputSize = inputSize;
      this.inputSize = inputSize;
      this.timer = timer;
    }

    public void outcome(String outcome) {
      this.outcome = outcome;
    }

    public void sizes(long inputSize, long outputSize) {
      this.inputSize = inputSize;
      this.outputSize = outputSize;
    }

    @Override
    public void close() {
      if (closed) {
        return;
      }
      closed = true;
      activeCompilations.decrementAndGet();
      registry.counter("jscompressor.compilation.results", "outcome", outcome).increment();
      timer.stop(Timer.builder("jscompressor.compilation.duration")
          .description("Time spent downloading and compiling JavaScript")
          .tag("outcome", outcome)
          .publishPercentileHistogram()
          .register(registry));
      DistributionSummary.builder("jscompressor.compilation.input")
          .description("Source characters processed per compilation")
          .baseUnit("characters")
          .tag("outcome", outcome)
          .register(registry)
          .record(inputSize > 0 ? inputSize : initialInputSize);
      DistributionSummary.builder("jscompressor.compilation.output")
          .description("Compiled characters produced per compilation")
          .baseUnit("characters")
          .tag("outcome", outcome)
          .register(registry)
          .record(outputSize);
    }
  }
}
