package com.alteryx.byx.language;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ByxFileType extends LanguageFileType  {
	public static final String FILE_EXTENSION = "byx";
		public static final ByxFileType INSTANCE = new ByxFileType();

	protected ByxFileType() {
		super(ByxLanguage.INSTANCE);
	}

	@NotNull
	@Override
	public String getName() {
		return "Byx Language";
	}

	@NotNull
	@Override
	public String getDescription() {
		return "Byx language";
	}

	@NotNull
	@Override
	public String getDefaultExtension() {
		return FILE_EXTENSION;
	}

	@Nullable
	@Override
	public Icon getIcon() {
		return ByxIcons.SAMPLE_ICON;
	}
}

