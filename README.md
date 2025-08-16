# Response Handler for Spring WebFlux

A lightweight handler that enforces a **consistent JSON envelope** for WebFlux controllers and guards against invalid reactive return types.

## Response Format

All responses are wrapped into a common envelope:

- `@ResponseHandler` for `Mono<T>,Flux<T>,Mono<List<T>>`
- `Invalid Response` for `Flux<Mono<T>>,Mono<Flux<T>>`


```json
{
  "timestamp": "2025-08-16T12:00:00Z",
  "status": 200,
  "result": true,
  "data": {  },
  "message": "only on errors"
}
