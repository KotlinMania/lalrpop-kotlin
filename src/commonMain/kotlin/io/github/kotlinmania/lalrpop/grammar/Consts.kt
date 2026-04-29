// port-lint: source grammar/consts.rs
package io.github.kotlinmania.lalrpop.grammar

/** Recognized associated type for the token location */
const val LOCATION: String = "Location"

/** Recognized associated type for custom errors */
const val ERROR: String = "Error"

/** The lifetime parameter injected when we do not have an external token enum */
const val INPUT_LIFETIME: String = "'input"

/** The parameter injected when we do not have an external token enum */
const val INPUT_PARAMETER: String = "input"

/** The attribute to request inlining. */
const val INLINE: String = "inline"

/** The attribute to request conditional compilation. */
const val CFG: String = "cfg"

/** The attribute to request LALR. */
const val LALR: String = "LALR"

/** The attribute to request recursive-ascent-style code generation. */
const val TABLE_DRIVEN: String = "table_driven"

/** The attribute to request recursive-ascent-style code generation. */
const val RECURSIVE_ASCENT: String = "recursive_ascent"

/** The attribute to request test-all-style code generation. */
const val TEST_ALL: String = "test_all"
