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

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.inject.internal.util.Lists;
import com.google.inject.internal.util.Maps;

import static java.lang.String.format;

public class GitFileUtil {

	private static final Log log = new SystemStreamLog();
	private static final String GIT_PATH = "mvn.git.path";
	private static final String GIT_BASE_COMMIT = "mvn.git.base.commit";

	private static final AtomicBoolean diffCode = new AtomicBoolean(false);
	private static final Map<String, ClassFileInfo> diffCodeMap = Maps
			.newHashMap();

	public static Map<String, ClassFileInfo> getDiffCodeMap() {
		return diffCodeMap;
	}

	public static boolean inited() {
		return diffCode.get();
	}

	public static synchronized void initDiffCode(MavenProject project) {
		if (diffCode.get()) {
			return;
		}
		if (diffCode.compareAndSet(false, true)) {
			String gitPath = project.getProperties().getProperty(GIT_PATH,
					"git");
			String baseDir = project.getBasedir().getAbsolutePath();
			if (project.getParent() != null) {
				baseDir = project.getParent().getBasedir().getAbsolutePath();
			}
			log.info(format("GitFileUtil baseDir='%s' ", baseDir));
			String baseCommitId = project.getProperties()
					.getProperty(GIT_BASE_COMMIT);
			log.info(format("GitFileUtil baseCommitId='%s' ", baseCommitId));
			String currentCommitId = getHeadCommitId(gitPath, baseDir);
			log.info(format("GitFileUtil currentCommitId='%s' ",
					currentCommitId));
			// 获取变更的java文件
			List<ClassFileInfo> fileList = getChangeFileList(gitPath, baseDir,
					baseCommitId, currentCommitId);
			for (ClassFileInfo info : fileList) {
				getChangeLineNumber(info, gitPath, baseDir, baseCommitId,
						currentCommitId);
				diffCodeMap.put(info.getClassFullName(), info);
			}
		}
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
			log.info(format("getChangeFileList '%s' ", cmd));

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

	private static void getChangeLineNumber(ClassFileInfo info, String gitPath,
			String baseDir, String baseCommitId, String currentCommitId) {
		BufferedReader buffer = null;
		try {
			String cmd = gitPath + " diff -U100000 --cc -W  " + baseCommitId
					+ " " + currentCommitId + " " + info.getFileFullPath();
			log.info(format("getChangeLineNumber '%s' ", cmd));

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
		MavenProject project = new MavenProject();
		project.setFile(
				new File("/Users/caojingui/IdeaProjects/vod/vod-common"));
		Properties properties = new Properties();
		properties.put(GIT_BASE_COMMIT,
				"38430107271377954e167d8ab56ae1bd513ad110");
		project.getModel().setProperties(properties);

		initDiffCode(project);
		Map<String, ClassFileInfo> map = getDiffCodeMap();

		System.out.println(map);
	}
}
