import { TMGrammarScope } from '../types';
import { regex } from '../utility';
import {
	ident,
	bracketIdent,
	namedTypePattern as namedType,
	primTypePattern as primType,
} from '../patterns';

export const structExpr: TMGrammarScope = {
	patterns: [
		{
			match: regex`/${namedType}(?=\{)/`,
			captures: {
				1: { name: 'entity.name.byx' },
				2: { name: 'punctuation.accessor.byx' },
				3: { name: 'entity.name.byx' },
			},
		},
	],
};

export const identifier: TMGrammarScope = {
	patterns: [{
		match: ident,
		name: 'entity.name.byx',
	},
	{
		match: bracketIdent,
		name: 'entity.name.byx',
	},
	],
};
