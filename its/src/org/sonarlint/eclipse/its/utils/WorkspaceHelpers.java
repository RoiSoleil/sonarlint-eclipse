/*
 * SonarLint for Eclipse ITs
 * Copyright (C) 2009-2021 SonarSource SA
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarlint.eclipse.its.utils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.Function;
import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.TextConsole;
import org.sonarlint.eclipse.its.bots.ConsoleViewBot;

public final class WorkspaceHelpers {

  public static <G> G withSonarLintConsole(SWTWorkbenchBot bot, Function<TextConsole, G> consumer) {
    new ConsoleViewBot(bot).openSonarLintConsole();
    IConsoleManager manager = ConsolePlugin.getDefault().getConsoleManager();

    IConsole[] consoles = manager.getConsoles();
    for (IConsole iConsole : consoles) {
      if ("SonarLint Console".equals(iConsole.getName())) {
        return consumer.apply((TextConsole) iConsole);
      }
    }
    throw new IllegalStateException("Unable to find the SonarLint console");
  }

  public static void cleanWorkspace(SWTWorkbenchBot bot) throws InterruptedException, CoreException {
    withSonarLintConsole(bot, c -> {
      System.out.println(c.getDocument().get());
      c.clearConsole();
      return null;
    });
    Exception cause = null;
    int i;
    for (i = 0; i < 10; i++) {
      try {
        System.gc();
        doCleanWorkspace(bot);
      } catch (InterruptedException e) {
        throw e;
      } catch (OperationCanceledException e) {
        throw e;
      } catch (Exception e) {
        cause = e;
        e.printStackTrace();
        System.out.println(i);
        Thread.sleep(6 * 1000);
        continue;
      }
      // all clear
      return;
    }
    // must be a timeout
    throw new CoreException(new Status(IStatus.ERROR, "org.sonar.ide.eclipse.ui",
      "Could not delete workspace resources (after " + i + " retries): "
        + Arrays.asList(ResourcesPlugin.getWorkspace().getRoot().getProjects()),
      cause));
  }

  private static void doCleanWorkspace(SWTWorkbenchBot bot) throws InterruptedException, CoreException, IOException {
    final IWorkspace workspace = ResourcesPlugin.getWorkspace();
    workspace.run(new IWorkspaceRunnable() {

      @Override
      public void run(IProgressMonitor monitor) throws CoreException {
        IProject[] projects = workspace.getRoot().getProjects();
        for (IProject project : projects) {
          project.delete(true, true, monitor);
        }
      }
    }, new NullProgressMonitor());

    JobHelpers.waitForJobsToComplete(bot);

    File[] files = workspace.getRoot().getLocation().toFile().listFiles();
    if (files != null) {
      for (File file : files) {
        if (!".metadata".equals(file.getName())) {
          if (file.isDirectory()) {
            FileUtils.deleteDirectory(file);
          } else {
            if (!file.delete()) {
              throw new IOException("Could not delete file " + file.getCanonicalPath());
            }
          }
        }
      }
    }
  }

  private WorkspaceHelpers() {
  }
}