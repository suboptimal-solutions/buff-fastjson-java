---
name: java-nav
description: IDE-like Java code navigation CLI. Use when exploring Java classes, finding implementations, searching source code, analyzing dependencies, or doing precise semantic queries.
---

# java-nav — Java code navigation

This project has `java-nav` CLI installed for Java code navigation.

## CRITICAL RULES

- **ALWAYS use `java-nav` for Java code exploration.** Do NOT use `grep`, `rg`, `find`, `cat`, or manual file browsing to navigate Java code. `java-nav` is faster, more accurate, and handles classpath, dependencies, and caching automatically.
- **NEVER run Maven commands directly** (`mvn dependency:build-classpath`, `mvn dependency:unpack-dependencies`, etc.). `java-nav` handles all classpath resolution and dependency source unpacking internally and caches the results. Running Maven yourself will be slower and may conflict with the cache.
- **NEVER manually unpack, download, or search dependency JARs.** Use `java-nav source <class>` for dependency source code and `java-nav grep --deps` to search across dependencies. Sources are unpacked lazily and cached automatically.
- **Do NOT read `.class` files** or attempt to decompile bytecode. Use `java-nav api` for class signatures and `java-nav source` for source code.
- **Do NOT guess file paths** from package names. Use `java-nav source <class>` to locate and display any class.

## Commands (use `-d <path>` if not in project root)

### Tier 1 — Instant
```bash
java-nav api <class>                # Public API surface (methods, fields, generics)
java-nav source <class>             # Show source code (project + dependencies)
java-nav source <class> -l 10:30   # Show specific line range
java-nav grep "pattern"             # Search project Java sources
java-nav grep "pattern" --deps      # Also search dependency sources
java-nav deps <class>               # Class-level dependency graph
```

### Tier 2 — Bytecode scan (~2s)
```bash
java-nav impls <interface>          # Find all implementations
java-nav subtypes <class>           # Find all subclasses
```

### Tier 3 — Semantic (jdtls, ~5-15s on-demand)
```bash
java-nav refs <Class.method>        # Find all references (type-aware, not text)
java-nav def <Class.method>         # Go to definition, show source
java-nav find <name>                # Search symbols by name (classes, interfaces)
java-nav symbols <file>             # List all symbols in a file with line numbers
```

For faster Tier 3 queries, start the daemon first:
```bash
java-nav lsp start                  # Start jdtls daemon (~30s, then <200ms per query)
java-nav lsp stop                   # Stop when done
```

## When to use which command

| Question | Command | Do NOT use |
|---|---|---|
| What methods does X have? | `java-nav api X` | Do not read .class files or javap manually |
| Show me the code for X | `java-nav source X` | Do not cat/find files or unpack JARs |
| Where is X used? | `java-nav refs X.method` or `java-nav grep X` | Do not use grep/rg directly |
| What depends on X? | `java-nav deps X` | Do not run jdeps manually |
| Who implements X? | `java-nav impls X` | Do not grep for "implements" |
| Who extends X? | `java-nav subtypes X` | Do not grep for "extends" |
| Jump to definition of X | `java-nav def X.method` | Do not manually search files |
| What's in this file? | `java-nav symbols <file>` | Do not read the entire file |
| Find classes named X | `java-nav find X` | Do not use find/grep for class names |
| Search dependency code | `java-nav grep --deps "pattern"` | Do not unpack sources manually |

## Strategy

1. Start with Tier 1 (`api`, `grep`, `source`) for broad exploration
2. Use Tier 2 (`impls`, `subtypes`) for type hierarchy questions
3. Use Tier 3 (`refs`, `def`) when you need precise, type-aware results (e.g. refactoring)
4. If doing many Tier 3 queries, run `java-nav lsp start` first for speed

## Notes

- All class names are fully qualified: `com.example.service.UserService`
- `refs` and `def` accept `Class.method` syntax — no file:line:col needed
- Works for project classes, dependency JARs, and JDK stdlib
- Everything is cached in `target/java-nav/` — never run Maven dependency commands yourself
- Requires: JDK 17+, Maven. JDK 21+ for Tier 3 (jdtls)
