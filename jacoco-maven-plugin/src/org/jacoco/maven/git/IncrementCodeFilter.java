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
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.ILine;
import org.jacoco.core.analysis.IMethodCoverage;
import org.jacoco.core.analysis.IPackageCoverage;
import org.jacoco.core.analysis.ISourceFileCoverage;
import org.jacoco.core.analysis.ISourceNode;
import org.jacoco.core.internal.analysis.CounterImpl;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public class IncrementCodeFilter {
	private static final Log log = new SystemStreamLog();

	public static void filterIncrementCode(IBundleCoverage bundle) {
		Map<String, ClassFileInfo> map = GitFileUtil.getDiffCodeMap();

		log.info("updateCoverage for " + bundle.getName());
		// 更新统计结果
		updateCoverage(map, bundle);

	}

	private static void updateCoverage(Map<String, ClassFileInfo> map,
			IBundleCoverage bundleCoverage) {
		int bundleCoveredLine = 0;
		int bundleMissedLine = 0;

		Collection<IPackageCoverage> packageCoverages = bundleCoverage
				.getPackages();
		Iterator<IPackageCoverage> packageIterator = packageCoverages
				.iterator();
		while (packageIterator.hasNext()) {
			int packageCoveredLine = 0;
			int packageMissedLine = 0;
			IPackageCoverage iPackage = packageIterator.next();
			// update class
			Iterator<IClassCoverage> classIterator = iPackage.getClasses()
					.iterator();
			while (classIterator.hasNext()) {
				IClassCoverage iClass = classIterator.next();
				String name = iClass.getName().replaceAll("/", ".") + ".java";
				if (!map.containsKey(name)) {
					classIterator.remove();
					continue;
				}
				ClassFileInfo info = map.get(name);
				CounterImpl counter = updateCounterInLine(iClass, info);
				updateMethodCounter(iClass, info);
				iClass.setLineCounter(counter);
			}

			// update source
			Iterator<ISourceFileCoverage> sourceIterator = iPackage
					.getSourceFiles().iterator();
			while (sourceIterator.hasNext()) {
				ISourceFileCoverage iSource = sourceIterator.next();
				String name = iSource.getPackageName().replaceAll("/", ".")
						+ "." + iSource.getName();
				if (!map.containsKey(name)) {
					sourceIterator.remove();
					continue;
				}
				ClassFileInfo info = map.get(name);
				CounterImpl counter = updateCounterInLine(iSource, info);
				iSource.setLineCounter(counter);

				packageCoveredLine += counter.getCoveredCount();
				packageMissedLine += counter.getMissedCount();
			}

			// remove empty package
			if (iPackage.getSourceFiles().size() == 0) {
				packageIterator.remove();
			}

			iPackage.setLineCounter(CounterImpl.getInstance(packageMissedLine,
					packageCoveredLine));

			bundleCoveredLine += packageCoveredLine;
			bundleMissedLine += packageMissedLine;
		}

		bundleCoverage.setLineCounter(
				CounterImpl.getInstance(bundleMissedLine, bundleCoveredLine));

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

	private static CounterImpl updateCounterInLine(ISourceNode aSource,
			ClassFileInfo info) {
		int missedLine = 0;
		int coveredLine = 0;
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
				} else if (s == ICounter.EMPTY) {
					// ignore
				} else {
					coveredLine += 1;
				}
			}
			index++;
		}
		return CounterImpl.getInstance(missedLine, coveredLine);
	}

}
