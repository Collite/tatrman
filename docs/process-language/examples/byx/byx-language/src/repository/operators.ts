import { TMGrammarScope } from '../types';

export const operators: TMGrammarScope = {
	patterns: [
		{
			match: /<-/,
			name: 'keyword.operator.arrow.byx',
		},
		{
			match: /(--|\+\+)/,
			captures: {
				1: { name: 'keyword.operator.arithmetic.$1.byx' },
			},
		},
		{
			match: /([-+*\/%]=?)/,
			captures: {
				1: { name: 'keyword.operator.arithmetic.$1.byx' },
			},
		},
		{
			match: /((?:<<|>>|&\^|[&|^])=?)/,
			captures: {
				1: { name: 'keyword.operator.bitwise.$1.byx' },
			},
		},
		{
			match: /(&&|\|\||==|[!<>]=?)/,
			captures: {
				1: { name: 'keyword.operator.comparison.$1.byx' },
			},
		},
		{
			match: /(:=|=)/,
			name: 'keyword.operator.assignment.byx',
		},
	],
};

export const punctuation: TMGrammarScope = {
	patterns: [
		{
			match: /[()]/,
			name: 'punctuation.brace.round.byx',
		},
		{
			match: /[\[\]]/,
			name: 'punctuation.brace.square.byx',
		},
		{
			match: /[{}]/,
			name: 'punctuation.brace.curly.byx',
		},
		{
			match: /,/,
			name: 'punctuation.separator.comma.byx',
		},
		{
			match: /:/,
			name: 'punctuation.separator.key-value.byx',
		},
		{
			match: /\./,
			name: 'punctuation.accessor.byx',
		},
		{
			match: /;/,
			name: 'punctuation.terminator.byx',
		},
	],
};
