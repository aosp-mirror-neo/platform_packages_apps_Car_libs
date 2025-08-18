# Lazy Configuration: Providers and Properties

Lazy configuration is a core principle for creating performant, scalable, and cacheable Gradle builds. It defers the calculation of a value until it is absolutely required during the **execution phase**, avoiding unnecessary work during the **configuration phase**.

The key APIs are `Provider<T>` (read-only) and `Property<T>` (read-write).

## Key APIs

*   **`Property<T>` (Read/Write):** A `Provider<T>` that can also be set. It is the standard way to define a **configurable input** for a task or extension. In custom tasks, always use `Property<T>` for inputs that users will configure.
*   **`Provider<T>` (Read-Only):** A read-only container for a value that will be provided later. Use `Provider<T>` to pass a value from one place to another without allowing modification, especially for **task outputs**.

## Creating and Transforming Properties

*   **Instantiation:** Never implement `Provider` or `Property` yourself. Obtain an `ObjectFactory` instance via `@Inject` in a custom task's constructor or from `project.objects` to create property instances.
    *   `objects.property(String::class.java)`
    *   `objects.directoryProperty()`
*   **Transformations:** Chain providers together without resolving their values using `map` and `flatMap`.
    *   **`.map { ... }`**: Use for simple, synchronous transformations. It transforms the value inside a `Provider` and returns a `Provider` of the new type.
        ```kotlin
        val stringProvider: Provider<String> = numberProvider.map { it.toString() }
        ```
    *   **`.flatMap { ... }`**: Use when your transformation logic itself returns another `Provider`.
        ```kotlin
        val versionProvider: Provider<String> = providerNameProvider.flatMap { name ->
            project.providers.named(name, String::class.java)
        }
        ```

## Best Practices Summary

*   **Prefer `tasks.register`:** It creates tasks lazily, whereas `tasks.create` is eager.
*   **Use `Property<T>` for Inputs:** Use specific types like `Property<T>`, `DirectoryProperty`, and `RegularFileProperty` for task inputs.
*   **Set Defaults with `.convention()`:** In a task's `init` block or constructor, provide sensible default values.
*   **Wire with `.set()`:** Connect providers to properties using `.set()`.
*   **NO `.get()` in Configuration Phase:** **Never** call `.get()` directly in a `build.gradle.kts` script body or a task configuration block. Only resolve values inside a `@TaskAction` method or a `doFirst`/`doLast` block.
*   **Use `project.layout` for Paths:** Access project paths via `project.layout` (e.g., `layout.buildDirectory`, `layout.projectDirectory`). These methods return `Directory` providers, preserving laziness.