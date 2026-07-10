package com.alteryx.byx.language

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public class ByxColorSettingsPage implements ColorSettingsPage {
	private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[]{
		new AttributesDescriptor("Identifier", ByxSyntaxHighlighter.ID),
		new AttributesDescriptor("Keyword", ByxSyntaxHighlighter.KEYWORD),
		new AttributesDescriptor("String", ByxSyntaxHighlighter.STRING),
		new AttributesDescriptor("Line comment", ByxSyntaxHighlighter.LINE_COMMENT),
		new AttributesDescriptor("Block comment", ByxSyntaxHighlighter.BLOCK_COMMENT),
	};

	@Nullable
	@Override
	public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
		return null;
	}

	@Nullable
	@Override
	public Icon getIcon() {
		return Icons.Byx_ICON;
	}

	@NotNull
	@Override
	public SyntaxHighlighter getHighlighter() {
		return new ByxSyntaxHighlighter();
	}

	@NotNull
	@Override
	public String getDemoText() {
		return
			"create new formula 1*sin(amount) as [My new name].\n"
	}

	@NotNull
	@Override
	public AttributesDescriptor[] getAttributeDescriptors() {
		return DESCRIPTORS;
	}

	@NotNull
	@Override
	public ColorDescriptor[] getColorDescriptors() {
		return ColorDescriptor.EMPTY_ARRAY;
	}

	@NotNull
	@Override
	public String getDisplayName() {
		return "Byx";
	}
}
