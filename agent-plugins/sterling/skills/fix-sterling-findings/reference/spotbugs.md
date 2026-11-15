# SpotBugs findings

`findings.sh` prints each as `{"tool":"SpotBugs","check":"<PATTERN>",...}`. The
pattern's explanation is at `https://spotbugs.readthedocs.io/en/stable/bugDescriptions.html`.
Sterling's default filter already skips `EI_EXPOSE_REP`, `EI_EXPOSE_REP2` and
`CT_CONSTRUCTOR_THROW`, which Spring constructor injection and records trigger.
The patterns below were checked against SpotBugs 4.10.5.

Common ones and their fixes:

| Pattern | Fix |
|---|---|
| `RV_RETURN_VALUE_IGNORED_BAD_PRACTICE` | Use the result: `if (!file.delete()) ...`, or `Files.delete(path)`, which throws |
| `DM_DEFAULT_ENCODING` | Name the charset: `new String(bytes, StandardCharsets.UTF_8)` |
| `NP_NULL_ON_SOME_PATH` and `NP_*` | Check for null before the dereference |
| `MS_MUTABLE_ARRAY`, `MS_MUTABLE_COLLECTION` | Keep the field private, or wrap it: `List.copyOf`, `Collections.unmodifiableList` |
| `DLS_DEAD_LOCAL_STORE` | Remove the assignment, or use the value |
| `SE_BAD_FIELD`, `SE_NO_SERIALVERSIONID` | Add `serialVersionUID`; make the field `transient` or serializable |
| `DM_EXIT` | Throw an exception; let the caller decide |
| `OBL_UNSATISFIED_OBLIGATION` | Use try-with-resources |

Fix the code. If the finding is wrong, add a `<Match>` to the project's
existing exclude filter (`spotbugs.excludeFilter` in `stlInfo --json`) with
an XML comment giving the reason, and list it in the report. If no filter is
set, report the finding instead.
