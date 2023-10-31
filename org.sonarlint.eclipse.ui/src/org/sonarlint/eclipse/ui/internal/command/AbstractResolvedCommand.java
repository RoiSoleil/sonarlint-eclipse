/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2023 SonarSource SA
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
package org.sonarlint.eclipse.ui.internal.command;

import java.util.Map;
import java.util.Optional;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.menus.UIElement;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarlint.eclipse.core.internal.engine.connected.ResolvedBinding;
import org.sonarlint.eclipse.core.internal.markers.MarkerUtils;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import org.sonarlint.eclipse.ui.internal.SonarLintImages;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

/** Shared logic for all commands on (to be) resolved issues */
public abstract class AbstractResolvedCommand extends AbstractIssueCommand implements IElementUpdater {
  protected IWorkbenchWindow currentWindow;
  
  @Override
  public void updateElement(UIElement element, Map parameters) {
    var window = element.getServiceLocator().getService(IWorkbenchWindow.class);
    if (window == null) {
      return;
    }
    
    /** When opening the context menu on no selected issue marker, this command should not be shown */
    var marker = getSelectedMarker((IStructuredSelection) window.getSelectionService().getSelection());
    if (marker != null) {
      var binding = getBinding(marker);
      if (binding.isPresent()) {
        element.setIcon(binding.get().getEngineFacade().isSonarCloud()
          ? SonarLintImages.SONARCLOUD_16
            : SonarLintImages.SONARQUBE_16);
      }
    }
  }
  
  /** Check for issue binding: Either SonarQube or SonarCloud */
  protected static Optional<ResolvedBinding> getBinding(IMarker marker) {
    var project = Adapters.adapt(marker.getResource().getProject(), ISonarLintProject.class);
    return SonarLintCorePlugin.getServersManager().resolveBinding(project);
  }
  
  /** Get the issue key (e.g. server issue key / UUID) */
  @Nullable
  protected String getIssueKey(IMarker marker) {
    var serverIssue = marker.getAttribute(MarkerUtils.SONAR_MARKER_SERVER_ISSUE_KEY_ATTR, null);
    return serverIssue != null
      ? serverIssue
        : marker.getAttribute(MarkerUtils.SONAR_MARKER_TRACKED_ISSUE_ID_ATTR, null);
  }
  
  /** Try to get the marker type (normal issue or a taint, different behavior) */
  @Nullable
  protected String tryGetMarkerType(IMarker marker, String errorTitle) {
    String markerType = null;
    try {
      markerType = marker.getType();
    } catch (CoreException err) {
      SonarLintLogger.get().error("Error getting marker type", err);
      currentWindow.getShell().getDisplay()
        .asyncExec(() -> MessageDialog.openError(currentWindow.getShell(), errorTitle, err.getMessage()));
    }
    return markerType;
  }
}