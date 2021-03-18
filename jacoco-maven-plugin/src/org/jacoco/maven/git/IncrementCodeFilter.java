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

import org.apache.maven.project.MavenProject;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.ILine;
import org.jacoco.core.analysis.IMethodCoverage;
import org.jacoco.core.analysis.IPackageCoverage;
import org.jacoco.core.analysis.ISourceFileCoverage;
import org.jacoco.core.analysis.ISourceNode;
import org.jacoco.core.internal.analysis.CounterImpl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.google.inject.internal.util.Lists;
import com.google.inject.internal.util.Maps;

public class IncrementCodeFilter {

	public static void filterIncrementCode(MavenProject project,
			IBundleCoverage bundle) {
		String gitPath = project.getProperties().getProperty("mvn.git.path",
				"git");
		String baseDir = project.getBasedir().getAbsolutePath();
		String baseCommitId = project.getProperties()
				.getProperty("mvn.git.base.commit");
		String currentCommitId = getHeadCommitId(gitPath, baseDir);
		// 获取变更的java文件
		List<ClassFileInfo> fileList = getChangeFileList(gitPath, baseDir,
				baseCommitId, currentCommitId);
		Map<String, ClassFileInfo> map = Maps.newHashMap();
		for (ClassFileInfo fileInfo : fileList) {
			// 获取java文件变更行
			getChangeLineNumber(fileInfo, gitPath, baseDir, baseCommitId,
					currentCommitId);
			map.put(fileInfo.getName(), fileInfo);
		}

		// 更新统计结果
		updateCoverage(map, bundle);

		System.out.println(project.getBasedir().getAbsolutePath());
		System.out.println(bundle);
	}

	private static void updateCoverage(Map<String, ClassFileInfo> map,
			IBundleCoverage bundleCoverage) {
		Collection<IPackageCoverage> packageCoverages = bundleCoverage
				.getPackages();
		int totalLineForBundle = 0;
		int missedLineForBundle = 0;
		for (IPackageCoverage iPackage : packageCoverages) {
			for (ISourceFileCoverage aSource : iPackage.getSourceFiles()) {
				String name = aSource.getPackageName() + "/"
						+ aSource.getName().replace(".java", "");
				ClassFileInfo info = map.get(name);
				if (info == null) {
					info = new ClassFileInfo();
				}
				int missed = updateCounterInLine(aSource, info);
				int covered = info.getChangeLines().size() - missed;
				aSource.setLineCounter(
						CounterImpl.getInstance(missed, covered));
			}
			int totalLineForPackage = 0;
			int missedLineForPackage = 0;
			for (IClassCoverage aClass : iPackage.getClasses()) {
				ClassFileInfo info = map.get(aClass.getName());
				if (info == null) {
					info = new ClassFileInfo();
				}
				totalLineForPackage += info.getChangeLines().size();
				int missed = updateCounterInLine(aClass, info);
				missedLineForPackage += missed;
				updateMethodCounter(aClass, info);
				int covered = info.getChangeLines().size() - missed;
				aClass.setLineCounter(CounterImpl.getInstance(missed, covered));
			}

			int covered = totalLineForPackage - missedLineForPackage;
			iPackage.setLineCounter(
					CounterImpl.getInstance(missedLineForPackage, covered));
			totalLineForBundle += totalLineForPackage;
			missedLineForBundle += missedLineForPackage;
		}

		int covered = totalLineForBundle - missedLineForBundle;
		bundleCoverage.setLineCounter(
				CounterImpl.getInstance(missedLineForBundle, covered));

	}

	private static void updateMethodCounter(IClassCoverage aClass,
			ClassFileInfo info) {
		for (IMethodCoverage method : aClass.getMethods()) {
			int index = method.getFirstLine();
			int end = method.getLastLine();
			int totalLine = 0;
			int missedLine = 0;
			while (index <= end) {
				ILine iLine = method.getLine(index);
				if (!info.getChangeLines().contains(index)) {
					method.replaceLine(index, null);
				} else {
					totalLine++;
					int s = iLine.getStatus();
					if (s == ICounter.NOT_COVERED
							|| s == ICounter.PARTLY_COVERED) {
						missedLine += 1;
					}
				}
				index++;
			}

			int covered = totalLine - missedLine;
			method.setLineCounter(CounterImpl.getInstance(missedLine, covered));
		}
	}

	private static int updateCounterInLine(ISourceNode aSource,
			ClassFileInfo info) {
		int missedLine = 0;
		int index = aSource.getFirstLine();
		int end = aSource.getLastLine();
		while (index <= end) {
			ILine iLine = aSource.getLine(index);
			if (!info.getChangeLines().contains(index)) {
				aSource.replaceLine(index, null);
			} else {
				int s = iLine.getStatus();
				if (s == ICounter.NOT_COVERED || s == ICounter.PARTLY_COVERED) {
					missedLine += 1;
				}
			}
			index++;
		}
		return missedLine;
	}

	private static List<ClassFileInfo> getChangeFileList(String gitPath,
			String baseDir, String baseCommitId, String currentCommitId) {
		// git diff --name-status fe93308e09004514976423787e0c1518dbb83749
		// 23061878918cf27dde83c7bb8cb9e280b6729346
		List<String> lines = Lists.newArrayList();
		List<ClassFileInfo> result = Lists.newArrayList();

		BufferedReader buffer = null;
		try {
			String cmd = gitPath + " diff --name-status " + baseCommitId + " "
					+ currentCommitId;
			Process ps = Runtime.getRuntime().exec(cmd, null,
					new File(baseDir));
			InputStream ins = ps.getInputStream();
			buffer = new BufferedReader(new InputStreamReader(ins));
			String line = buffer.readLine();
			while (line != null) {
				lines.add(line);
				line = buffer.readLine();
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			if (buffer != null) {
				try {
					buffer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		for (String line : lines) {
			// skip delete file or not java file
			if (line.startsWith("D") || !line.endsWith(".java")) {
				continue;
			}
			String[] strs = line.split("\t");
			if (strs.length < 2) {
				continue;
			}
			String jname = strs[1];
			int ix = jname.indexOf("src/main/java/");
			if (ix >= 0) {
				jname = jname.substring(ix + 14);
				String simpleName = jname;
				int lix = jname.lastIndexOf("/");
				if (lix > 0) {
					simpleName = jname.substring(lix + 1);
				}
				String fullName = jname.replaceAll("/", ".");
				ClassFileInfo info = new ClassFileInfo();
				info.setChangeType(strs[0]);
				info.setClassFullName(fullName);
				info.setSimpleName(simpleName);
				info.setFileFullPath(strs[1]);
				info.setName(jname.replace(".java", ""));
				result.add(info);
			}
		}
		return result;
	}

	// git diff -U100000 --cc -W baseCommitId currentCommitId
	// src/main/java/com/test/jacoco/demo/DateUtil.java
	private static void getChangeLineNumber(ClassFileInfo info, String gitPath,
			String baseDir, String baseCommitId, String currentCommitId) {
		BufferedReader buffer = null;
		try {
			String cmd = gitPath + " diff -U100000 --cc -W  " + baseCommitId
					+ " " + currentCommitId + " " + info.getFileFullPath();
			System.out.println(cmd);
			Process ps = Runtime.getRuntime().exec(cmd, null,
					new File(baseDir));
			InputStream ins = ps.getInputStream();
			buffer = new BufferedReader(new InputStreamReader(ins));
			String line = buffer.readLine();
			int index = 0;

			// skip diff header
			while (line != null) {
				if (line.startsWith("@@")) {
					line = buffer.readLine();
					break;
				}
				line = buffer.readLine();
			}

			while (line != null) {
				if (line.startsWith("-")) {
					line = buffer.readLine();
					continue;
				}
				index++;
				if (line.startsWith("+")) {
					info.addChangeLines(index);
				}
				line = buffer.readLine();
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			if (buffer != null) {
				try {
					buffer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * git rev-parse HEAD 获取当前commitId Implies
	 */
	private static String getHeadCommitId(String gitPath, String baseDir) {
		BufferedReader buffer = null;
		try {
			String cmd = gitPath + " rev-parse HEAD";
			Process ps = Runtime.getRuntime().exec(cmd, null,
					new File(baseDir));
			ps.waitFor();
			buffer = new BufferedReader(
					new InputStreamReader(ps.getInputStream()));
			String line;
			StringBuilder output = new StringBuilder();
			while ((line = buffer.readLine()) != null) {
				output.append(line);
			}
			return output.toString();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		} finally {
			if (buffer != null) {
				try {
					buffer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static void main(String[] args) {
		// System.out.println(getHeadCommitId("git",
		// "/Users/caojingui/IdeaProjects/vod").trim());

		System.out.println(
				getChangeFileList("git", "/Users/caojingui/IdeaProjects/vod",
						"38430107271377954e167d8ab56ae1bd513ad110",
						"f20d6067567e84e685ebae4b338849bf6a373a45"));

		ClassFileInfo fileInfo = new ClassFileInfo();
		fileInfo.setSimpleName("AntiTrashRequest.java");
		fileInfo.setClassFullName(
				"com.netease.vcloud.model.antitrash.AntiTrashRequest.java");
		fileInfo.setFileFullPath(
				"vod-common/src/main/java/com/netease/vcloud/model/antitrash/AntiTrashRequest.java");

		ClassFileInfo fileInfo1 = new ClassFileInfo();
		fileInfo1.setSimpleName("CallbackTypeEnum.java");
		fileInfo1.setClassFullName(
				"com.netease.vcloud.model.CallbackTypeEnum.java");
		fileInfo1.setFileFullPath(
				"vod-common/src/main/java/com/netease/vcloud/model/CallbackTypeEnum.java");
		getChangeLineNumber(fileInfo1, "git",
				"/Users/caojingui/IdeaProjects/vod",
				"38430107271377954e167d8ab56ae1bd513ad110",
				"f20d6067567e84e685ebae4b338849bf6a373a45");
		System.out.println(fileInfo1.getChangeLines());
	}
}
