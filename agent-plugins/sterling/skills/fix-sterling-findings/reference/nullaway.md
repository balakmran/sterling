# NullAway findings

NullAway reports `[NullAway]` errors only in packages marked `@NullMarked`
(in `package-info.java`). Fix the nullness; don't change which code is checked.

| Message | Fix |
|---|---|
| `dereferenced expression 'x' is @Nullable` | Check before use (`if (x == null) ...`), restructure so it can't be null, or if `x` can't be null at that point, remove the `@Nullable` where it came from |
| `returning @Nullable expression from method with @NonNull return type` | Make the return type `@Nullable` if null is a real result, and fix the callers; otherwise return a non-null value |
| `passing @Nullable parameter 'x' where @NonNull is required` | Check first, or make the callee accept `@Nullable` when null is meaningful there |
| `initializer method does not guarantee @NonNull field 'f' is initialized` | Assign the field in every constructor, or make it `@Nullable`. Prefer constructor injection with `final` fields |
| `@NonNull field 'f' not initialized` | Same; Spring field injection is the usual cause, so switch to constructor injection |
| `assigning @Nullable expression to @NonNull field` | Check the value first, or make the field `@Nullable` |

- Annotate with `org.jspecify.annotations.Nullable` on the type: `@Nullable String`,
  `String @Nullable []`. Never `javax.annotation`, `jakarta.annotation` or
  `org.springframework.lang`.
- At `STRICT`, JSpecify mode also checks generics: `List<@Nullable String>`
  holds nulls, `List<String>` doesn't.
- `Assert.notNull(x, "...")`, `Objects.requireNonNull(x)` and AssertJ's
  `assertThat(x).isNotNull()` count as null checks.
- Known limits (late initialisation as in `afterPropertiesSet()`, lambdas,
  reflection, map keys) have a documented suppression, in
  `docs/guide/tools.md` of the Sterling repository ("NullAway with Spring");
  use it with its reason as the comment.
- Never mark a package `@NullMarked` to add checking or remove the annotation to
  silence a finding.
