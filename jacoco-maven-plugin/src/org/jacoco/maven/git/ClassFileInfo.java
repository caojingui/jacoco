/*******************************************************************************
 * Copyright (c) 2009, 2020 Mountainminds GmbH & Co. KG and Contributors
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Evgeny Mandrikov - initial API and implementation
 *    Kyle Lieber - implementation of CheckMojo
 *
 *******************************************************************************/
package org.jacoco.maven.git;

import java.util.Set;

import com.google.inject.internal.util.Sets;

public class ClassFileInfo {

	/* DateUtil.java */
	private String simpleName;
	/* com.demo.package.DateUtil.java */
	private String classFullName;
	/* src/main/java/com/demo/package/DateUtil.java */
	private String fileFullPath;
	/* com/demo/package/DateUtil */
	private String name;
	private String changeType;
	/* change line numbers */
	private final Set<Integer> changeLines = Sets.newHashSet();

	public String getSimpleName() {
		return simpleName;
	}

	public void setSimpleName(String simpleName) {
		this.simpleName = simpleName;
	}

	public String getClassFullName() {
		return classFullName;
	}

	public void setClassFullName(String classFullName) {
		this.classFullName = classFullName;
	}

	public String getFileFullPath() {
		return fileFullPath;
	}

	public void setFileFullPath(String fileFullPath) {
		this.fileFullPath = fileFullPath;
	}

	public Set<Integer> getChangeLines() {
		return changeLines;
	}

	public void addChangeLines(Integer lineNumber) {
		this.changeLines.add(lineNumber);
	}

	public String getChangeType() {
		return changeType;
	}

	public void setChangeType(String changeType) {
		this.changeType = changeType;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return "ClassFileInfo{" + "simpleName='" + simpleName + '\''
				+ ", classFullName='" + classFullName + '\''
				+ ", fileFullPath='" + fileFullPath + '\'' + ", name='" + name
				+ '\'' + ", changeType='" + changeType + '\'' + ", changeLines="
				+ changeLines + '}';
	}
}
