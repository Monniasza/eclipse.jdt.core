/*******************************************************************************
 * Copyright (c)  2020 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * This is an implementation of an early-draft specification developed under the Java
 * Community Process (JCP) and is made available for testing and evaluation purposes
 * only. The code is not compatible with any specification of the JCP.
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.core.tests.compiler.regression;

import java.util.Map;

import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;

import junit.framework.Test;

public class JavadocTestForRecord extends JavadocTest {

	static {
//		 TESTS_NAMES = new String[] { "testBug549855a" };
		// TESTS_NUMBERS = new int[] { 1 };
		// TESTS_RANGE = new int[] { 298, -1 };
	}

	public JavadocTestForRecord(String name) {
		super(name);
	}

	String docCommentSupport = CompilerOptions.ENABLED;
	String reportInvalidJavadoc = CompilerOptions.ERROR;
	String reportInvalidJavadocVisibility = CompilerOptions.PRIVATE;
	String reportMissingJavadocTags = CompilerOptions.ERROR;
	String reportMissingJavadocTagsOverriding = CompilerOptions.ENABLED;
	String reportMissingJavadocComments = CompilerOptions.ERROR;
	String reportMissingJavadocCommentsVisibility = CompilerOptions.PROTECTED;

	public static Class testClass() {
		return JavadocTestForRecord.class;
	}

	// Use this static initializer to specify subset for tests
	// All specified tests which does not belong to the class are skipped...
	static {
//		TESTS_PREFIX = "testBug95521";
//		TESTS_NAMES = new String[] { "testBug331872d" };
//		TESTS_NUMBERS = new int[] { 101283 };
//		TESTS_RANGE = new int[] { 23, -1 };
	}

	public static Test suite() {
		return buildMinimalComplianceTestSuite(testClass(), F_14);
	}

	@SuppressWarnings("unchecked")
	protected Map getCompilerOptions() {
		Map options = super.getCompilerOptions();
		options.put(CompilerOptions.OPTION_DocCommentSupport, this.docCommentSupport);
		options.put(CompilerOptions.OPTION_ReportInvalidJavadoc, this.reportInvalidJavadoc);
		if (!CompilerOptions.IGNORE.equals(this.reportInvalidJavadoc)) {
			options.put(CompilerOptions.OPTION_ReportInvalidJavadocTagsVisibility, this.reportInvalidJavadocVisibility);
		}
		if (this.reportMissingJavadocComments != null)
			options.put(CompilerOptions.OPTION_ReportMissingJavadocComments, this.reportMissingJavadocComments);
		else
			options.put(CompilerOptions.OPTION_ReportMissingJavadocComments, this.reportInvalidJavadoc);
		if (this.reportMissingJavadocCommentsVisibility != null)
			options.put(CompilerOptions.OPTION_ReportMissingJavadocCommentsVisibility,
					this.reportMissingJavadocCommentsVisibility);
		if (this.reportMissingJavadocTags != null) {
			options.put(CompilerOptions.OPTION_ReportMissingJavadocTags, this.reportMissingJavadocTags);
			if (this.reportMissingJavadocTagsOverriding != null) {
				options.put(CompilerOptions.OPTION_ReportMissingJavadocTagsOverriding,
						this.reportMissingJavadocTagsOverriding);
			}
		} else {
			options.put(CompilerOptions.OPTION_ReportMissingJavadocTags, this.reportInvalidJavadoc);
		}
		if (this.reportMissingJavadocComments != null)
			options.put(CompilerOptions.OPTION_ReportMissingJavadocComments, this.reportMissingJavadocComments);
		options.put(CompilerOptions.OPTION_ReportFieldHiding, CompilerOptions.IGNORE);
		options.put(CompilerOptions.OPTION_ReportSyntheticAccessEmulation, CompilerOptions.IGNORE);
		options.put(CompilerOptions.OPTION_ReportDeprecation, CompilerOptions.ERROR);
		options.put(CompilerOptions.OPTION_ReportUnusedImport, CompilerOptions.ERROR);
		options.put(CompilerOptions.OPTION_ReportUnusedPrivateMember, CompilerOptions.IGNORE);
		options.put(CompilerOptions.OPTION_ReportMissingJavadocTagsMethodTypeParameters, CompilerOptions.ENABLED);
		options.put(CompilerOptions.OPTION_Release, CompilerOptions.ENABLED);
		options.put(CompilerOptions.OPTION_EnablePreviews, CompilerOptions.ENABLED);
		options.put(CompilerOptions.OPTION_EnablePreviews, CompilerOptions.ENABLED);
		options.put(CompilerOptions.OPTION_ReportPreviewFeatures, CompilerOptions.IGNORE);
		return options;
	}
	/*
	 * (non-Javadoc)
	 * 
	 * @see junit.framework.TestCase#setUp()
	 */

	@Override
	protected void runNegativeTest(String[] testFiles, String expectedCompilerLog) {
		runNegativeTest(testFiles, expectedCompilerLog, JavacTestOptions.forReleaseWithPreview("14"));
	}

	protected void setUp() throws Exception {
		super.setUp();
		this.docCommentSupport = CompilerOptions.ENABLED;
		this.reportInvalidJavadoc = CompilerOptions.ERROR;
		this.reportInvalidJavadocVisibility = CompilerOptions.PRIVATE;
		this.reportMissingJavadocTags = CompilerOptions.ERROR;
		this.reportMissingJavadocComments = CompilerOptions.ERROR;
		this.reportMissingJavadocTagsOverriding = CompilerOptions.ENABLED;
		this.reportMissingJavadocComments = CompilerOptions.ERROR;
	}

	public void test001() {
		this.runNegativeTest(new String[] { "X.java", "public record X() {\n" + "}\n" },
				"----------\n" + "1. ERROR in X.java (at line 1)\n" + "	public record X() {\n" + "	              ^\n"
						+ "Javadoc: Missing comment for public declaration\n" + "----------\n",
				JavacTestOptions.Excuse.EclipseWarningConfiguredAsError);
	}

	public void test002() {
		this.runNegativeTest(
				new String[] { "X.java",
						"	/**\n" + "	 * @param radius radius of X\n" + "	 */\n" + "public record X(int radius) {\n"
								+ "	public void foo() {\n" + "	}\n" + "}\n" },
				"----------\n" + "1. ERROR in X.java (at line 5)\n" + "	public void foo() {\n" + "	            ^^^^^\n"
						+ "Javadoc: Missing comment for public declaration\n" + "----------\n",
				JavacTestOptions.Excuse.EclipseWarningConfiguredAsError);
	}

}