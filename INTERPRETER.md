# Plan: Data-Driven Generated Parsers

## Status

Proposal. Recovered from a 2026-04-30 session that was lost to chat
archival, then refined to fit the runtime that already exists.
Supersedes earlier "split LrGrammar.kt into many files" sketches —
that approach kept the function-per-rule shape and is no longer the
direction.

## What's already there

`src/commonMain/kotlin/io/github/kotlinmania/lalrpop/runtime/StateMachine.kt`
already contains a generic LR(1) driver, `Parser<Location, Error,
Token, …>`, that walks any `ParserDefinition` implementation. We do
not need to write a new interpreter — the driver loop is solved.

What's missing is a small, **data-driven** `ParserDefinition`
implementation that the future Kotlin emitter can populate cheaply, in
place of today's 38k-line hand-translated `LrGrammar.kt`.

## The problem

`src/commonMain/kotlin/io/github/kotlinmania/lalrpop/parser/LrGrammar.kt`
is 38,626 lines. It is the bootstrapped LALRPOP-grammar parser. Of
those lines:

- **~530 `reduceN` functions.** Each pops N typed values from the
  parse stack, calls an action lambda, pushes the result. Mechanically
  identical except for N and the variants involved.
- **~102 `popVariantN` functions.** Each unwraps `Symbol.VariantK` to
  extract the typed payload. Mechanically identical except for K.
- **A giant `when` dispatcher** mapping reduce-rule IDs to `reduceN`
  calls.
- **~845 action functions.** The only part carrying real grammar
  semantics (the body of each user-defined production).
- **Per-state `when` ladders** in `action()`, `eofAction()`,
  `goto()` — should be packed `ShortArray` lookups.
- **Indentation, imports, comments, scaffolding.**

The first three categories plus the per-state `when` ladders are
templated boilerplate. The fourth carries actual semantics. The rest
is overhead.

## The reshape: data, not code

Reframe the generated parser as:

1. **Packed tables** as `ShortArray` literals (no `when` ladders).
2. **A typed `Production` array** — one entry per LALRPOP rule.
3. **A small `ParserDefinition` impl** (`TableDrivenParserDefinition`)
   that the existing `Parser<...>` driver consumes unchanged.

### Strict typing — non-negotiable

Sydney requires strict typing throughout. Concretely:

- **No `Any` or `Any?` payload escapes.** Every value pushed on the
  parse stack is a typed variant of a per-grammar `Symbol` sealed
  class. The `Symbol` hierarchy stays — it is the type-safety layer
  that makes the whole pipeline checkable, not waste.
- **Reified-generic pop in place of `popVariantN`.** A single
  `inline fun <reified V : Symbol> ParseStack.pop(): V` collapses 102
  hand-written unwrap functions while preserving the same runtime
  check (a `Symbol` sealed-class cast, runtime-verified).
- **`Production<S : Symbol>` carries a typed action lambda.** The
  productions array is `Array<Production<out S>>` (variance keeps the
  array assignable while each row's lambda has its concrete return
  type). No `Any?` returns from action lambdas.
- **No casts that aren't on a sealed class.** `as Symbol.VariantK`
  is fine (sealed-class checked cast). `as Foo` on an unconstrained
  type is not.

### Data layout per grammar

```kotlin
sealed class LrGrammarSymbol {
    data class Variant0(val v: Tok) : LrGrammarSymbol()
    data class Variant1(val v: String) : LrGrammarSymbol()
    // … one per distinct stack type, same as today
}

object LrGrammarTables {
    val ACTION: ShortArray          // packed (state, terminal) → action
    val EOF_ACTION: ShortArray      // (state) → EOF action
    val GOTO: ShortArray            // packed (state, nonterminal) → next state
    val EXPECTED_TOKENS: Array<ShortArray>  // per-state expected-terminal sets

    val PRODUCTIONS: Array<Production<LrGrammarSymbol>>
}

class Production<S>(
    val nonterminalId: Short,
    val rhsLength: Int,
    val action: (ParseStack<S>) -> S,
)
```

The action lambda's return is a concrete `Symbol` variant — it cannot
return `null`, cannot return `Any`, and cannot return some unrelated
type. Type-checked at the call site.

### The runtime shim

```kotlin
class TableDrivenParserDefinition<S, …>(
    private val tables: ParseTables<S>,
    private val tokenIndexer: (Token) -> TokenIndex?,
    private val tokenAsSymbol: (TokenIndex, Token) -> S,
    // … other small bits the existing ParserDefinition contract needs
) : ParserDefinition<…, S, …> {

    override fun action(state: StateIndex, ti: TokenIndex): Action =
        decodeAction(tables.ACTION[state.toInt() * NUM_TERMINALS + ti.toInt()])

    override fun goto(state: StateIndex, nt: NonterminalIndex): StateIndex =
        tables.GOTO[state.toInt() * NUM_NONTERMINALS + nt.toInt()].toStateIndex()

    override fun reduce(reduceIndex, …, states, symbols): ParseResult<…>? {
        val production = tables.PRODUCTIONS[reduceIndex.toInt()]
        // pop production.rhsLength symbols, call production.action, push result.
    }

    // … etc.
}
```

`TableDrivenParserDefinition` is generic over the per-grammar `Symbol`
sealed class. Every grammar instantiates it with its own tables and
its own `Symbol`. The class itself is ported once.

### What collapses

| Before                                           | After                                                  |
|--------------------------------------------------|--------------------------------------------------------|
| ~530 `reduceN(...)` functions                    | one `reduce(...)` in `TableDrivenParserDefinition`     |
| ~102 `popVariantN(...)` functions                | one `inline fun <reified V> pop(): V`                  |
| Giant `when (reduceIndex) { … }` dispatcher      | `PRODUCTIONS[reduceIndex]` array index                 |
| Per-state `when` ladders in `action`/`goto`/etc. | `ACTION` / `GOTO` `ShortArray` lookups                 |
| ~845 free-standing `actionN` functions           | ~845 typed lambda literals inside `PRODUCTIONS` rows   |
| 38,626 lines                                     | ~10,000–15,000 lines (estimated)                       |

Honest estimate: ~10–15k lines, not the 9k cited in the recovered
proposal. The `Symbol` sealed class with ~100 named variants stays
(it's the type-safety load-bearer), and ~845 typed action lambdas
each carry real grammar code. Everything around them collapses into
table lookups + one shared dispatcher.

### Why this is the right shape for Phase 2

The Phase 2 Kotlin-emitting backend was going to mirror the Rust
backend's structure: walk the LR(1) state machine, emit one Kotlin
function per state and per reduction, plus a giant dispatcher. Same
38k-line shape for every grammar.

This proposal makes the codegen *much* smaller. The Kotlin emitter
needs to produce:

- a per-grammar `Symbol` sealed class (one variant per distinct stack
  type — straight from the type analysis already in the front end),
- packed `ShortArray` literals for `ACTION` / `EOF_ACTION` / `GOTO` /
  `EXPECTED_TOKENS`,
- an `Array<Production<…>>` of `(nonterminalId, rhsLen, lambda)` rows,
- a tiny `parse(tokens)` entry point that calls `Parser.drive(...)`
  with a `TableDrivenParserDefinition` instance.

That's a few hundred lines of emitter logic, not a few thousand.

## Five-step migration plan

The plan threads carefully so the Rust-output gate (byte-identical
Rust matching upstream LALRPOP) is never broken — it stays as the
witness that the front-end pipeline (lex / parse / normalize / LR(1)
construction) still works while we build out the new Kotlin path.

### 1. Build `Production`, `ParseStack`, and `TableDrivenParserDefinition`

- Create `src/commonMain/kotlin/io/github/kotlinmania/lalrpop/runtime/Production.kt`
  and `…/runtime/ParseStack.kt` and
  `…/runtime/TableDrivenParserDefinition.kt`.
- `Production<S>` carries `(nonterminalId: Short, rhsLength: Int,
  action: (ParseStack<S>) -> S)`.
- `ParseStack<S>` exposes `pushSymbol`, `popSymbol`,
  `inline fun <reified V : S> pop(): V`. Stack storage is internal
  `ArrayDeque<S>` — `ArrayDeque` is `commonMain` stdlib.
- `TableDrivenParserDefinition` implements the existing
  `ParserDefinition` interface using packed tables + the productions
  array. Action decoding follows the upstream convention (positive =
  shift target, negative = reduce index, zero = error).
- Tests live in `src/commonTest/kotlin/.../runtime/` and exercise a
  toy grammar: `S → A B`, two terminals `a` and `b`, three
  productions. Hand-write the `ACTION` / `GOTO` / `PRODUCTIONS`
  constants. Confirm parse trees come out right.
- **Gate:** toy-grammar tests pass on `macosArm64`. Rust-emit gate
  untouched.

### 2. Hand-translate one production family from `LrGrammar.kt`

- Pick one production family. The recovered proposal named the five
  fallible reduces (productions 205, 206, 386, 446, 447) as the first
  candidate — they exercise error propagation through the
  interpreter, which is the corner case most likely to expose
  interface gaps.
- Express that family as `PRODUCTIONS` rows + lambdas wired into the
  real grammar's tables.
- Verify against the existing `ParserTest.kt` — same inputs, same
  parse trees out.
- Iterate on the runtime shim API until the family round-trips
  cleanly. **Do not** convert all 845 productions by hand; the point
  is to lock the contract before automating.
- **Gate:** the hand-translated production family passes its tests.
  Rust-emit gate untouched.

### 3. Write the Kotlin emitter against the locked contract

- New backend code under `lalrpop.lr1.kotlintarget` (parallel to the
  existing `lalrpop.lr1.codegen`). Per CLAUDE.md / AGENTS.md, no
  single file exceeds ~500–800 lines — the emitter splits naturally
  into:
  - `KotlinSymbolEmit.kt` — per-grammar `Symbol` sealed class.
  - `KotlinTablesEmit.kt` — `ACTION` / `EOF_ACTION` / `GOTO` /
    `EXPECTED_TOKENS` `ShortArray` literals.
  - `KotlinProductionsEmit.kt` — `PRODUCTIONS` array rows.
  - `KotlinActionLambdas.kt` — translates each rule body into a
    typed Kotlin lambda. This is where grammar-author user code (the
    `=> { … }` blocks in `.lalrpop` files) gets translated.
  - `KotlinParserEmit.kt` — top-level driver that orchestrates the
    others and writes through `IndentedWriter`.
- **Gate:** the emitter produces a file that, when compiled with
  `TableDrivenParserDefinition`, parses the same fixture inputs to
  the same trees as step 2's hand-translated version.

### 4. Regenerate `LrGrammar.kt` from `lrgrammar.lalrpop`

- Run the new Kotlin emitter on the bootstrapped grammar.
- Replace the 38k-line hand-ported `LrGrammar.kt` with the generated
  ~10–15k-line file.
- Run the full `commonTest` suite. Every existing parser test must
  pass.
- **Gate:** `./gradlew test` is green on every platform. Rust-emit
  gate also still green.

### 5. Retire the hand-ported file

- Delete the old `LrGrammar.kt` (already replaced in step 4 — this
  step is the cleanup of any stragglers: doc references, port-lint
  headers, dead imports).
- Update `CLAUDE.md` / `AGENTS.md` to note that generated grammars
  use the data-driven shape and `TableDrivenParserDefinition` is the
  runtime entry point.

## Constraints and non-goals

- **No JVM imports.** `TableDrivenParserDefinition` is `commonMain`.
  The reified-pop helper uses `inline fun <reified V : S>`; it does
  not use `java.util.Stack` or any JVM type.
- **Strict typing throughout.** No `Any`/`Any?` payloads. No
  unchecked casts. The reified-generic pop is a sealed-class checked
  cast and is fine.
- **Keep the Rust-output backend untouched.** Step 1 doesn't touch
  `lalrpop.rust` or `lalrpop.lr1.codegen`. Steps 2–4 don't either.
  The Rust emitter remains the byte-parity gate against upstream
  LALRPOP for the entire migration.
- **No optimization-first design.** First make the shim correct.
  Microoptimizations (action arrays vs. method dispatch, inline
  caches, dense vs. sparse `ACTION` packing) come after the parser
  tests pass.
- **No partial migrations live in main.** Either step N is fully
  complete and all tests pass, or it stays on a branch.

## Open questions for Sydney

1. **Action-decoding convention.** The existing `ByteAction` /
   `ShortAction` / `IntAction` types in `StateMachine.kt` use sign +
   magnitude to encode shift / reduce / error. Reuse that same
   encoding in the packed `ACTION` table, or define a fresh one?
   Lean toward reusing `ShortAction` since it's already the public
   contract.

2. **Error recovery.** The current `LrGrammar.kt` has explicit
   error-recovery paths threaded through every state. Need to
   confirm `TableDrivenParserDefinition` preserves the same recovery
   semantics or document where it deviates. Not blocking step 1.

3. **Where does the action-lambda body translation live?** Step 3's
   `KotlinActionLambdas.kt` has to translate user-supplied
   `.lalrpop` action bodies (`=> { … }`) from Rust syntax into
   Kotlin. That's a non-trivial sub-port — small grammars are easy,
   but `lrgrammar.lalrpop` itself uses a wide slice of Rust
   expression syntax in its action bodies. Worth a separate plan
   doc once step 1 is done and the contract is clearer.
