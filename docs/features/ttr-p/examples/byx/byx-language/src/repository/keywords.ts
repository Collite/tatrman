import { TMGrammarScope } from '../types';

export const keywords: TMGrammarScope = {
	patterns: [
		{
			match: /\b(select|summarize|filter|join|create|input|output|rename|change|retype|convert|compute|calculate|delete|remove|keep|take)\b/,
			captures: {
				1: { name: 'keyword.control.byx' },
			},
		},
		{
			match: /\b(column|columns|col|cols|row|rows|record|records|formula|new|as|only|summary|group|by|of|where|for)\b/,
			captures: {
				1: { name: 'keyword.helper.byx' },
			},
		},
		{
			match: /\b(beginning|starting|first|left|ending|last|characters|chars|character|char)\b/,
			captures: {
				1: { name: 'keyword.helper.byx' },
			},
		},
		{
			match: /\b(is|are|than|have|has|to|comes|come|the|name|type|those|this|these|that|which|with|it)\b/,
			captures: {
				1: { name: 'keyword.helper.byx' },
			},
		},
		{
			match: /\b(string|text|number|decimal|float|double|int|integer|date|time|datetime)\b/,
			captures: {
				1: { name: 'meta.type.byx' },
			},
		},
		{
			match: /\b(equal|equals|same|during|precisely|less|fewer|smaller|before|up to|greater|higher|bigger|more|larger|after|lower|since|older|younger|between|within)\b/,
			captures: {
				1: { name: 'keyword.operator.logical.byx' },
			},
		},
		{
			match: /\b(and|or|not)\b/,
			captures: {
				1: { name: 'keyword.operator.logical.byx' },
			},
		},
		{
			match: /\b(plus|minus|times|div|divided)\b/,
			captures: {
				1: { name: 'keyword.operator.arithmetic.byx' },
			},
		},
		{
			match: /\b(sum|avg|min|max|substr|substring|left|right)\b/,
			captures: {
				1: { name: 'meta.function-call.byx' },
			},
		},
		{
			match: /\b(in|if|then|elseif|else|endif)\b/,
			captures: {
				1: { name: 'meta.function-call.byx' },
			},
		}
	],
};
