// port-lint: source <none — Kotlin-side parity corpus index>
//! The codegen-parity corpus.
//!
//! Each entry mirrors a `(.lalrpop, .expected.rs)` pair under
//! `src/commonTest/resources/codegen-parity/`. The resource files are
//! the source of truth for human inspection; the Kotlin string constants
//! below are inlined copies — KMP `commonTest` has no portable filesystem
//! reader, so embedding the oracle text directly is the only way to run a
//! byte-identical comparison from `commonTest`.
//!
//! When you add an entry: update the resource file, paste the same text
//! into the `expected = """..."""` slot below, and refresh the sha3.
package io.github.kotlinmania.lalrpop.codegen

object CodegenParityCorpus {
    val entries: List<CodegenParityEntry> = listOf(
        useSuperInternalTok,
        zeroLengthMatch,
        dynArgument,
        matchAlternatives,
    )

    // ---- use_super_internal_tok --------------------------------------

    private val useSuperInternalTok: CodegenParityEntry
        get() = CodegenParityEntry(
            name = "use_super_internal_tok",
            input = """
                |grammar;
                |
                |use super::util::CaptureMe as Renamed;
                |
                |pub S: Renamed = "b" => Renamed;
                |""".trimMargin(),
            expected = """// auto-generated: "lalrpop 0.23.1"
// sha3: e354066db1fefe8888c14ea3944704fbb2f90e985bc4fbf7aaea57460237c193
use super::util::CaptureMe as Renamed;
#[allow(unused_extern_crates)]
extern crate lalrpop_util as __lalrpop_util;
#[allow(unused_imports)]
use self::__lalrpop_util::state_machine as __state_machine;
#[allow(unused_extern_crates)]
extern crate alloc;

#[rustfmt::skip]
#[allow(explicit_outlives_requirements, non_snake_case, non_camel_case_types, unused_mut, unused_variables, unused_imports, unused_parens, clippy::needless_lifetimes, clippy::type_complexity, clippy::needless_return, clippy::too_many_arguments, clippy::match_single_binding, clippy::clone_on_copy, clippy::unit_arg)]
mod __parse__S {

    use super::super::util::CaptureMe as Renamed;
    #[allow(unused_extern_crates)]
    extern crate lalrpop_util as __lalrpop_util;
    #[allow(unused_imports)]
    use self::__lalrpop_util::state_machine as __state_machine;
    #[allow(unused_extern_crates)]
    extern crate alloc;
    use self::__lalrpop_util::lexer::Token;
    pub struct SParser {
        builder: __lalrpop_util::lexer::MatcherBuilder,
        _priv: (),
    }

    impl Default for SParser { fn default() -> Self { Self::new() } }
    impl SParser {
        pub fn new() -> SParser {
            let __builder = super::__intern_token::new_builder();
            SParser {
                builder: __builder,
                _priv: (),
            }
        }

        #[allow(dead_code)]
        pub fn parse<
            'input,
        >(
            &self,
            input: &'input str,
        ) -> Result<Renamed, __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>
        {
            let _ = self.builder;
            let __ascent = __ascent::SParser::new().parse(
                input,
            );
            let __parse_table = __parse_table::SParser::new().parse(
                input,
            );
            assert_eq!(__ascent, __parse_table);
            return __ascent;
        }
    }
    #[rustfmt::skip]
    mod __ascent {

        #[rustfmt::skip]
        #[allow(explicit_outlives_requirements, non_snake_case, non_camel_case_types, unused_mut, unused_variables, unused_imports, unused_parens, clippy::needless_lifetimes, clippy::type_complexity, clippy::needless_return, clippy::too_many_arguments, clippy::match_single_binding, clippy::clone_on_copy, clippy::unit_arg)]
        mod __parse__S {

            use super::super::super::super::util::CaptureMe as Renamed;
            #[allow(unused_extern_crates)]
            extern crate lalrpop_util as __lalrpop_util;
            #[allow(unused_imports)]
            use self::__lalrpop_util::state_machine as __state_machine;
            #[allow(unused_extern_crates)]
            extern crate alloc;
            use self::__lalrpop_util::lexer::Token;
            pub struct SParser {
                builder: __lalrpop_util::lexer::MatcherBuilder,
                _priv: (),
            }

            impl Default for SParser { fn default() -> Self { Self::new() } }
            impl SParser {
                pub fn new() -> SParser {
                    let __builder = super::super::super::__intern_token::new_builder();
                    SParser {
                        builder: __builder,
                        _priv: (),
                    }
                }

                #[allow(dead_code)]
                pub fn parse<
                    'input,
                >(
                    &self,
                    input: &'input str,
                ) -> Result<Renamed, __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>
                {
                    let mut __tokens = self.builder.matcher(input);
                    let __lookahead = match __tokens.next() {
                        Some(Ok(v)) => Some(v),
                        Some(Err(e)) => return Err(e),
                        None => None,
                    };
                    match __state0(input, &mut __tokens, __lookahead, core::marker::PhantomData::<(&())>)? {
                        (Some(__lookahead), _) => {
                            Err(__lalrpop_util::ParseError::ExtraToken { token: __lookahead })
                        }
                        (None, __Nonterminal::____S((_, __nt, _))) => {
                            Ok(__nt)
                        }
                        _ => unreachable!(),
                    }
                }
            }

            #[allow(dead_code)]
            enum __Nonterminal<>
             {
                S((usize, Renamed, usize)),
                ____S((usize, Renamed, usize)),
            }

            // State 0
            //     AllInputs = []
            //     OptionalInputs = []
            //     FixedInputs = []
            //     WillPushLen = 0
            //     WillPush = []
            //     WillProduce = None
            //
            //     S = (*) "b" ["b", Eof]
            //     __S = (*) S ["b", Eof]
            //
            //   "b" -> S2
            //
            //     S -> S1
            fn __state0<
                'input,
                __TOKENS: Iterator<Item=Result<(usize, Token<'input>, usize),__lalrpop_util::ParseError<usize, Token<'input>, &'static str>>>,
            >(
                input: &'input str,
                __tokens: &mut __TOKENS,
                __lookahead: Option<(usize, Token<'input>, usize)>,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> Result<(Option<(usize, Token<'input>, usize)>, __Nonterminal<>), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>
            {
                let mut __result: (Option<(usize, Token<'input>, usize)>, __Nonterminal<>);
                match __lookahead {
                    Some((__loc1, Token(0, __tok0), __loc2)) => {
                        let __sym0 = (__loc1, (__tok0), __loc2);
                        __result = __state2(input, __tokens, __sym0, core::marker::PhantomData::<(&())>)?;
                    }
                    _ => {
                        #[allow(clippy::needless_raw_string_hashes)]
                        let __expected = alloc::vec![
                            r###""b""###.to_string(),
                        ];
                        return Err(
                            match __lookahead {
                                Some(__token) => {
                                    __lalrpop_util::ParseError::UnrecognizedToken {
                                        token: __token,
                                        expected: __expected,
                                    }
                                }
                                None => {
                                    let __location = Default::default();
                                    __lalrpop_util::ParseError::UnrecognizedEof {
                                        location: __location,
                                        expected: __expected,
                                    }
                                }
                            }
                        )
                    }
                }
                #[allow(clippy::never_loop)]
                loop {
                    let (__lookahead, __nt) = __result;
                    match __nt {
                        __Nonterminal::S(__sym0) => {
                            __result = __state1(input, __tokens, __lookahead, __sym0, core::marker::PhantomData::<(&())>)?;
                        }
                        _ => {
                            return Ok((__lookahead, __nt));
                        }
                    }
                }
            }

            // State 1
            //     AllInputs = [S]
            //     OptionalInputs = []
            //     FixedInputs = [S]
            //     WillPushLen = 0
            //     WillPush = []
            //     WillProduce = Some(__S)
            //
            //     __S = S (*) ["b", Eof]
            //
            //   [Eof] -> __S = S => ActionFn(0);
            //
            fn __state1<
                'input,
                __TOKENS: Iterator<Item=Result<(usize, Token<'input>, usize),__lalrpop_util::ParseError<usize, Token<'input>, &'static str>>>,
            >(
                input: &'input str,
                __tokens: &mut __TOKENS,
                __lookahead: Option<(usize, Token<'input>, usize)>,
                __sym0: (usize, Renamed, usize),
                _: core::marker::PhantomData<(&'input ())>,
            ) -> Result<(Option<(usize, Token<'input>, usize)>, __Nonterminal<>), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>
            {
                let mut __result: (Option<(usize, Token<'input>, usize)>, __Nonterminal<>);
                match __lookahead {
                    None => {
                        let __start = __sym0.0.clone();
                        let __end = __sym0.2.clone();
                        let __nt = super::super::super::__action0::<>(input, __sym0);
                        let __nt = __Nonterminal::____S((
                            __start,
                            __nt,
                            __end,
                        ));
                        __result = (__lookahead, __nt);
                        return Ok(__result);
                    }
                    _ => {
                        #[allow(clippy::needless_raw_string_hashes)]
                        let __expected = alloc::vec![
                        ];
                        return Err(
                            match __lookahead {
                                Some(__token) => {
                                    __lalrpop_util::ParseError::UnrecognizedToken {
                                        token: __token,
                                        expected: __expected,
                                    }
                                }
                                None => {
                                    let __location = __sym0.2;
                                    __lalrpop_util::ParseError::UnrecognizedEof {
                                        location: __location,
                                        expected: __expected,
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // State 2
            //     AllInputs = ["b"]
            //     OptionalInputs = []
            //     FixedInputs = ["b"]
            //     WillPushLen = 0
            //     WillPush = []
            //     WillProduce = Some(S)
            //
            //     S = "b" (*) ["b", Eof]
            //
            //   [Eof] -> S = "b" => ActionFn(1);
            //
            fn __state2<
                'input,
                __TOKENS: Iterator<Item=Result<(usize, Token<'input>, usize),__lalrpop_util::ParseError<usize, Token<'input>, &'static str>>>,
            >(
                input: &'input str,
                __tokens: &mut __TOKENS,
                __sym0: (usize, &'input str, usize),
                _: core::marker::PhantomData<(&'input ())>,
            ) -> Result<(Option<(usize, Token<'input>, usize)>, __Nonterminal<>), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>
            {
                let mut __result: (Option<(usize, Token<'input>, usize)>, __Nonterminal<>);
                let __lookahead = match __tokens.next() {
                    Some(Ok(v)) => Some(v),
                    Some(Err(e)) => return Err(e),
                    None => None,
                };
                match __lookahead {
                    None => {
                        let __start = __sym0.0.clone();
                        let __end = __sym0.2.clone();
                        let __nt = super::super::super::__action1::<>(input, __sym0);
                        let __nt = __Nonterminal::S((
                            __start,
                            __nt,
                            __end,
                        ));
                        __result = (__lookahead, __nt);
                        return Ok(__result);
                    }
                    _ => {
                        #[allow(clippy::needless_raw_string_hashes)]
                        let __expected = alloc::vec![
                        ];
                        return Err(
                            match __lookahead {
                                Some(__token) => {
                                    __lalrpop_util::ParseError::UnrecognizedToken {
                                        token: __token,
                                        expected: __expected,
                                    }
                                }
                                None => {
                                    let __location = __sym0.2;
                                    __lalrpop_util::ParseError::UnrecognizedEof {
                                        location: __location,
                                        expected: __expected,
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
        pub use self::__parse__S::SParser;
    }
    #[rustfmt::skip]
    mod __parse_table {

        #[rustfmt::skip]
        #[allow(explicit_outlives_requirements, non_snake_case, non_camel_case_types, unused_mut, unused_variables, unused_imports, unused_parens, clippy::needless_lifetimes, clippy::type_complexity, clippy::needless_return, clippy::too_many_arguments, clippy::match_single_binding, clippy::clone_on_copy, clippy::unit_arg)]
        mod __parse__S {

            use super::super::super::super::util::CaptureMe as Renamed;
            #[allow(unused_extern_crates)]
            extern crate lalrpop_util as __lalrpop_util;
            #[allow(unused_imports)]
            use self::__lalrpop_util::state_machine as __state_machine;
            #[allow(unused_extern_crates)]
            extern crate alloc;
            use self::__lalrpop_util::lexer::Token;
            #[allow(dead_code)]
            pub(crate) enum __Symbol<'input>
             {
                Variant0(&'input str),
                Variant1(Renamed),
            }
            const __ACTION: &[i8] = &[
                // State 0
                //     S = (*) "b" ["b", Eof]
                //     __S = (*) S ["b", Eof]
                3,  // on "b", goto 2

                // State 1
                //     __S = S (*) ["b", Eof]
                0,  // on "b", error

                // State 2
                //     S = "b" (*) ["b", Eof]
                0,  // on "b", error

            ];
            fn __action(state: i8, integer: usize) -> i8 {
                __ACTION[(state as usize)  + integer]
            }
            const __EOF_ACTION: &[i8] = &[
                // State 0
                0,  // on Eof, error

                // State 1
                -2,  // on Eof, reduce `__S = S => ActionFn(0);`

                // State 2
                -1,  // on Eof, reduce `S = "b" => ActionFn(1);`

            ];
            fn __goto(state: i8, nt: usize) -> i8 {
                match nt {
                    0 => 1,
                    _ => 0,
                }
            }
            #[allow(clippy::needless_raw_string_hashes)]
            const __TERMINAL: &[&str] = &[
                r###""b""###,
            ];
            fn __expected_tokens(__state: i8) -> alloc::vec::Vec<alloc::string::String> {
                __TERMINAL.iter().enumerate().filter_map(|(index, terminal)| {
                    let next_state = __action(__state, index);
                    if next_state == 0 {
                        None
                    } else {
                        Some(alloc::string::ToString::to_string(terminal))
                    }
                }).collect()
            }
            fn __expected_tokens_from_states<
                'input,
            >(
                __states: &[i8],
                _: core::marker::PhantomData<(&'input ())>,
            ) -> alloc::vec::Vec<alloc::string::String>
            {
                __TERMINAL.iter().enumerate().filter_map(|(index, terminal)| {
                    if __accepts(None, __states, Some(index), core::marker::PhantomData::<(&())>) {
                        Some(alloc::string::ToString::to_string(terminal))
                    } else {
                        None
                    }
                }).collect()
            }
            struct __StateMachine<'input>
            where 
            {
                input: &'input str,
                __phantom: core::marker::PhantomData<(&'input ())>,
            }
            impl<'input> __state_machine::ParserDefinition for __StateMachine<'input>
            where 
            {
                type Location = usize;
                type Error = &'static str;
                type Token = Token<'input>;
                type TokenIndex = usize;
                type Symbol = __Symbol<'input>;
                type Success = Renamed;
                type StateIndex = i8;
                type Action = i8;
                type ReduceIndex = i8;
                type NonterminalIndex = usize;

                #[inline]
                fn start_location(&self) -> Self::Location {
                      Default::default()
                }

                #[inline]
                fn start_state(&self) -> Self::StateIndex {
                      0
                }

                #[inline]
                fn token_to_index(&self, token: &Self::Token) -> Option<usize> {
                    __token_to_integer(token, core::marker::PhantomData::<(&())>)
                }

                #[inline]
                fn action(&self, state: i8, integer: usize) -> i8 {
                    __action(state, integer)
                }

                #[inline]
                fn error_action(&self, state: i8) -> i8 {
                    __action(state, 0)
                }

                #[inline]
                fn eof_action(&self, state: i8) -> i8 {
                    __EOF_ACTION[state as usize]
                }

                #[inline]
                fn goto(&self, state: i8, nt: usize) -> i8 {
                    __goto(state, nt)
                }

                fn token_to_symbol(&self, token_index: usize, token: Self::Token) -> Self::Symbol {
                    __token_to_symbol(token_index, token, core::marker::PhantomData::<(&())>)
                }

                fn expected_tokens(&self, state: i8) -> alloc::vec::Vec<alloc::string::String> {
                    __expected_tokens(state)
                }

                fn expected_tokens_from_states(&self, states: &[i8]) -> alloc::vec::Vec<alloc::string::String> {
                    __expected_tokens_from_states(states, core::marker::PhantomData::<(&())>)
                }

                #[inline]
                fn uses_error_recovery(&self) -> bool {
                    false
                }

                #[inline]
                fn error_recovery_symbol(
                    &self,
                    recovery: __state_machine::ErrorRecovery<Self>,
                ) -> Self::Symbol {
                    panic!("error recovery not enabled for this grammar")
                }

                fn reduce(
                    &mut self,
                    action: i8,
                    start_location: Option<&Self::Location>,
                    states: &mut alloc::vec::Vec<i8>,
                    symbols: &mut alloc::vec::Vec<__state_machine::SymbolTriple<Self>>,
                ) -> Option<__state_machine::ParseResult<Self>> {
                    __reduce(
                        self.input,
                        action,
                        start_location,
                        states,
                        symbols,
                        core::marker::PhantomData::<(&())>,
                    )
                }

                fn simulate_reduce(&self, action: i8) -> __state_machine::SimulatedReduce<Self> {
                    __simulate_reduce(action, core::marker::PhantomData::<(&())>)
                }
            }
            fn __token_to_integer<
                'input,
            >(
                __token: &Token<'input>,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> Option<usize>
            {
                #[warn(unused_variables)]
                match __token {
                    Token(0, _) if true => Some(0),
                    _ => None,
                }
            }
            fn __token_to_symbol<
                'input,
            >(
                __token_index: usize,
                __token: Token<'input>,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> __Symbol<'input>
            {
                #[allow(clippy::manual_range_patterns)]match __token_index {
                    0 => match __token {
                        Token(0, __tok0) if true => __Symbol::Variant0(__tok0),
                        _ => unreachable!(),
                    },
                    _ => unreachable!(),
                }
            }
            fn __simulate_reduce<
                'input,
            >(
                __reduce_index: i8,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> __state_machine::SimulatedReduce<__StateMachine<'input>>
            {
                match __reduce_index {
                    // simulate S = "b" => ActionFn(1);
                    0 => {
                        __state_machine::SimulatedReduce::Reduce {
                            states_to_pop: 1,
                            nonterminal_produced: 0,
                        }
                    }
                    // simulate __S = S => ActionFn(0);
                    1 => __state_machine::SimulatedReduce::Accept,
                    _ => panic!("invalid reduction index {__reduce_index}")
                }
            }
            pub struct SParser {
                builder: __lalrpop_util::lexer::MatcherBuilder,
                _priv: (),
            }

            impl Default for SParser { fn default() -> Self { Self::new() } }
            impl SParser {
                pub fn new() -> SParser {
                    let __builder = super::super::super::__intern_token::new_builder();
                    SParser {
                        builder: __builder,
                        _priv: (),
                    }
                }

                #[allow(dead_code)]
                pub fn parse<
                    'input,
                >(
                    &self,
                    input: &'input str,
                ) -> Result<Renamed, __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>
                {
                    let mut __tokens = self.builder.matcher(input);
                    __state_machine::Parser::drive(
                        __StateMachine {
                            input,
                            __phantom: core::marker::PhantomData::<(&())>,
                        },
                        __tokens,
                    )
                }
            }
            fn __accepts<
                'input,
            >(
                __error_state: Option<i8>,
                __states: &[i8],
                __opt_integer: Option<usize>,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> bool
            {
                let mut __states = __states.to_vec();
                __states.extend(__error_state);
                loop {
                    let mut __states_len = __states.len();
                    let __top = __states[__states_len - 1];
                    let __action = match __opt_integer {
                        None => __EOF_ACTION[__top as usize],
                        Some(__integer) => __action(__top, __integer),
                    };
                    if __action == 0 { return false; }
                    if __action > 0 { return true; }
                    let (__to_pop, __nt) = match __simulate_reduce(-(__action + 1), core::marker::PhantomData::<(&())>) {
                        __state_machine::SimulatedReduce::Reduce {
                            states_to_pop, nonterminal_produced
                        } => (states_to_pop, nonterminal_produced),
                        __state_machine::SimulatedReduce::Accept => return true,
                    };
                    __states_len -= __to_pop;
                    __states.truncate(__states_len);
                    let __top = __states[__states_len - 1];
                    let __next_state = __goto(__top, __nt);
                    __states.push(__next_state);
                }
            }
            fn __reduce<
                'input,
            >(
                input: &'input str,
                __action: i8,
                __lookahead_start: Option<&usize>,
                __states: &mut alloc::vec::Vec<i8>,
                __symbols: &mut alloc::vec::Vec<(usize,__Symbol<'input>,usize)>,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> Option<Result<Renamed,__lalrpop_util::ParseError<usize, Token<'input>, &'static str>>>
            {
                let (__pop_states, __nonterminal) = match __action {
                    0 => {
                        __reduce0(input, __lookahead_start, __symbols, core::marker::PhantomData::<(&())>)
                    }
                    1 => {
                        // __S = S => ActionFn(0);
                        let __sym0 = __pop_Variant1(__symbols);
                        let __start = __sym0.0.clone();
                        let __end = __sym0.2.clone();
                        let __nt = super::super::super::__action0::<>(input, __sym0);
                        return Some(Ok(__nt));
                    }
                    _ => panic!("invalid action code {__action}")
                };
                let __states_len = __states.len();
                __states.truncate(__states_len - __pop_states);
                let __state = *__states.last().unwrap();
                let __next_state = __goto(__state, __nonterminal);
                __states.push(__next_state);
                None
            }
            #[inline(never)]
            fn __symbol_type_mismatch() -> ! {
                panic!("symbol type mismatch")
            }
            fn __pop_Variant1<
              'input,
            >(
                __symbols: &mut alloc::vec::Vec<(usize,__Symbol<'input>,usize)>
            ) -> (usize, Renamed, usize)
             {
                match __symbols.pop() {
                    Some((__l, __Symbol::Variant1(__v), __r)) => (__l, __v, __r),
                    _ => __symbol_type_mismatch()
                }
            }
            fn __pop_Variant0<
              'input,
            >(
                __symbols: &mut alloc::vec::Vec<(usize,__Symbol<'input>,usize)>
            ) -> (usize, &'input str, usize)
             {
                match __symbols.pop() {
                    Some((__l, __Symbol::Variant0(__v), __r)) => (__l, __v, __r),
                    _ => __symbol_type_mismatch()
                }
            }
            fn __reduce0<
                'input,
            >(
                input: &'input str,
                __lookahead_start: Option<&usize>,
                __symbols: &mut alloc::vec::Vec<(usize,__Symbol<'input>,usize)>,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> (usize, usize)
            {
                // S = "b" => ActionFn(1);
                let __sym0 = __pop_Variant0(__symbols);
                let __start = __sym0.0.clone();
                let __end = __sym0.2.clone();
                let __nt = super::super::super::__action1::<>(input, __sym0);
                __symbols.push((__start, __Symbol::Variant1(__nt), __end));
                (1, 0)
            }
        }
        pub use self::__parse__S::SParser;
    }
}
#[allow(unused_imports)]
pub use self::__parse__S::SParser;
#[rustfmt::skip]
mod __intern_token {
    #![allow(unused_imports)]
    use super::super::util::CaptureMe as Renamed;
    #[allow(unused_extern_crates)]
    extern crate lalrpop_util as __lalrpop_util;
    #[allow(unused_imports)]
    use self::__lalrpop_util::state_machine as __state_machine;
    #[allow(unused_extern_crates)]
    extern crate alloc;
    pub fn new_builder() -> __lalrpop_util::lexer::MatcherBuilder {
        let __strs: &[(&str, bool)] = &[
            ("b", false),
            (r"\s+", true),
        ];
        __lalrpop_util::lexer::MatcherBuilder::new(__strs.iter().copied()).unwrap()
    }
}
pub(crate) use self::__lalrpop_util::lexer::Token;

#[allow(unused_variables)]
#[allow(clippy::too_many_arguments, clippy::needless_lifetimes, clippy::just_underscores_and_digits)]
fn __action0<
    'input,
>(
    input: &'input str,
    (_, __0, _): (usize, Renamed, usize),
) -> Renamed
{
    __0
}

#[allow(unused_variables)]
#[allow(clippy::too_many_arguments, clippy::needless_lifetimes, clippy::just_underscores_and_digits)]
fn __action1<
    'input,
>(
    input: &'input str,
    (_, __0, _): (usize, &'input str, usize),
) -> Renamed
{
    Renamed
}

#[allow(clippy::type_complexity, dead_code)]
pub trait __ToTriple<'input, >
{
    fn to_triple(self) -> Result<(usize,Token<'input>,usize), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>;
}

impl<'input, > __ToTriple<'input, > for (usize, Token<'input>, usize)
{
    fn to_triple(self) -> Result<(usize,Token<'input>,usize), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>> {
        Ok(self)
    }
}
impl<'input, > __ToTriple<'input, > for Result<(usize, Token<'input>, usize), &'static str>
{
    fn to_triple(self) -> Result<(usize,Token<'input>,usize), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>> {
        self.map_err(|error| __lalrpop_util::ParseError::User { error })
    }
}
""",
            sha3 = "e354066db1fefe8888c14ea3944704fbb2f90e985bc4fbf7aaea57460237c193",
            status = ParityStatus.Matching,
        )

    // ---- zero_length_match -------------------------------------------

    private val zeroLengthMatch: CodegenParityEntry
        get() = CodegenParityEntry(
            name = "zero_length_match",
            input = """
                |grammar;
                |
                |pub A: String = {
                |${'\t'}"A" => "A".to_string()
                |};
                |
                |match {
                |${'\t'}r"\s*" => {}
                |} else {
                |${'\t'}_
                |}
                |""".trimMargin(),
            expected = """// auto-generated: "lalrpop 0.23.1"
// sha3: aee79bb8564374c7b65a2f4deaa3144d34f9be989b519a165553a563d5619215
#[allow(unused_extern_crates)]
extern crate lalrpop_util as __lalrpop_util;
#[allow(unused_imports)]
use self::__lalrpop_util::state_machine as __state_machine;
#[allow(unused_extern_crates)]
extern crate alloc;

#[rustfmt::skip]
#[allow(explicit_outlives_requirements, non_snake_case, non_camel_case_types, unused_mut, unused_variables, unused_imports, unused_parens, clippy::needless_lifetimes, clippy::type_complexity, clippy::needless_return, clippy::too_many_arguments, clippy::match_single_binding, clippy::clone_on_copy, clippy::unit_arg)]
mod __parse__A {

    #[allow(unused_extern_crates)]
    extern crate lalrpop_util as __lalrpop_util;
    #[allow(unused_imports)]
    use self::__lalrpop_util::state_machine as __state_machine;
    #[allow(unused_extern_crates)]
    extern crate alloc;
    use self::__lalrpop_util::lexer::Token;
    pub struct AParser {
        builder: __lalrpop_util::lexer::MatcherBuilder,
        _priv: (),
    }

    impl Default for AParser { fn default() -> Self { Self::new() } }
    impl AParser {
        pub fn new() -> AParser {
            let __builder = super::__intern_token::new_builder();
            AParser {
                builder: __builder,
                _priv: (),
            }
        }

        #[allow(dead_code)]
        pub fn parse<
            'input,
        >(
            &self,
            input: &'input str,
        ) -> Result<String, __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>
        {
            let _ = self.builder;
            let __ascent = __ascent::AParser::new().parse(
                input,
            );
            let __parse_table = __parse_table::AParser::new().parse(
                input,
            );
            assert_eq!(__ascent, __parse_table);
            return __ascent;
        }
    }
    #[rustfmt::skip]
    mod __ascent {

        #[rustfmt::skip]
        #[allow(explicit_outlives_requirements, non_snake_case, non_camel_case_types, unused_mut, unused_variables, unused_imports, unused_parens, clippy::needless_lifetimes, clippy::type_complexity, clippy::needless_return, clippy::too_many_arguments, clippy::match_single_binding, clippy::clone_on_copy, clippy::unit_arg)]
        mod __parse__A {

            #[allow(unused_extern_crates)]
            extern crate lalrpop_util as __lalrpop_util;
            #[allow(unused_imports)]
            use self::__lalrpop_util::state_machine as __state_machine;
            #[allow(unused_extern_crates)]
            extern crate alloc;
            use self::__lalrpop_util::lexer::Token;
            pub struct AParser {
                builder: __lalrpop_util::lexer::MatcherBuilder,
                _priv: (),
            }

            impl Default for AParser { fn default() -> Self { Self::new() } }
            impl AParser {
                pub fn new() -> AParser {
                    let __builder = super::super::super::__intern_token::new_builder();
                    AParser {
                        builder: __builder,
                        _priv: (),
                    }
                }

                #[allow(dead_code)]
                pub fn parse<
                    'input,
                >(
                    &self,
                    input: &'input str,
                ) -> Result<String, __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>
                {
                    let mut __tokens = self.builder.matcher(input);
                    let __lookahead = match __tokens.next() {
                        Some(Ok(v)) => Some(v),
                        Some(Err(e)) => return Err(e),
                        None => None,
                    };
                    match __state0(input, &mut __tokens, __lookahead, core::marker::PhantomData::<(&())>)? {
                        (Some(__lookahead), _) => {
                            Err(__lalrpop_util::ParseError::ExtraToken { token: __lookahead })
                        }
                        (None, __Nonterminal::____A((_, __nt, _))) => {
                            Ok(__nt)
                        }
                        _ => unreachable!(),
                    }
                }
            }

            #[allow(dead_code)]
            enum __Nonterminal<>
             {
                A((usize, String, usize)),
                ____A((usize, String, usize)),
            }

            // State 0
            //     AllInputs = []
            //     OptionalInputs = []
            //     FixedInputs = []
            //     WillPushLen = 0
            //     WillPush = []
            //     WillProduce = None
            //
            //     A = (*) "A" ["A", Eof]
            //     __A = (*) A ["A", Eof]
            //
            //   "A" -> S2
            //
            //     A -> S1
            fn __state0<
                'input,
                __TOKENS: Iterator<Item=Result<(usize, Token<'input>, usize),__lalrpop_util::ParseError<usize, Token<'input>, &'static str>>>,
            >(
                input: &'input str,
                __tokens: &mut __TOKENS,
                __lookahead: Option<(usize, Token<'input>, usize)>,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> Result<(Option<(usize, Token<'input>, usize)>, __Nonterminal<>), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>
            {
                let mut __result: (Option<(usize, Token<'input>, usize)>, __Nonterminal<>);
                match __lookahead {
                    Some((__loc1, Token(0, __tok0), __loc2)) => {
                        let __sym0 = (__loc1, (__tok0), __loc2);
                        __result = __state2(input, __tokens, __sym0, core::marker::PhantomData::<(&())>)?;
                    }
                    _ => {
                        #[allow(clippy::needless_raw_string_hashes)]
                        let __expected = alloc::vec![
                            r###""A""###.to_string(),
                        ];
                        return Err(
                            match __lookahead {
                                Some(__token) => {
                                    __lalrpop_util::ParseError::UnrecognizedToken {
                                        token: __token,
                                        expected: __expected,
                                    }
                                }
                                None => {
                                    let __location = Default::default();
                                    __lalrpop_util::ParseError::UnrecognizedEof {
                                        location: __location,
                                        expected: __expected,
                                    }
                                }
                            }
                        )
                    }
                }
                #[allow(clippy::never_loop)]
                loop {
                    let (__lookahead, __nt) = __result;
                    match __nt {
                        __Nonterminal::A(__sym0) => {
                            __result = __state1(input, __tokens, __lookahead, __sym0, core::marker::PhantomData::<(&())>)?;
                        }
                        _ => {
                            return Ok((__lookahead, __nt));
                        }
                    }
                }
            }

            // State 1
            //     AllInputs = [A]
            //     OptionalInputs = []
            //     FixedInputs = [A]
            //     WillPushLen = 0
            //     WillPush = []
            //     WillProduce = Some(__A)
            //
            //     __A = A (*) ["A", Eof]
            //
            //   [Eof] -> __A = A => ActionFn(0);
            //
            fn __state1<
                'input,
                __TOKENS: Iterator<Item=Result<(usize, Token<'input>, usize),__lalrpop_util::ParseError<usize, Token<'input>, &'static str>>>,
            >(
                input: &'input str,
                __tokens: &mut __TOKENS,
                __lookahead: Option<(usize, Token<'input>, usize)>,
                __sym0: (usize, String, usize),
                _: core::marker::PhantomData<(&'input ())>,
            ) -> Result<(Option<(usize, Token<'input>, usize)>, __Nonterminal<>), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>
            {
                let mut __result: (Option<(usize, Token<'input>, usize)>, __Nonterminal<>);
                match __lookahead {
                    None => {
                        let __start = __sym0.0.clone();
                        let __end = __sym0.2.clone();
                        let __nt = super::super::super::__action0::<>(input, __sym0);
                        let __nt = __Nonterminal::____A((
                            __start,
                            __nt,
                            __end,
                        ));
                        __result = (__lookahead, __nt);
                        return Ok(__result);
                    }
                    _ => {
                        #[allow(clippy::needless_raw_string_hashes)]
                        let __expected = alloc::vec![
                        ];
                        return Err(
                            match __lookahead {
                                Some(__token) => {
                                    __lalrpop_util::ParseError::UnrecognizedToken {
                                        token: __token,
                                        expected: __expected,
                                    }
                                }
                                None => {
                                    let __location = __sym0.2;
                                    __lalrpop_util::ParseError::UnrecognizedEof {
                                        location: __location,
                                        expected: __expected,
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // State 2
            //     AllInputs = ["A"]
            //     OptionalInputs = []
            //     FixedInputs = ["A"]
            //     WillPushLen = 0
            //     WillPush = []
            //     WillProduce = Some(A)
            //
            //     A = "A" (*) ["A", Eof]
            //
            //   [Eof] -> A = "A" => ActionFn(1);
            //
            fn __state2<
                'input,
                __TOKENS: Iterator<Item=Result<(usize, Token<'input>, usize),__lalrpop_util::ParseError<usize, Token<'input>, &'static str>>>,
            >(
                input: &'input str,
                __tokens: &mut __TOKENS,
                __sym0: (usize, &'input str, usize),
                _: core::marker::PhantomData<(&'input ())>,
            ) -> Result<(Option<(usize, Token<'input>, usize)>, __Nonterminal<>), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>
            {
                let mut __result: (Option<(usize, Token<'input>, usize)>, __Nonterminal<>);
                let __lookahead = match __tokens.next() {
                    Some(Ok(v)) => Some(v),
                    Some(Err(e)) => return Err(e),
                    None => None,
                };
                match __lookahead {
                    None => {
                        let __start = __sym0.0.clone();
                        let __end = __sym0.2.clone();
                        let __nt = super::super::super::__action1::<>(input, __sym0);
                        let __nt = __Nonterminal::A((
                            __start,
                            __nt,
                            __end,
                        ));
                        __result = (__lookahead, __nt);
                        return Ok(__result);
                    }
                    _ => {
                        #[allow(clippy::needless_raw_string_hashes)]
                        let __expected = alloc::vec![
                        ];
                        return Err(
                            match __lookahead {
                                Some(__token) => {
                                    __lalrpop_util::ParseError::UnrecognizedToken {
                                        token: __token,
                                        expected: __expected,
                                    }
                                }
                                None => {
                                    let __location = __sym0.2;
                                    __lalrpop_util::ParseError::UnrecognizedEof {
                                        location: __location,
                                        expected: __expected,
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
        pub use self::__parse__A::AParser;
    }
    #[rustfmt::skip]
    mod __parse_table {

        #[rustfmt::skip]
        #[allow(explicit_outlives_requirements, non_snake_case, non_camel_case_types, unused_mut, unused_variables, unused_imports, unused_parens, clippy::needless_lifetimes, clippy::type_complexity, clippy::needless_return, clippy::too_many_arguments, clippy::match_single_binding, clippy::clone_on_copy, clippy::unit_arg)]
        mod __parse__A {

            #[allow(unused_extern_crates)]
            extern crate lalrpop_util as __lalrpop_util;
            #[allow(unused_imports)]
            use self::__lalrpop_util::state_machine as __state_machine;
            #[allow(unused_extern_crates)]
            extern crate alloc;
            use self::__lalrpop_util::lexer::Token;
            #[allow(dead_code)]
            pub(crate) enum __Symbol<'input>
             {
                Variant0(&'input str),
                Variant1(String),
            }
            const __ACTION: &[i8] = &[
                // State 0
                //     A = (*) "A" ["A", Eof]
                //     __A = (*) A ["A", Eof]
                3,  // on "A", goto 2

                // State 1
                //     __A = A (*) ["A", Eof]
                0,  // on "A", error

                // State 2
                //     A = "A" (*) ["A", Eof]
                0,  // on "A", error

            ];
            fn __action(state: i8, integer: usize) -> i8 {
                __ACTION[(state as usize)  + integer]
            }
            const __EOF_ACTION: &[i8] = &[
                // State 0
                0,  // on Eof, error

                // State 1
                -2,  // on Eof, reduce `__A = A => ActionFn(0);`

                // State 2
                -1,  // on Eof, reduce `A = "A" => ActionFn(1);`

            ];
            fn __goto(state: i8, nt: usize) -> i8 {
                match nt {
                    0 => 1,
                    _ => 0,
                }
            }
            #[allow(clippy::needless_raw_string_hashes)]
            const __TERMINAL: &[&str] = &[
                r###""A""###,
            ];
            fn __expected_tokens(__state: i8) -> alloc::vec::Vec<alloc::string::String> {
                __TERMINAL.iter().enumerate().filter_map(|(index, terminal)| {
                    let next_state = __action(__state, index);
                    if next_state == 0 {
                        None
                    } else {
                        Some(alloc::string::ToString::to_string(terminal))
                    }
                }).collect()
            }
            fn __expected_tokens_from_states<
                'input,
            >(
                __states: &[i8],
                _: core::marker::PhantomData<(&'input ())>,
            ) -> alloc::vec::Vec<alloc::string::String>
            {
                __TERMINAL.iter().enumerate().filter_map(|(index, terminal)| {
                    if __accepts(None, __states, Some(index), core::marker::PhantomData::<(&())>) {
                        Some(alloc::string::ToString::to_string(terminal))
                    } else {
                        None
                    }
                }).collect()
            }
            struct __StateMachine<'input>
            where 
            {
                input: &'input str,
                __phantom: core::marker::PhantomData<(&'input ())>,
            }
            impl<'input> __state_machine::ParserDefinition for __StateMachine<'input>
            where 
            {
                type Location = usize;
                type Error = &'static str;
                type Token = Token<'input>;
                type TokenIndex = usize;
                type Symbol = __Symbol<'input>;
                type Success = String;
                type StateIndex = i8;
                type Action = i8;
                type ReduceIndex = i8;
                type NonterminalIndex = usize;

                #[inline]
                fn start_location(&self) -> Self::Location {
                      Default::default()
                }

                #[inline]
                fn start_state(&self) -> Self::StateIndex {
                      0
                }

                #[inline]
                fn token_to_index(&self, token: &Self::Token) -> Option<usize> {
                    __token_to_integer(token, core::marker::PhantomData::<(&())>)
                }

                #[inline]
                fn action(&self, state: i8, integer: usize) -> i8 {
                    __action(state, integer)
                }

                #[inline]
                fn error_action(&self, state: i8) -> i8 {
                    __action(state, 0)
                }

                #[inline]
                fn eof_action(&self, state: i8) -> i8 {
                    __EOF_ACTION[state as usize]
                }

                #[inline]
                fn goto(&self, state: i8, nt: usize) -> i8 {
                    __goto(state, nt)
                }

                fn token_to_symbol(&self, token_index: usize, token: Self::Token) -> Self::Symbol {
                    __token_to_symbol(token_index, token, core::marker::PhantomData::<(&())>)
                }

                fn expected_tokens(&self, state: i8) -> alloc::vec::Vec<alloc::string::String> {
                    __expected_tokens(state)
                }

                fn expected_tokens_from_states(&self, states: &[i8]) -> alloc::vec::Vec<alloc::string::String> {
                    __expected_tokens_from_states(states, core::marker::PhantomData::<(&())>)
                }

                #[inline]
                fn uses_error_recovery(&self) -> bool {
                    false
                }

                #[inline]
                fn error_recovery_symbol(
                    &self,
                    recovery: __state_machine::ErrorRecovery<Self>,
                ) -> Self::Symbol {
                    panic!("error recovery not enabled for this grammar")
                }

                fn reduce(
                    &mut self,
                    action: i8,
                    start_location: Option<&Self::Location>,
                    states: &mut alloc::vec::Vec<i8>,
                    symbols: &mut alloc::vec::Vec<__state_machine::SymbolTriple<Self>>,
                ) -> Option<__state_machine::ParseResult<Self>> {
                    __reduce(
                        self.input,
                        action,
                        start_location,
                        states,
                        symbols,
                        core::marker::PhantomData::<(&())>,
                    )
                }

                fn simulate_reduce(&self, action: i8) -> __state_machine::SimulatedReduce<Self> {
                    __simulate_reduce(action, core::marker::PhantomData::<(&())>)
                }
            }
            fn __token_to_integer<
                'input,
            >(
                __token: &Token<'input>,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> Option<usize>
            {
                #[warn(unused_variables)]
                match __token {
                    Token(0, _) if true => Some(0),
                    _ => None,
                }
            }
            fn __token_to_symbol<
                'input,
            >(
                __token_index: usize,
                __token: Token<'input>,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> __Symbol<'input>
            {
                #[allow(clippy::manual_range_patterns)]match __token_index {
                    0 => match __token {
                        Token(0, __tok0) if true => __Symbol::Variant0(__tok0),
                        _ => unreachable!(),
                    },
                    _ => unreachable!(),
                }
            }
            fn __simulate_reduce<
                'input,
            >(
                __reduce_index: i8,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> __state_machine::SimulatedReduce<__StateMachine<'input>>
            {
                match __reduce_index {
                    // simulate A = "A" => ActionFn(1);
                    0 => {
                        __state_machine::SimulatedReduce::Reduce {
                            states_to_pop: 1,
                            nonterminal_produced: 0,
                        }
                    }
                    // simulate __A = A => ActionFn(0);
                    1 => __state_machine::SimulatedReduce::Accept,
                    _ => panic!("invalid reduction index {__reduce_index}")
                }
            }
            pub struct AParser {
                builder: __lalrpop_util::lexer::MatcherBuilder,
                _priv: (),
            }

            impl Default for AParser { fn default() -> Self { Self::new() } }
            impl AParser {
                pub fn new() -> AParser {
                    let __builder = super::super::super::__intern_token::new_builder();
                    AParser {
                        builder: __builder,
                        _priv: (),
                    }
                }

                #[allow(dead_code)]
                pub fn parse<
                    'input,
                >(
                    &self,
                    input: &'input str,
                ) -> Result<String, __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>
                {
                    let mut __tokens = self.builder.matcher(input);
                    __state_machine::Parser::drive(
                        __StateMachine {
                            input,
                            __phantom: core::marker::PhantomData::<(&())>,
                        },
                        __tokens,
                    )
                }
            }
            fn __accepts<
                'input,
            >(
                __error_state: Option<i8>,
                __states: &[i8],
                __opt_integer: Option<usize>,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> bool
            {
                let mut __states = __states.to_vec();
                __states.extend(__error_state);
                loop {
                    let mut __states_len = __states.len();
                    let __top = __states[__states_len - 1];
                    let __action = match __opt_integer {
                        None => __EOF_ACTION[__top as usize],
                        Some(__integer) => __action(__top, __integer),
                    };
                    if __action == 0 { return false; }
                    if __action > 0 { return true; }
                    let (__to_pop, __nt) = match __simulate_reduce(-(__action + 1), core::marker::PhantomData::<(&())>) {
                        __state_machine::SimulatedReduce::Reduce {
                            states_to_pop, nonterminal_produced
                        } => (states_to_pop, nonterminal_produced),
                        __state_machine::SimulatedReduce::Accept => return true,
                    };
                    __states_len -= __to_pop;
                    __states.truncate(__states_len);
                    let __top = __states[__states_len - 1];
                    let __next_state = __goto(__top, __nt);
                    __states.push(__next_state);
                }
            }
            fn __reduce<
                'input,
            >(
                input: &'input str,
                __action: i8,
                __lookahead_start: Option<&usize>,
                __states: &mut alloc::vec::Vec<i8>,
                __symbols: &mut alloc::vec::Vec<(usize,__Symbol<'input>,usize)>,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> Option<Result<String,__lalrpop_util::ParseError<usize, Token<'input>, &'static str>>>
            {
                let (__pop_states, __nonterminal) = match __action {
                    0 => {
                        __reduce0(input, __lookahead_start, __symbols, core::marker::PhantomData::<(&())>)
                    }
                    1 => {
                        // __A = A => ActionFn(0);
                        let __sym0 = __pop_Variant1(__symbols);
                        let __start = __sym0.0.clone();
                        let __end = __sym0.2.clone();
                        let __nt = super::super::super::__action0::<>(input, __sym0);
                        return Some(Ok(__nt));
                    }
                    _ => panic!("invalid action code {__action}")
                };
                let __states_len = __states.len();
                __states.truncate(__states_len - __pop_states);
                let __state = *__states.last().unwrap();
                let __next_state = __goto(__state, __nonterminal);
                __states.push(__next_state);
                None
            }
            #[inline(never)]
            fn __symbol_type_mismatch() -> ! {
                panic!("symbol type mismatch")
            }
            fn __pop_Variant1<
              'input,
            >(
                __symbols: &mut alloc::vec::Vec<(usize,__Symbol<'input>,usize)>
            ) -> (usize, String, usize)
             {
                match __symbols.pop() {
                    Some((__l, __Symbol::Variant1(__v), __r)) => (__l, __v, __r),
                    _ => __symbol_type_mismatch()
                }
            }
            fn __pop_Variant0<
              'input,
            >(
                __symbols: &mut alloc::vec::Vec<(usize,__Symbol<'input>,usize)>
            ) -> (usize, &'input str, usize)
             {
                match __symbols.pop() {
                    Some((__l, __Symbol::Variant0(__v), __r)) => (__l, __v, __r),
                    _ => __symbol_type_mismatch()
                }
            }
            fn __reduce0<
                'input,
            >(
                input: &'input str,
                __lookahead_start: Option<&usize>,
                __symbols: &mut alloc::vec::Vec<(usize,__Symbol<'input>,usize)>,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> (usize, usize)
            {
                // A = "A" => ActionFn(1);
                let __sym0 = __pop_Variant0(__symbols);
                let __start = __sym0.0.clone();
                let __end = __sym0.2.clone();
                let __nt = super::super::super::__action1::<>(input, __sym0);
                __symbols.push((__start, __Symbol::Variant1(__nt), __end));
                (1, 0)
            }
        }
        pub use self::__parse__A::AParser;
    }
}
#[allow(unused_imports)]
pub use self::__parse__A::AParser;
#[rustfmt::skip]
mod __intern_token {
    #![allow(unused_imports)]
    #[allow(unused_extern_crates)]
    extern crate lalrpop_util as __lalrpop_util;
    #[allow(unused_imports)]
    use self::__lalrpop_util::state_machine as __state_machine;
    #[allow(unused_extern_crates)]
    extern crate alloc;
    pub fn new_builder() -> __lalrpop_util::lexer::MatcherBuilder {
        let __strs: &[(&str, bool)] = &[
            ("A", false),
            ("[\t-\r \u{85}\u{a0}\u{1680}\u{2000}-\u{200a}\u{2028}\u{2029}\u{202f}\u{205f}\u{3000}]*", true),
        ];
        __lalrpop_util::lexer::MatcherBuilder::new(__strs.iter().copied()).unwrap()
    }
}
pub(crate) use self::__lalrpop_util::lexer::Token;

#[allow(unused_variables)]
#[allow(clippy::too_many_arguments, clippy::needless_lifetimes, clippy::just_underscores_and_digits)]
fn __action0<
    'input,
>(
    input: &'input str,
    (_, __0, _): (usize, String, usize),
) -> String
{
    __0
}

#[allow(unused_variables)]
#[allow(clippy::too_many_arguments, clippy::needless_lifetimes, clippy::just_underscores_and_digits)]
fn __action1<
    'input,
>(
    input: &'input str,
    (_, __0, _): (usize, &'input str, usize),
) -> String
{
    "A".to_string()
}

#[allow(clippy::type_complexity, dead_code)]
pub trait __ToTriple<'input, >
{
    fn to_triple(self) -> Result<(usize,Token<'input>,usize), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>;
}

impl<'input, > __ToTriple<'input, > for (usize, Token<'input>, usize)
{
    fn to_triple(self) -> Result<(usize,Token<'input>,usize), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>> {
        Ok(self)
    }
}
impl<'input, > __ToTriple<'input, > for Result<(usize, Token<'input>, usize), &'static str>
{
    fn to_triple(self) -> Result<(usize,Token<'input>,usize), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>> {
        self.map_err(|error| __lalrpop_util::ParseError::User { error })
    }
}
""",
            sha3 = "aee79bb8564374c7b65a2f4deaa3144d34f9be989b519a165553a563d5619215",
            status = ParityStatus.Matching,
        )

    // ---- dyn_argument ------------------------------------------------

    private val dynArgument: CodegenParityEntry
        get() = CodegenParityEntry(
            name = "dyn_argument",
            input = """
                |grammar(x: &dyn Send, y: &dyn for<'a> Fn(&'a i32, &'a f64) -> String);
                |
                |pub Test: () = {
                |    "a" => (),
                |};
                |""".trimMargin(),
            expected = """// auto-generated: "lalrpop 0.23.1"
// sha3: 4acf230483469231f98117092d61696afb0c260fe8f307ec5359d0e91a25abc3
#[allow(unused_extern_crates)]
extern crate lalrpop_util as __lalrpop_util;
#[allow(unused_imports)]
use self::__lalrpop_util::state_machine as __state_machine;
#[allow(unused_extern_crates)]
extern crate alloc;

#[rustfmt::skip]
#[allow(explicit_outlives_requirements, non_snake_case, non_camel_case_types, unused_mut, unused_variables, unused_imports, unused_parens, clippy::needless_lifetimes, clippy::type_complexity, clippy::needless_return, clippy::too_many_arguments, clippy::match_single_binding, clippy::clone_on_copy, clippy::unit_arg)]
mod __parse__Test {

    #[allow(unused_extern_crates)]
    extern crate lalrpop_util as __lalrpop_util;
    #[allow(unused_imports)]
    use self::__lalrpop_util::state_machine as __state_machine;
    #[allow(unused_extern_crates)]
    extern crate alloc;
    use self::__lalrpop_util::lexer::Token;
    pub struct TestParser {
        builder: __lalrpop_util::lexer::MatcherBuilder,
        _priv: (),
    }

    impl Default for TestParser { fn default() -> Self { Self::new() } }
    impl TestParser {
        pub fn new() -> TestParser {
            let __builder = super::__intern_token::new_builder();
            TestParser {
                builder: __builder,
                _priv: (),
            }
        }

        #[allow(dead_code)]
        pub fn parse<
            'input,
        >(
            &self,
            x: &dyn Send,
            y: &dyn for<'a> Fn(&'a i32, &'a f64) -> String,
            input: &'input str,
        ) -> Result<(), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>
        {
            let _ = self.builder;
            let __ascent = __ascent::TestParser::new().parse(
                x,
                y,
                input,
            );
            let __parse_table = __parse_table::TestParser::new().parse(
                x,
                y,
                input,
            );
            assert_eq!(__ascent, __parse_table);
            return __ascent;
        }
    }
    #[rustfmt::skip]
    mod __ascent {

        #[rustfmt::skip]
        #[allow(explicit_outlives_requirements, non_snake_case, non_camel_case_types, unused_mut, unused_variables, unused_imports, unused_parens, clippy::needless_lifetimes, clippy::type_complexity, clippy::needless_return, clippy::too_many_arguments, clippy::match_single_binding, clippy::clone_on_copy, clippy::unit_arg)]
        mod __parse__Test {

            #[allow(unused_extern_crates)]
            extern crate lalrpop_util as __lalrpop_util;
            #[allow(unused_imports)]
            use self::__lalrpop_util::state_machine as __state_machine;
            #[allow(unused_extern_crates)]
            extern crate alloc;
            use self::__lalrpop_util::lexer::Token;
            pub struct TestParser {
                builder: __lalrpop_util::lexer::MatcherBuilder,
                _priv: (),
            }

            impl Default for TestParser { fn default() -> Self { Self::new() } }
            impl TestParser {
                pub fn new() -> TestParser {
                    let __builder = super::super::super::__intern_token::new_builder();
                    TestParser {
                        builder: __builder,
                        _priv: (),
                    }
                }

                #[allow(dead_code)]
                pub fn parse<
                    'input,
                >(
                    &self,
                    x: &dyn Send,
                    y: &dyn for<'a> Fn(&'a i32, &'a f64) -> String,
                    input: &'input str,
                ) -> Result<(), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>
                {
                    let mut __tokens = self.builder.matcher(input);
                    let __lookahead = match __tokens.next() {
                        Some(Ok(v)) => Some(v),
                        Some(Err(e)) => return Err(e),
                        None => None,
                    };
                    match __state0(x, y, input, &mut __tokens, __lookahead, core::marker::PhantomData::<(&())>)? {
                        (Some(__lookahead), _) => {
                            Err(__lalrpop_util::ParseError::ExtraToken { token: __lookahead })
                        }
                        (None, __Nonterminal::____Test((_, __nt, _))) => {
                            Ok(__nt)
                        }
                        _ => unreachable!(),
                    }
                }
            }

            #[allow(dead_code)]
            enum __Nonterminal<>
             {
                Test((usize, (), usize)),
                ____Test((usize, (), usize)),
            }

            // State 0
            //     AllInputs = []
            //     OptionalInputs = []
            //     FixedInputs = []
            //     WillPushLen = 0
            //     WillPush = []
            //     WillProduce = None
            //
            //     Test = (*) "a" ["a", Eof]
            //     __Test = (*) Test ["a", Eof]
            //
            //   "a" -> S2
            //
            //     Test -> S1
            fn __state0<
                'input,
                __TOKENS: Iterator<Item=Result<(usize, Token<'input>, usize),__lalrpop_util::ParseError<usize, Token<'input>, &'static str>>>,
            >(
                x: &dyn Send,
                y: &dyn for<'a> Fn(&'a i32, &'a f64) -> String,
                input: &'input str,
                __tokens: &mut __TOKENS,
                __lookahead: Option<(usize, Token<'input>, usize)>,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> Result<(Option<(usize, Token<'input>, usize)>, __Nonterminal<>), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>
            {
                let mut __result: (Option<(usize, Token<'input>, usize)>, __Nonterminal<>);
                match __lookahead {
                    Some((__loc1, Token(0, __tok0), __loc2)) => {
                        let __sym0 = (__loc1, (__tok0), __loc2);
                        __result = __state2(x, y, input, __tokens, __sym0, core::marker::PhantomData::<(&())>)?;
                    }
                    _ => {
                        #[allow(clippy::needless_raw_string_hashes)]
                        let __expected = alloc::vec![
                            r###""a""###.to_string(),
                        ];
                        return Err(
                            match __lookahead {
                                Some(__token) => {
                                    __lalrpop_util::ParseError::UnrecognizedToken {
                                        token: __token,
                                        expected: __expected,
                                    }
                                }
                                None => {
                                    let __location = Default::default();
                                    __lalrpop_util::ParseError::UnrecognizedEof {
                                        location: __location,
                                        expected: __expected,
                                    }
                                }
                            }
                        )
                    }
                }
                #[allow(clippy::never_loop)]
                loop {
                    let (__lookahead, __nt) = __result;
                    match __nt {
                        __Nonterminal::Test(__sym0) => {
                            __result = __state1(x, y, input, __tokens, __lookahead, __sym0, core::marker::PhantomData::<(&())>)?;
                        }
                        _ => {
                            return Ok((__lookahead, __nt));
                        }
                    }
                }
            }

            // State 1
            //     AllInputs = [Test]
            //     OptionalInputs = []
            //     FixedInputs = [Test]
            //     WillPushLen = 0
            //     WillPush = []
            //     WillProduce = Some(__Test)
            //
            //     __Test = Test (*) ["a", Eof]
            //
            //   [Eof] -> __Test = Test => ActionFn(0);
            //
            fn __state1<
                'input,
                __TOKENS: Iterator<Item=Result<(usize, Token<'input>, usize),__lalrpop_util::ParseError<usize, Token<'input>, &'static str>>>,
            >(
                x: &dyn Send,
                y: &dyn for<'a> Fn(&'a i32, &'a f64) -> String,
                input: &'input str,
                __tokens: &mut __TOKENS,
                __lookahead: Option<(usize, Token<'input>, usize)>,
                __sym0: (usize, (), usize),
                _: core::marker::PhantomData<(&'input ())>,
            ) -> Result<(Option<(usize, Token<'input>, usize)>, __Nonterminal<>), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>
            {
                let mut __result: (Option<(usize, Token<'input>, usize)>, __Nonterminal<>);
                match __lookahead {
                    None => {
                        let __start = __sym0.0.clone();
                        let __end = __sym0.2.clone();
                        let __nt = super::super::super::__action0::<>(x, y, input, __sym0);
                        let __nt = __Nonterminal::____Test((
                            __start,
                            __nt,
                            __end,
                        ));
                        __result = (__lookahead, __nt);
                        return Ok(__result);
                    }
                    _ => {
                        #[allow(clippy::needless_raw_string_hashes)]
                        let __expected = alloc::vec![
                        ];
                        return Err(
                            match __lookahead {
                                Some(__token) => {
                                    __lalrpop_util::ParseError::UnrecognizedToken {
                                        token: __token,
                                        expected: __expected,
                                    }
                                }
                                None => {
                                    let __location = __sym0.2;
                                    __lalrpop_util::ParseError::UnrecognizedEof {
                                        location: __location,
                                        expected: __expected,
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // State 2
            //     AllInputs = ["a"]
            //     OptionalInputs = []
            //     FixedInputs = ["a"]
            //     WillPushLen = 0
            //     WillPush = []
            //     WillProduce = Some(Test)
            //
            //     Test = "a" (*) ["a", Eof]
            //
            //   [Eof] -> Test = "a" => ActionFn(1);
            //
            fn __state2<
                'input,
                __TOKENS: Iterator<Item=Result<(usize, Token<'input>, usize),__lalrpop_util::ParseError<usize, Token<'input>, &'static str>>>,
            >(
                x: &dyn Send,
                y: &dyn for<'a> Fn(&'a i32, &'a f64) -> String,
                input: &'input str,
                __tokens: &mut __TOKENS,
                __sym0: (usize, &'input str, usize),
                _: core::marker::PhantomData<(&'input ())>,
            ) -> Result<(Option<(usize, Token<'input>, usize)>, __Nonterminal<>), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>
            {
                let mut __result: (Option<(usize, Token<'input>, usize)>, __Nonterminal<>);
                let __lookahead = match __tokens.next() {
                    Some(Ok(v)) => Some(v),
                    Some(Err(e)) => return Err(e),
                    None => None,
                };
                match __lookahead {
                    None => {
                        let __start = __sym0.0.clone();
                        let __end = __sym0.2.clone();
                        let __nt = super::super::super::__action1::<>(x, y, input, __sym0);
                        let __nt = __Nonterminal::Test((
                            __start,
                            __nt,
                            __end,
                        ));
                        __result = (__lookahead, __nt);
                        return Ok(__result);
                    }
                    _ => {
                        #[allow(clippy::needless_raw_string_hashes)]
                        let __expected = alloc::vec![
                        ];
                        return Err(
                            match __lookahead {
                                Some(__token) => {
                                    __lalrpop_util::ParseError::UnrecognizedToken {
                                        token: __token,
                                        expected: __expected,
                                    }
                                }
                                None => {
                                    let __location = __sym0.2;
                                    __lalrpop_util::ParseError::UnrecognizedEof {
                                        location: __location,
                                        expected: __expected,
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
        pub use self::__parse__Test::TestParser;
    }
    #[rustfmt::skip]
    mod __parse_table {

        #[rustfmt::skip]
        #[allow(explicit_outlives_requirements, non_snake_case, non_camel_case_types, unused_mut, unused_variables, unused_imports, unused_parens, clippy::needless_lifetimes, clippy::type_complexity, clippy::needless_return, clippy::too_many_arguments, clippy::match_single_binding, clippy::clone_on_copy, clippy::unit_arg)]
        mod __parse__Test {

            #[allow(unused_extern_crates)]
            extern crate lalrpop_util as __lalrpop_util;
            #[allow(unused_imports)]
            use self::__lalrpop_util::state_machine as __state_machine;
            #[allow(unused_extern_crates)]
            extern crate alloc;
            use self::__lalrpop_util::lexer::Token;
            #[allow(dead_code)]
            pub(crate) enum __Symbol<'input>
             {
                Variant0(&'input str),
                Variant1(()),
            }
            const __ACTION: &[i8] = &[
                // State 0
                //     Test = (*) "a" ["a", Eof]
                //     __Test = (*) Test ["a", Eof]
                3,  // on "a", goto 2

                // State 1
                //     __Test = Test (*) ["a", Eof]
                0,  // on "a", error

                // State 2
                //     Test = "a" (*) ["a", Eof]
                0,  // on "a", error

            ];
            fn __action(state: i8, integer: usize) -> i8 {
                __ACTION[(state as usize)  + integer]
            }
            const __EOF_ACTION: &[i8] = &[
                // State 0
                0,  // on Eof, error

                // State 1
                -2,  // on Eof, reduce `__Test = Test => ActionFn(0);`

                // State 2
                -1,  // on Eof, reduce `Test = "a" => ActionFn(1);`

            ];
            fn __goto(state: i8, nt: usize) -> i8 {
                match nt {
                    0 => 1,
                    _ => 0,
                }
            }
            #[allow(clippy::needless_raw_string_hashes)]
            const __TERMINAL: &[&str] = &[
                r###""a""###,
            ];
            fn __expected_tokens(__state: i8) -> alloc::vec::Vec<alloc::string::String> {
                __TERMINAL.iter().enumerate().filter_map(|(index, terminal)| {
                    let next_state = __action(__state, index);
                    if next_state == 0 {
                        None
                    } else {
                        Some(alloc::string::ToString::to_string(terminal))
                    }
                }).collect()
            }
            fn __expected_tokens_from_states<
                'input,
                '__1,
                '__2,
            >(
                __states: &[i8],
                _: core::marker::PhantomData<(&'input ())>,
            ) -> alloc::vec::Vec<alloc::string::String>
            {
                __TERMINAL.iter().enumerate().filter_map(|(index, terminal)| {
                    if __accepts(None, __states, Some(index), core::marker::PhantomData::<(&())>) {
                        Some(alloc::string::ToString::to_string(terminal))
                    } else {
                        None
                    }
                }).collect()
            }
            struct __StateMachine<'input, '__1, '__2>
            where 
            {
                x: &'__1 dyn Send,
                y: &'__2 dyn for<'a> Fn(&'a i32, &'a f64) -> String,
                input: &'input str,
                __phantom: core::marker::PhantomData<(&'input ())>,
            }
            impl<'input, '__1, '__2> __state_machine::ParserDefinition for __StateMachine<'input, '__1, '__2>
            where 
            {
                type Location = usize;
                type Error = &'static str;
                type Token = Token<'input>;
                type TokenIndex = usize;
                type Symbol = __Symbol<'input>;
                type Success = ();
                type StateIndex = i8;
                type Action = i8;
                type ReduceIndex = i8;
                type NonterminalIndex = usize;

                #[inline]
                fn start_location(&self) -> Self::Location {
                      Default::default()
                }

                #[inline]
                fn start_state(&self) -> Self::StateIndex {
                      0
                }

                #[inline]
                fn token_to_index(&self, token: &Self::Token) -> Option<usize> {
                    __token_to_integer(token, core::marker::PhantomData::<(&())>)
                }

                #[inline]
                fn action(&self, state: i8, integer: usize) -> i8 {
                    __action(state, integer)
                }

                #[inline]
                fn error_action(&self, state: i8) -> i8 {
                    __action(state, 0)
                }

                #[inline]
                fn eof_action(&self, state: i8) -> i8 {
                    __EOF_ACTION[state as usize]
                }

                #[inline]
                fn goto(&self, state: i8, nt: usize) -> i8 {
                    __goto(state, nt)
                }

                fn token_to_symbol(&self, token_index: usize, token: Self::Token) -> Self::Symbol {
                    __token_to_symbol(token_index, token, core::marker::PhantomData::<(&())>)
                }

                fn expected_tokens(&self, state: i8) -> alloc::vec::Vec<alloc::string::String> {
                    __expected_tokens(state)
                }

                fn expected_tokens_from_states(&self, states: &[i8]) -> alloc::vec::Vec<alloc::string::String> {
                    __expected_tokens_from_states(states, core::marker::PhantomData::<(&())>)
                }

                #[inline]
                fn uses_error_recovery(&self) -> bool {
                    false
                }

                #[inline]
                fn error_recovery_symbol(
                    &self,
                    recovery: __state_machine::ErrorRecovery<Self>,
                ) -> Self::Symbol {
                    panic!("error recovery not enabled for this grammar")
                }

                fn reduce(
                    &mut self,
                    action: i8,
                    start_location: Option<&Self::Location>,
                    states: &mut alloc::vec::Vec<i8>,
                    symbols: &mut alloc::vec::Vec<__state_machine::SymbolTriple<Self>>,
                ) -> Option<__state_machine::ParseResult<Self>> {
                    __reduce(
                        self.x,
                        self.y,
                        self.input,
                        action,
                        start_location,
                        states,
                        symbols,
                        core::marker::PhantomData::<(&())>,
                    )
                }

                fn simulate_reduce(&self, action: i8) -> __state_machine::SimulatedReduce<Self> {
                    __simulate_reduce(action, core::marker::PhantomData::<(&())>)
                }
            }
            fn __token_to_integer<
                'input,
            >(
                __token: &Token<'input>,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> Option<usize>
            {
                #[warn(unused_variables)]
                match __token {
                    Token(0, _) if true => Some(0),
                    _ => None,
                }
            }
            fn __token_to_symbol<
                'input,
            >(
                __token_index: usize,
                __token: Token<'input>,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> __Symbol<'input>
            {
                #[allow(clippy::manual_range_patterns)]match __token_index {
                    0 => match __token {
                        Token(0, __tok0) if true => __Symbol::Variant0(__tok0),
                        _ => unreachable!(),
                    },
                    _ => unreachable!(),
                }
            }
            fn __simulate_reduce<
                'input,
                '__1,
                '__2,
            >(
                __reduce_index: i8,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> __state_machine::SimulatedReduce<__StateMachine<'input, '__1, '__2>>
            {
                match __reduce_index {
                    // simulate Test = "a" => ActionFn(1);
                    0 => {
                        __state_machine::SimulatedReduce::Reduce {
                            states_to_pop: 1,
                            nonterminal_produced: 0,
                        }
                    }
                    // simulate __Test = Test => ActionFn(0);
                    1 => __state_machine::SimulatedReduce::Accept,
                    _ => panic!("invalid reduction index {__reduce_index}")
                }
            }
            pub struct TestParser {
                builder: __lalrpop_util::lexer::MatcherBuilder,
                _priv: (),
            }

            impl Default for TestParser { fn default() -> Self { Self::new() } }
            impl TestParser {
                pub fn new() -> TestParser {
                    let __builder = super::super::super::__intern_token::new_builder();
                    TestParser {
                        builder: __builder,
                        _priv: (),
                    }
                }

                #[allow(dead_code)]
                pub fn parse<
                    'input,
                >(
                    &self,
                    x: &dyn Send,
                    y: &dyn for<'a> Fn(&'a i32, &'a f64) -> String,
                    input: &'input str,
                ) -> Result<(), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>
                {
                    let mut __tokens = self.builder.matcher(input);
                    __state_machine::Parser::drive(
                        __StateMachine {
                            x,
                            y,
                            input,
                            __phantom: core::marker::PhantomData::<(&())>,
                        },
                        __tokens,
                    )
                }
            }
            fn __accepts<
                'input,
                '__1,
                '__2,
            >(
                __error_state: Option<i8>,
                __states: &[i8],
                __opt_integer: Option<usize>,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> bool
            {
                let mut __states = __states.to_vec();
                __states.extend(__error_state);
                loop {
                    let mut __states_len = __states.len();
                    let __top = __states[__states_len - 1];
                    let __action = match __opt_integer {
                        None => __EOF_ACTION[__top as usize],
                        Some(__integer) => __action(__top, __integer),
                    };
                    if __action == 0 { return false; }
                    if __action > 0 { return true; }
                    let (__to_pop, __nt) = match __simulate_reduce(-(__action + 1), core::marker::PhantomData::<(&())>) {
                        __state_machine::SimulatedReduce::Reduce {
                            states_to_pop, nonterminal_produced
                        } => (states_to_pop, nonterminal_produced),
                        __state_machine::SimulatedReduce::Accept => return true,
                    };
                    __states_len -= __to_pop;
                    __states.truncate(__states_len);
                    let __top = __states[__states_len - 1];
                    let __next_state = __goto(__top, __nt);
                    __states.push(__next_state);
                }
            }
            fn __reduce<
                'input,
            >(
                x: &dyn Send,
                y: &dyn for<'a> Fn(&'a i32, &'a f64) -> String,
                input: &'input str,
                __action: i8,
                __lookahead_start: Option<&usize>,
                __states: &mut alloc::vec::Vec<i8>,
                __symbols: &mut alloc::vec::Vec<(usize,__Symbol<'input>,usize)>,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> Option<Result<(),__lalrpop_util::ParseError<usize, Token<'input>, &'static str>>>
            {
                let (__pop_states, __nonterminal) = match __action {
                    0 => {
                        __reduce0(x, y, input, __lookahead_start, __symbols, core::marker::PhantomData::<(&())>)
                    }
                    1 => {
                        // __Test = Test => ActionFn(0);
                        let __sym0 = __pop_Variant1(__symbols);
                        let __start = __sym0.0.clone();
                        let __end = __sym0.2.clone();
                        let __nt = super::super::super::__action0::<>(x, y, input, __sym0);
                        return Some(Ok(__nt));
                    }
                    _ => panic!("invalid action code {__action}")
                };
                let __states_len = __states.len();
                __states.truncate(__states_len - __pop_states);
                let __state = *__states.last().unwrap();
                let __next_state = __goto(__state, __nonterminal);
                __states.push(__next_state);
                None
            }
            #[inline(never)]
            fn __symbol_type_mismatch() -> ! {
                panic!("symbol type mismatch")
            }
            fn __pop_Variant1<
              'input,
            >(
                __symbols: &mut alloc::vec::Vec<(usize,__Symbol<'input>,usize)>
            ) -> (usize, (), usize)
             {
                match __symbols.pop() {
                    Some((__l, __Symbol::Variant1(__v), __r)) => (__l, __v, __r),
                    _ => __symbol_type_mismatch()
                }
            }
            fn __pop_Variant0<
              'input,
            >(
                __symbols: &mut alloc::vec::Vec<(usize,__Symbol<'input>,usize)>
            ) -> (usize, &'input str, usize)
             {
                match __symbols.pop() {
                    Some((__l, __Symbol::Variant0(__v), __r)) => (__l, __v, __r),
                    _ => __symbol_type_mismatch()
                }
            }
            fn __reduce0<
                'input,
            >(
                x: &dyn Send,
                y: &dyn for<'a> Fn(&'a i32, &'a f64) -> String,
                input: &'input str,
                __lookahead_start: Option<&usize>,
                __symbols: &mut alloc::vec::Vec<(usize,__Symbol<'input>,usize)>,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> (usize, usize)
            {
                // Test = "a" => ActionFn(1);
                let __sym0 = __pop_Variant0(__symbols);
                let __start = __sym0.0.clone();
                let __end = __sym0.2.clone();
                let __nt = super::super::super::__action1::<>(x, y, input, __sym0);
                __symbols.push((__start, __Symbol::Variant1(__nt), __end));
                (1, 0)
            }
        }
        pub use self::__parse__Test::TestParser;
    }
}
#[allow(unused_imports)]
pub use self::__parse__Test::TestParser;
#[rustfmt::skip]
mod __intern_token {
    #![allow(unused_imports)]
    #[allow(unused_extern_crates)]
    extern crate lalrpop_util as __lalrpop_util;
    #[allow(unused_imports)]
    use self::__lalrpop_util::state_machine as __state_machine;
    #[allow(unused_extern_crates)]
    extern crate alloc;
    pub fn new_builder() -> __lalrpop_util::lexer::MatcherBuilder {
        let __strs: &[(&str, bool)] = &[
            ("a", false),
            (r"\s+", true),
        ];
        __lalrpop_util::lexer::MatcherBuilder::new(__strs.iter().copied()).unwrap()
    }
}
pub(crate) use self::__lalrpop_util::lexer::Token;

#[allow(unused_variables)]
#[allow(clippy::too_many_arguments, clippy::needless_lifetimes, clippy::just_underscores_and_digits)]
fn __action0<
    'input,
>(
    x: &dyn Send,
    y: &dyn for<'a> Fn(&'a i32, &'a f64) -> String,
    input: &'input str,
    (_, __0, _): (usize, (), usize),
)
{
}

#[allow(unused_variables)]
#[allow(clippy::too_many_arguments, clippy::needless_lifetimes, clippy::just_underscores_and_digits)]
fn __action1<
    'input,
>(
    x: &dyn Send,
    y: &dyn for<'a> Fn(&'a i32, &'a f64) -> String,
    input: &'input str,
    (_, __0, _): (usize, &'input str, usize),
)
{
}

#[allow(clippy::type_complexity, dead_code)]
pub trait __ToTriple<'input, >
{
    fn to_triple(self) -> Result<(usize,Token<'input>,usize), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>;
}

impl<'input, > __ToTriple<'input, > for (usize, Token<'input>, usize)
{
    fn to_triple(self) -> Result<(usize,Token<'input>,usize), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>> {
        Ok(self)
    }
}
impl<'input, > __ToTriple<'input, > for Result<(usize, Token<'input>, usize), &'static str>
{
    fn to_triple(self) -> Result<(usize,Token<'input>,usize), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>> {
        self.map_err(|error| __lalrpop_util::ParseError::User { error })
    }
}
""",
            sha3 = "4acf230483469231f98117092d61696afb0c260fe8f307ec5359d0e91a25abc3",
            status = ParityStatus.Matching,
        )

    // ---- match_alternatives ------------------------------------------

    private val matchAlternatives: CodegenParityEntry
        get() = CodegenParityEntry(
            name = "match_alternatives",
            input = """
                |grammar;
                |
                |pub File: String = bare_key bool => format!("{<>} {<>}");
                |
                |match {
                |    r"false|true" => bool,
                |} else {
                |    r"[a-z]+" => bare_key,
                |}
                |""".trimMargin(),
            expected = """// auto-generated: "lalrpop 0.23.1"
// sha3: caa803ce513258e0bb87531b22999b7850bd6a791d01590d8dcf6b4774d4934a
#[allow(unused_extern_crates)]
extern crate lalrpop_util as __lalrpop_util;
#[allow(unused_imports)]
use self::__lalrpop_util::state_machine as __state_machine;
#[allow(unused_extern_crates)]
extern crate alloc;

#[rustfmt::skip]
#[allow(explicit_outlives_requirements, non_snake_case, non_camel_case_types, unused_mut, unused_variables, unused_imports, unused_parens, clippy::needless_lifetimes, clippy::type_complexity, clippy::needless_return, clippy::too_many_arguments, clippy::match_single_binding, clippy::clone_on_copy, clippy::unit_arg)]
mod __parse__File {

    #[allow(unused_extern_crates)]
    extern crate lalrpop_util as __lalrpop_util;
    #[allow(unused_imports)]
    use self::__lalrpop_util::state_machine as __state_machine;
    #[allow(unused_extern_crates)]
    extern crate alloc;
    use self::__lalrpop_util::lexer::Token;
    pub struct FileParser {
        builder: __lalrpop_util::lexer::MatcherBuilder,
        _priv: (),
    }

    impl Default for FileParser { fn default() -> Self { Self::new() } }
    impl FileParser {
        pub fn new() -> FileParser {
            let __builder = super::__intern_token::new_builder();
            FileParser {
                builder: __builder,
                _priv: (),
            }
        }

        #[allow(dead_code)]
        pub fn parse<
            'input,
        >(
            &self,
            input: &'input str,
        ) -> Result<String, __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>
        {
            let _ = self.builder;
            let __ascent = __ascent::FileParser::new().parse(
                input,
            );
            let __parse_table = __parse_table::FileParser::new().parse(
                input,
            );
            assert_eq!(__ascent, __parse_table);
            return __ascent;
        }
    }
    #[rustfmt::skip]
    mod __ascent {

        #[rustfmt::skip]
        #[allow(explicit_outlives_requirements, non_snake_case, non_camel_case_types, unused_mut, unused_variables, unused_imports, unused_parens, clippy::needless_lifetimes, clippy::type_complexity, clippy::needless_return, clippy::too_many_arguments, clippy::match_single_binding, clippy::clone_on_copy, clippy::unit_arg)]
        mod __parse__File {

            #[allow(unused_extern_crates)]
            extern crate lalrpop_util as __lalrpop_util;
            #[allow(unused_imports)]
            use self::__lalrpop_util::state_machine as __state_machine;
            #[allow(unused_extern_crates)]
            extern crate alloc;
            use self::__lalrpop_util::lexer::Token;
            pub struct FileParser {
                builder: __lalrpop_util::lexer::MatcherBuilder,
                _priv: (),
            }

            impl Default for FileParser { fn default() -> Self { Self::new() } }
            impl FileParser {
                pub fn new() -> FileParser {
                    let __builder = super::super::super::__intern_token::new_builder();
                    FileParser {
                        builder: __builder,
                        _priv: (),
                    }
                }

                #[allow(dead_code)]
                pub fn parse<
                    'input,
                >(
                    &self,
                    input: &'input str,
                ) -> Result<String, __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>
                {
                    let mut __tokens = self.builder.matcher(input);
                    let __lookahead = match __tokens.next() {
                        Some(Ok(v)) => Some(v),
                        Some(Err(e)) => return Err(e),
                        None => None,
                    };
                    match __state0(input, &mut __tokens, __lookahead, core::marker::PhantomData::<(&())>)? {
                        (Some(__lookahead), _) => {
                            Err(__lalrpop_util::ParseError::ExtraToken { token: __lookahead })
                        }
                        (None, __Nonterminal::____File((_, __nt, _))) => {
                            Ok(__nt)
                        }
                        _ => unreachable!(),
                    }
                }
            }

            #[allow(dead_code)]
            enum __Nonterminal<>
             {
                File((usize, String, usize)),
                ____File((usize, String, usize)),
            }

            // State 0
            //     AllInputs = []
            //     OptionalInputs = []
            //     FixedInputs = []
            //     WillPushLen = 0
            //     WillPush = []
            //     WillProduce = None
            //
            //     File = (*) bare_key bool [bare_key, bool, Eof]
            //     __File = (*) File [bare_key, bool, Eof]
            //
            //   bare_key -> S2
            //
            //     File -> S1
            fn __state0<
                'input,
                __TOKENS: Iterator<Item=Result<(usize, Token<'input>, usize),__lalrpop_util::ParseError<usize, Token<'input>, &'static str>>>,
            >(
                input: &'input str,
                __tokens: &mut __TOKENS,
                __lookahead: Option<(usize, Token<'input>, usize)>,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> Result<(Option<(usize, Token<'input>, usize)>, __Nonterminal<>), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>
            {
                let mut __result: (Option<(usize, Token<'input>, usize)>, __Nonterminal<>);
                match __lookahead {
                    Some((__loc1, Token(0, __tok0), __loc2)) => {
                        let __sym0 = (__loc1, (__tok0), __loc2);
                        __result = __state2(input, __tokens, __sym0, core::marker::PhantomData::<(&())>)?;
                    }
                    _ => {
                        #[allow(clippy::needless_raw_string_hashes)]
                        let __expected = alloc::vec![
                            r###"bare_key"###.to_string(),
                        ];
                        return Err(
                            match __lookahead {
                                Some(__token) => {
                                    __lalrpop_util::ParseError::UnrecognizedToken {
                                        token: __token,
                                        expected: __expected,
                                    }
                                }
                                None => {
                                    let __location = Default::default();
                                    __lalrpop_util::ParseError::UnrecognizedEof {
                                        location: __location,
                                        expected: __expected,
                                    }
                                }
                            }
                        )
                    }
                }
                #[allow(clippy::never_loop)]
                loop {
                    let (__lookahead, __nt) = __result;
                    match __nt {
                        __Nonterminal::File(__sym0) => {
                            __result = __state1(input, __tokens, __lookahead, __sym0, core::marker::PhantomData::<(&())>)?;
                        }
                        _ => {
                            return Ok((__lookahead, __nt));
                        }
                    }
                }
            }

            // State 1
            //     AllInputs = [File]
            //     OptionalInputs = []
            //     FixedInputs = [File]
            //     WillPushLen = 0
            //     WillPush = []
            //     WillProduce = Some(__File)
            //
            //     __File = File (*) [bare_key, bool, Eof]
            //
            //   [Eof] -> __File = File => ActionFn(0);
            //
            fn __state1<
                'input,
                __TOKENS: Iterator<Item=Result<(usize, Token<'input>, usize),__lalrpop_util::ParseError<usize, Token<'input>, &'static str>>>,
            >(
                input: &'input str,
                __tokens: &mut __TOKENS,
                __lookahead: Option<(usize, Token<'input>, usize)>,
                __sym0: (usize, String, usize),
                _: core::marker::PhantomData<(&'input ())>,
            ) -> Result<(Option<(usize, Token<'input>, usize)>, __Nonterminal<>), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>
            {
                let mut __result: (Option<(usize, Token<'input>, usize)>, __Nonterminal<>);
                match __lookahead {
                    None => {
                        let __start = __sym0.0.clone();
                        let __end = __sym0.2.clone();
                        let __nt = super::super::super::__action0::<>(input, __sym0);
                        let __nt = __Nonterminal::____File((
                            __start,
                            __nt,
                            __end,
                        ));
                        __result = (__lookahead, __nt);
                        return Ok(__result);
                    }
                    _ => {
                        #[allow(clippy::needless_raw_string_hashes)]
                        let __expected = alloc::vec![
                        ];
                        return Err(
                            match __lookahead {
                                Some(__token) => {
                                    __lalrpop_util::ParseError::UnrecognizedToken {
                                        token: __token,
                                        expected: __expected,
                                    }
                                }
                                None => {
                                    let __location = __sym0.2;
                                    __lalrpop_util::ParseError::UnrecognizedEof {
                                        location: __location,
                                        expected: __expected,
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // State 2
            //     AllInputs = [bare_key]
            //     OptionalInputs = []
            //     FixedInputs = [bare_key]
            //     WillPushLen = 1
            //     WillPush = [bool]
            //     WillProduce = Some(File)
            //
            //     File = bare_key (*) bool [bare_key, bool, Eof]
            //
            //   bool -> S3
            //
            fn __state2<
                'input,
                __TOKENS: Iterator<Item=Result<(usize, Token<'input>, usize),__lalrpop_util::ParseError<usize, Token<'input>, &'static str>>>,
            >(
                input: &'input str,
                __tokens: &mut __TOKENS,
                __sym0: (usize, &'input str, usize),
                _: core::marker::PhantomData<(&'input ())>,
            ) -> Result<(Option<(usize, Token<'input>, usize)>, __Nonterminal<>), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>
            {
                let mut __result: (Option<(usize, Token<'input>, usize)>, __Nonterminal<>);
                let __lookahead = match __tokens.next() {
                    Some(Ok(v)) => Some(v),
                    Some(Err(e)) => return Err(e),
                    None => None,
                };
                match __lookahead {
                    Some((__loc1, Token(1, __tok0), __loc2)) => {
                        let __sym1 = (__loc1, (__tok0), __loc2);
                        __result = __state3(input, __tokens, __sym0, __sym1, core::marker::PhantomData::<(&())>)?;
                        return Ok(__result);
                    }
                    _ => {
                        #[allow(clippy::needless_raw_string_hashes)]
                        let __expected = alloc::vec![
                            r###"bool"###.to_string(),
                        ];
                        return Err(
                            match __lookahead {
                                Some(__token) => {
                                    __lalrpop_util::ParseError::UnrecognizedToken {
                                        token: __token,
                                        expected: __expected,
                                    }
                                }
                                None => {
                                    let __location = __sym0.2;
                                    __lalrpop_util::ParseError::UnrecognizedEof {
                                        location: __location,
                                        expected: __expected,
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // State 3
            //     AllInputs = [bare_key, bool]
            //     OptionalInputs = []
            //     FixedInputs = [bare_key, bool]
            //     WillPushLen = 0
            //     WillPush = []
            //     WillProduce = Some(File)
            //
            //     File = bare_key bool (*) [bare_key, bool, Eof]
            //
            //   [Eof] -> File = bare_key, bool => ActionFn(1);
            //
            fn __state3<
                'input,
                __TOKENS: Iterator<Item=Result<(usize, Token<'input>, usize),__lalrpop_util::ParseError<usize, Token<'input>, &'static str>>>,
            >(
                input: &'input str,
                __tokens: &mut __TOKENS,
                __sym0: (usize, &'input str, usize),
                __sym1: (usize, &'input str, usize),
                _: core::marker::PhantomData<(&'input ())>,
            ) -> Result<(Option<(usize, Token<'input>, usize)>, __Nonterminal<>), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>
            {
                let mut __result: (Option<(usize, Token<'input>, usize)>, __Nonterminal<>);
                let __lookahead = match __tokens.next() {
                    Some(Ok(v)) => Some(v),
                    Some(Err(e)) => return Err(e),
                    None => None,
                };
                match __lookahead {
                    None => {
                        let __start = __sym0.0.clone();
                        let __end = __sym1.2.clone();
                        let __nt = super::super::super::__action1::<>(input, __sym0, __sym1);
                        let __nt = __Nonterminal::File((
                            __start,
                            __nt,
                            __end,
                        ));
                        __result = (__lookahead, __nt);
                        return Ok(__result);
                    }
                    _ => {
                        #[allow(clippy::needless_raw_string_hashes)]
                        let __expected = alloc::vec![
                        ];
                        return Err(
                            match __lookahead {
                                Some(__token) => {
                                    __lalrpop_util::ParseError::UnrecognizedToken {
                                        token: __token,
                                        expected: __expected,
                                    }
                                }
                                None => {
                                    let __location = __sym1.2;
                                    __lalrpop_util::ParseError::UnrecognizedEof {
                                        location: __location,
                                        expected: __expected,
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
        pub use self::__parse__File::FileParser;
    }
    #[rustfmt::skip]
    mod __parse_table {

        #[rustfmt::skip]
        #[allow(explicit_outlives_requirements, non_snake_case, non_camel_case_types, unused_mut, unused_variables, unused_imports, unused_parens, clippy::needless_lifetimes, clippy::type_complexity, clippy::needless_return, clippy::too_many_arguments, clippy::match_single_binding, clippy::clone_on_copy, clippy::unit_arg)]
        mod __parse__File {

            #[allow(unused_extern_crates)]
            extern crate lalrpop_util as __lalrpop_util;
            #[allow(unused_imports)]
            use self::__lalrpop_util::state_machine as __state_machine;
            #[allow(unused_extern_crates)]
            extern crate alloc;
            use self::__lalrpop_util::lexer::Token;
            #[allow(dead_code)]
            pub(crate) enum __Symbol<'input>
             {
                Variant0(&'input str),
                Variant1(String),
            }
            const __ACTION: &[i8] = &[
                // State 0
                //     File = (*) bare_key bool [bare_key, bool, Eof]
                //     __File = (*) File [bare_key, bool, Eof]
                3,  // on bare_key, goto 2
                0,  // on bool, error

                // State 1
                //     __File = File (*) [bare_key, bool, Eof]
                0,  // on bare_key, error
                0,  // on bool, error

                // State 2
                //     File = bare_key (*) bool [bare_key, bool, Eof]
                0,  // on bare_key, error
                4,  // on bool, goto 3

                // State 3
                //     File = bare_key bool (*) [bare_key, bool, Eof]
                0,  // on bare_key, error
                0,  // on bool, error

            ];
            fn __action(state: i8, integer: usize) -> i8 {
                __ACTION[(state as usize) * 2 + integer]
            }
            const __EOF_ACTION: &[i8] = &[
                // State 0
                0,  // on Eof, error

                // State 1
                -2,  // on Eof, reduce `__File = File => ActionFn(0);`

                // State 2
                0,  // on Eof, error

                // State 3
                -1,  // on Eof, reduce `File = bare_key, bool => ActionFn(1);`

            ];
            fn __goto(state: i8, nt: usize) -> i8 {
                match nt {
                    0 => 1,
                    _ => 0,
                }
            }
            #[allow(clippy::needless_raw_string_hashes)]
            const __TERMINAL: &[&str] = &[
                r###"bare_key"###,
                r###"bool"###,
            ];
            fn __expected_tokens(__state: i8) -> alloc::vec::Vec<alloc::string::String> {
                __TERMINAL.iter().enumerate().filter_map(|(index, terminal)| {
                    let next_state = __action(__state, index);
                    if next_state == 0 {
                        None
                    } else {
                        Some(alloc::string::ToString::to_string(terminal))
                    }
                }).collect()
            }
            fn __expected_tokens_from_states<
                'input,
            >(
                __states: &[i8],
                _: core::marker::PhantomData<(&'input ())>,
            ) -> alloc::vec::Vec<alloc::string::String>
            {
                __TERMINAL.iter().enumerate().filter_map(|(index, terminal)| {
                    if __accepts(None, __states, Some(index), core::marker::PhantomData::<(&())>) {
                        Some(alloc::string::ToString::to_string(terminal))
                    } else {
                        None
                    }
                }).collect()
            }
            struct __StateMachine<'input>
            where 
            {
                input: &'input str,
                __phantom: core::marker::PhantomData<(&'input ())>,
            }
            impl<'input> __state_machine::ParserDefinition for __StateMachine<'input>
            where 
            {
                type Location = usize;
                type Error = &'static str;
                type Token = Token<'input>;
                type TokenIndex = usize;
                type Symbol = __Symbol<'input>;
                type Success = String;
                type StateIndex = i8;
                type Action = i8;
                type ReduceIndex = i8;
                type NonterminalIndex = usize;

                #[inline]
                fn start_location(&self) -> Self::Location {
                      Default::default()
                }

                #[inline]
                fn start_state(&self) -> Self::StateIndex {
                      0
                }

                #[inline]
                fn token_to_index(&self, token: &Self::Token) -> Option<usize> {
                    __token_to_integer(token, core::marker::PhantomData::<(&())>)
                }

                #[inline]
                fn action(&self, state: i8, integer: usize) -> i8 {
                    __action(state, integer)
                }

                #[inline]
                fn error_action(&self, state: i8) -> i8 {
                    __action(state, 2 - 1)
                }

                #[inline]
                fn eof_action(&self, state: i8) -> i8 {
                    __EOF_ACTION[state as usize]
                }

                #[inline]
                fn goto(&self, state: i8, nt: usize) -> i8 {
                    __goto(state, nt)
                }

                fn token_to_symbol(&self, token_index: usize, token: Self::Token) -> Self::Symbol {
                    __token_to_symbol(token_index, token, core::marker::PhantomData::<(&())>)
                }

                fn expected_tokens(&self, state: i8) -> alloc::vec::Vec<alloc::string::String> {
                    __expected_tokens(state)
                }

                fn expected_tokens_from_states(&self, states: &[i8]) -> alloc::vec::Vec<alloc::string::String> {
                    __expected_tokens_from_states(states, core::marker::PhantomData::<(&())>)
                }

                #[inline]
                fn uses_error_recovery(&self) -> bool {
                    false
                }

                #[inline]
                fn error_recovery_symbol(
                    &self,
                    recovery: __state_machine::ErrorRecovery<Self>,
                ) -> Self::Symbol {
                    panic!("error recovery not enabled for this grammar")
                }

                fn reduce(
                    &mut self,
                    action: i8,
                    start_location: Option<&Self::Location>,
                    states: &mut alloc::vec::Vec<i8>,
                    symbols: &mut alloc::vec::Vec<__state_machine::SymbolTriple<Self>>,
                ) -> Option<__state_machine::ParseResult<Self>> {
                    __reduce(
                        self.input,
                        action,
                        start_location,
                        states,
                        symbols,
                        core::marker::PhantomData::<(&())>,
                    )
                }

                fn simulate_reduce(&self, action: i8) -> __state_machine::SimulatedReduce<Self> {
                    __simulate_reduce(action, core::marker::PhantomData::<(&())>)
                }
            }
            fn __token_to_integer<
                'input,
            >(
                __token: &Token<'input>,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> Option<usize>
            {
                #[warn(unused_variables)]
                match __token {
                    Token(0, _) if true => Some(0),
                    Token(1, _) if true => Some(1),
                    _ => None,
                }
            }
            fn __token_to_symbol<
                'input,
            >(
                __token_index: usize,
                __token: Token<'input>,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> __Symbol<'input>
            {
                #[allow(clippy::manual_range_patterns)]match __token_index {
                    0 | 1 => match __token {
                        Token(0, __tok0) | Token(1, __tok0) if true => __Symbol::Variant0(__tok0),
                        _ => unreachable!(),
                    },
                    _ => unreachable!(),
                }
            }
            fn __simulate_reduce<
                'input,
            >(
                __reduce_index: i8,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> __state_machine::SimulatedReduce<__StateMachine<'input>>
            {
                match __reduce_index {
                    // simulate File = bare_key, bool => ActionFn(1);
                    0 => {
                        __state_machine::SimulatedReduce::Reduce {
                            states_to_pop: 2,
                            nonterminal_produced: 0,
                        }
                    }
                    // simulate __File = File => ActionFn(0);
                    1 => __state_machine::SimulatedReduce::Accept,
                    _ => panic!("invalid reduction index {__reduce_index}")
                }
            }
            pub struct FileParser {
                builder: __lalrpop_util::lexer::MatcherBuilder,
                _priv: (),
            }

            impl Default for FileParser { fn default() -> Self { Self::new() } }
            impl FileParser {
                pub fn new() -> FileParser {
                    let __builder = super::super::super::__intern_token::new_builder();
                    FileParser {
                        builder: __builder,
                        _priv: (),
                    }
                }

                #[allow(dead_code)]
                pub fn parse<
                    'input,
                >(
                    &self,
                    input: &'input str,
                ) -> Result<String, __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>
                {
                    let mut __tokens = self.builder.matcher(input);
                    __state_machine::Parser::drive(
                        __StateMachine {
                            input,
                            __phantom: core::marker::PhantomData::<(&())>,
                        },
                        __tokens,
                    )
                }
            }
            fn __accepts<
                'input,
            >(
                __error_state: Option<i8>,
                __states: &[i8],
                __opt_integer: Option<usize>,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> bool
            {
                let mut __states = __states.to_vec();
                __states.extend(__error_state);
                loop {
                    let mut __states_len = __states.len();
                    let __top = __states[__states_len - 1];
                    let __action = match __opt_integer {
                        None => __EOF_ACTION[__top as usize],
                        Some(__integer) => __action(__top, __integer),
                    };
                    if __action == 0 { return false; }
                    if __action > 0 { return true; }
                    let (__to_pop, __nt) = match __simulate_reduce(-(__action + 1), core::marker::PhantomData::<(&())>) {
                        __state_machine::SimulatedReduce::Reduce {
                            states_to_pop, nonterminal_produced
                        } => (states_to_pop, nonterminal_produced),
                        __state_machine::SimulatedReduce::Accept => return true,
                    };
                    __states_len -= __to_pop;
                    __states.truncate(__states_len);
                    let __top = __states[__states_len - 1];
                    let __next_state = __goto(__top, __nt);
                    __states.push(__next_state);
                }
            }
            fn __reduce<
                'input,
            >(
                input: &'input str,
                __action: i8,
                __lookahead_start: Option<&usize>,
                __states: &mut alloc::vec::Vec<i8>,
                __symbols: &mut alloc::vec::Vec<(usize,__Symbol<'input>,usize)>,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> Option<Result<String,__lalrpop_util::ParseError<usize, Token<'input>, &'static str>>>
            {
                let (__pop_states, __nonterminal) = match __action {
                    0 => {
                        __reduce0(input, __lookahead_start, __symbols, core::marker::PhantomData::<(&())>)
                    }
                    1 => {
                        // __File = File => ActionFn(0);
                        let __sym0 = __pop_Variant1(__symbols);
                        let __start = __sym0.0.clone();
                        let __end = __sym0.2.clone();
                        let __nt = super::super::super::__action0::<>(input, __sym0);
                        return Some(Ok(__nt));
                    }
                    _ => panic!("invalid action code {__action}")
                };
                let __states_len = __states.len();
                __states.truncate(__states_len - __pop_states);
                let __state = *__states.last().unwrap();
                let __next_state = __goto(__state, __nonterminal);
                __states.push(__next_state);
                None
            }
            #[inline(never)]
            fn __symbol_type_mismatch() -> ! {
                panic!("symbol type mismatch")
            }
            fn __pop_Variant1<
              'input,
            >(
                __symbols: &mut alloc::vec::Vec<(usize,__Symbol<'input>,usize)>
            ) -> (usize, String, usize)
             {
                match __symbols.pop() {
                    Some((__l, __Symbol::Variant1(__v), __r)) => (__l, __v, __r),
                    _ => __symbol_type_mismatch()
                }
            }
            fn __pop_Variant0<
              'input,
            >(
                __symbols: &mut alloc::vec::Vec<(usize,__Symbol<'input>,usize)>
            ) -> (usize, &'input str, usize)
             {
                match __symbols.pop() {
                    Some((__l, __Symbol::Variant0(__v), __r)) => (__l, __v, __r),
                    _ => __symbol_type_mismatch()
                }
            }
            fn __reduce0<
                'input,
            >(
                input: &'input str,
                __lookahead_start: Option<&usize>,
                __symbols: &mut alloc::vec::Vec<(usize,__Symbol<'input>,usize)>,
                _: core::marker::PhantomData<(&'input ())>,
            ) -> (usize, usize)
            {
                // File = bare_key, bool => ActionFn(1);
                assert!(__symbols.len() >= 2);
                let __sym1 = __pop_Variant0(__symbols);
                let __sym0 = __pop_Variant0(__symbols);
                let __start = __sym0.0.clone();
                let __end = __sym1.2.clone();
                let __nt = super::super::super::__action1::<>(input, __sym0, __sym1);
                __symbols.push((__start, __Symbol::Variant1(__nt), __end));
                (2, 0)
            }
        }
        pub use self::__parse__File::FileParser;
    }
}
#[allow(unused_imports)]
pub use self::__parse__File::FileParser;
#[rustfmt::skip]
mod __intern_token {
    #![allow(unused_imports)]
    #[allow(unused_extern_crates)]
    extern crate lalrpop_util as __lalrpop_util;
    #[allow(unused_imports)]
    use self::__lalrpop_util::state_machine as __state_machine;
    #[allow(unused_extern_crates)]
    extern crate alloc;
    pub fn new_builder() -> __lalrpop_util::lexer::MatcherBuilder {
        let __strs: &[(&str, bool)] = &[
            ("[a-z]+", false),
            ("(?:(?:false)|(?:true))", false),
            (r"\s+", true),
        ];
        __lalrpop_util::lexer::MatcherBuilder::new(__strs.iter().copied()).unwrap()
    }
}
pub(crate) use self::__lalrpop_util::lexer::Token;

#[allow(unused_variables)]
#[allow(clippy::too_many_arguments, clippy::needless_lifetimes, clippy::just_underscores_and_digits)]
fn __action0<
    'input,
>(
    input: &'input str,
    (_, __0, _): (usize, String, usize),
) -> String
{
    __0
}

#[allow(unused_variables)]
#[allow(clippy::too_many_arguments, clippy::needless_lifetimes, clippy::just_underscores_and_digits)]
fn __action1<
    'input,
>(
    input: &'input str,
    (_, __0, _): (usize, &'input str, usize),
    (_, __1, _): (usize, &'input str, usize),
) -> String
{
    format!("{__0} {__1}")
}

#[allow(clippy::type_complexity, dead_code)]
pub trait __ToTriple<'input, >
{
    fn to_triple(self) -> Result<(usize,Token<'input>,usize), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>>;
}

impl<'input, > __ToTriple<'input, > for (usize, Token<'input>, usize)
{
    fn to_triple(self) -> Result<(usize,Token<'input>,usize), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>> {
        Ok(self)
    }
}
impl<'input, > __ToTriple<'input, > for Result<(usize, Token<'input>, usize), &'static str>
{
    fn to_triple(self) -> Result<(usize,Token<'input>,usize), __lalrpop_util::ParseError<usize, Token<'input>, &'static str>> {
        self.map_err(|error| __lalrpop_util::ParseError::User { error })
    }
}
""",
            sha3 = "caa803ce513258e0bb87531b22999b7850bd6a791d01590d8dcf6b4774d4934a",
            status = ParityStatus.Matching,
        )

}