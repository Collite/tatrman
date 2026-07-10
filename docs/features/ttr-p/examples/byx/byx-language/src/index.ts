import { TMGrammar } from './types';

import {
	comments,
	constants,
	escapes,
	identifier,
	keywords,
	literals,
	namedType,
	operators,
	primType,
	punctuation,
	stringLiteral,
	structExpr
} from './repository';

const grammar: TMGrammar = {
	name: 'byx',
	scopeName: 'source.byx',
	patterns: [
		{ include: '#comments' },
		{ include: '#keywords' },
		{ include: '#constants' },
		{ include: '#primType' },
		{ include: '#operators' },
		{ include: '#punctuation' },
		{ include: '#structExpr' },
		{ include: '#identifier' },
		{ include: '#literals' },
		{ include: '#namedType' },
	],
	repository: {
		comments,
		constants,
		escapes,
		identifier,
		keywords,
		literals,
		namedType,
		operators,
		primType,
		punctuation,
		stringLiteral,
		structExpr,
	},
};

export default grammar;
