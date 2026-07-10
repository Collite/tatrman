import { TMGrammarScope } from '../types';

export const comments: TMGrammarScope = {
	patterns: [
		{
			begin: /\/\//,
			beginCaptures: {
				0: { name: 'punctuation.definition.comment.line.byx' },
			},
			end: /(?=\n|\r)/,
			name: 'comment.line.byx',
		},
		{
			begin: /\/\*/,
			beginCaptures: {
				0: { name: 'punctuation.definition.comment.block.begin.byx' },
			},
			end: /\*\//,
			endCaptures: {
				0: { name: 'punctuation.definition.comment.block.end.byx' },
			},
			name: 'comment.block.byx',
		},
	],
};
