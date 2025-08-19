# Response Handler for Spring WebFlux

A lightweight handler that enforces a **consistent JSON envelope** for spring boot and webflux projects and guards
against invalid reactive return types.

## Response Format

All responses are wrapped into a common envelope:

- `@ResponseHandler` for `Mono<T>,Flux<T>,Mono<List<T>>,Object`
- `Invalid Response` for `Flux<Mono<T>>,Mono<Flux<T>>`

```json
{
  "timestamp": "2025-08-16T12:00:00Z",
  "status": 200,
  "result": true,
  "data": {}
}
```

Supports validation for @RequestParam and @RequestBody; all violations are returned under the errors field.

```json
{
  "timestamp": "...",
  "status": 400,
  "result": false,
  "errors": {
    "name": "name is required",
    "age": "age must be >= 1"
  },
  "message": "Validation error"
}
```

## 📌 Version History

| Version | Date       | Compatibility                   | Key Changes                                                                                                                    | Type    |
|--------:|------------|---------------------------------|--------------------------------------------------------------------------------------------------------------------------------|---------|
|  v1.0.0 | 2025-08-01 | **Spring WebFlux only**         | Base handler with **JSON envelope** for `Mono<T>`, `Flux<T>`, `Mono<List<T>>`; **rejects** `Flux<Mono<T>>` / `Mono<Flux<T>>`.  | Initial |
|  v1.2.0 | 2025-08-10 | **Spring WebFlux + Spring MVC** | Added support for **non-reactive controllers** (`Object`, `ResponseEntity<?>`) while keeping a consistent **JSON envelope**.   | Feature |
|  v1.4.1 | 2025-08-15 | **Spring WebFlux + Spring MVC** | Introduced **unified error handling**: Bean Validation (`@RequestParam`, `@RequestBody`) with `errors` and `message` response. | Fix/Enh |