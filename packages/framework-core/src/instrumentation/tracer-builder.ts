import { CallInformation, Tracer } from "./abstract-tracer";

export class TracerBuilder<T = unknown> {
  private tracers: Tracer<T>[] = [];

  private metadataInjector: (callInfo: CallInformation<T>) => CallInformation<T> = (callInfo) => callInfo


  private constructor(initialTracer?: Tracer<T>) {
    if (initialTracer) {
      this.tracers.push(initialTracer);
    }
  }

  static fromTracer<T>(tracer: Tracer<T>): TracerBuilder<T> {
    return new TracerBuilder(tracer);
  }

  append(tracer: Tracer<T>): TracerBuilder<T> {
    this.tracers.push(tracer);
    return this;
  }

  prepend(tracer: Tracer<T>): TracerBuilder<T> {
    this.tracers.unshift(tracer);
    return this;
  }

  build(): Tracer<T> {
    const tracers = this.tracers
    return new (class extends Tracer<T> {
      onStart(callInformation: CallInformation<T>): void {
        for (const tracer of tracers) {
          tracer.onStart(callInformation);
        }
      }

      onSuccess(callInformation: CallInformation<T>, result: unknown): void {
        for (const tracer of tracers) {
          tracer.onSuccess(callInformation, result);
        }
      }

      onError(callInformation: CallInformation<T>, error: unknown): void {
        for (const tracer of tracers) {
          tracer.onError(callInformation, error);
        }
      }
    })();
  }

  injectMetadata(injector: (callInfo: CallInformation<T>) => CallInformation<T>): TracerBuilder<T> {
    const existingInjector = this.metadataInjector
    this.metadataInjector = (innerCallInfo: CallInformation<T>) => injector(existingInjector(innerCallInfo))
    return this;
  }

}
