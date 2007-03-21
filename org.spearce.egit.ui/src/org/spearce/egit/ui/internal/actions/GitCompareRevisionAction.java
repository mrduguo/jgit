/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Robin Rosenberg - Git interface
 *******************************************************************************/
package org.spearce.egit.ui.internal.actions;

import org.eclipse.compare.CompareEditorInput;
import org.eclipse.compare.CompareUI;
import org.eclipse.compare.ITypedElement;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.osgi.util.NLS;
import org.eclipse.team.core.history.IFileRevision;
import org.eclipse.team.core.history.provider.FileRevision;
import org.eclipse.team.internal.core.history.LocalFileRevision;
import org.eclipse.team.internal.ui.TeamUIMessages;
import org.eclipse.team.internal.ui.history.AbstractHistoryCategory;
import org.eclipse.team.internal.ui.synchronize.LocalResourceTypedElement;
import org.eclipse.team.ui.history.HistoryPage;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IReusableEditor;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.actions.BaseSelectionListenerAction;
import org.spearce.egit.core.GitWorkspaceFileRevision;
import org.spearce.egit.ui.internal.GitCompareFileRevisionEditorInput;
import org.spearce.egit.ui.internal.GitResourceNode;

/**
 * Action to invoke a Git based compare on selected revivsions in the history window.
 *
 * This class i almost a verbatim copy of @{link org.eclipse.team.internal.ui.actions.CompareRevisionAction}
 * since it could not be subclassed to invoke the EGit methods.
 */
public class GitCompareRevisionAction extends BaseSelectionListenerAction {

	HistoryPage page;
	IStructuredSelection selection;
	IFileRevision currentFileRevision;

	public GitCompareRevisionAction(String text) {
		super(text);
	}

	public GitCompareRevisionAction() {
		this(TeamUIMessages.LocalHistoryPage_CompareAction);
	}

	public void run() {
		IStructuredSelection structSel = selection;
		Object[] objArray = structSel.toArray();

		IFileRevision file1 = null;
		IFileRevision file2 = null;

		switch (structSel.size()){
			case 1:
				file1 = getCurrentFileRevision();
				Object tempRevision = objArray[0];
				if (tempRevision instanceof IFileRevision)
					file2 = (IFileRevision) tempRevision;
				else
					return;
			break;

			case 2:
				Object tempRevision2 = objArray[0];
				Object tempRevision3 = objArray[1];

				if (tempRevision2 instanceof IFileRevision &&
					tempRevision3 instanceof IFileRevision){
					file1 = (IFileRevision) objArray[0];
					file2 = (IFileRevision) objArray[1];
				} else
					return;
			break;
		}

		if (file1 == null || file2 == null ||
		   !file1.exists() || !file2.exists()){
			MessageDialog.openError(page.getSite().getShell(),
					TeamUIMessages.OpenRevisionAction_DeletedRevTitle,
					TeamUIMessages.CompareRevisionAction_DeleteCompareMessage);
			return;
		}

		IResource resource = getResource(file2);
		if (resource != null) {
			IFileRevision temp = file1;
			file1 = file2;
			file2 = temp;
		}
		ITypedElement left;
		resource = getResource(file1);
		if (resource != null) {
			left = new LocalResourceTypedElement(resource);
		} else {
			left = new GitResourceNode(file1);
		}
		ITypedElement right = new GitResourceNode(file2);

	    openInCompare(left, right);
	}

	private void openInCompare(ITypedElement left, ITypedElement right) {
		CompareEditorInput input = createCompareEditorInput(left, right, page.getSite().getPage());
		IWorkbenchPage workBenchPage = page.getSite().getPage();
		IEditorPart editor = findReusableCompareEditor(workBenchPage);
		if (editor != null) {
			IEditorInput otherInput = editor.getEditorInput();
			if (otherInput.equals(input)) {
				// simply provide focus to editor
				workBenchPage.activate(editor);
			} else {
				// if editor is currently not open on that input either re-use
				// existing
				CompareUI.reuseCompareEditor(input, (IReusableEditor) editor);
				workBenchPage.activate(editor);
			}
		} else {
			CompareUI.openCompareEditor(input);
		}
	}

	protected GitCompareFileRevisionEditorInput createCompareEditorInput(
			ITypedElement left, ITypedElement right, IWorkbenchPage page) {
		return new GitCompareFileRevisionEditorInput(left, right, page);
	}

	private IResource getResource(IFileRevision revision) {
		if (revision instanceof GitWorkspaceFileRevision) {
			GitWorkspaceFileRevision local = (GitWorkspaceFileRevision) revision;
			return local.getResource();
		}
		return null;
	}

	private IFileRevision getCurrentFileRevision() {
		return currentFileRevision;
	}

	public void setCurrentFileRevision(IFileRevision fileRevision){
		this.currentFileRevision = fileRevision;
	}

	/**
	 * Returns an editor that can be re-used. An open compare editor that
	 * has un-saved changes cannot be re-used.
	 * @param page
	 * @return an EditorPart or <code>null</code> if none can be found
	 */
	public static IEditorPart findReusableCompareEditor(IWorkbenchPage page) {
		IEditorReference[] editorRefs = page.getEditorReferences();
		for (int i = 0; i < editorRefs.length; i++) {
			IEditorPart part = editorRefs[i].getEditor(false);
			if(part != null
					&& (part.getEditorInput() instanceof GitCompareFileRevisionEditorInput)
					&& part instanceof IReusableEditor) {
				if(! part.isDirty()) {
					return part;
				}
			}
		}
		return null;
	}

	protected boolean updateSelection(IStructuredSelection selection) {
		this.selection = selection;
		if (selection.size() == 1){
			Object el = selection.getFirstElement();
			if (el instanceof LocalFileRevision)
				this.setText(TeamUIMessages.CompareRevisionAction_Local);
			else if (el instanceof FileRevision){
				FileRevision tempFileRevision = (FileRevision) el;
				this.setText(NLS.bind(TeamUIMessages.CompareRevisionAction_Revision, new String[]{tempFileRevision.getContentIdentifier()}));
			}
			else
				this.setText(TeamUIMessages.CompareRevisionAction_CompareWithCurrent);
			return shouldShow();
		}
		else if (selection.size() == 2){
			this.setText(TeamUIMessages.CompareRevisionAction_CompareWithOther+" NG");
			return shouldShow();
		}

		return false;
	}

	public void setPage(HistoryPage page) {
		this.page = page;
	}

	private boolean shouldShow() {
		IStructuredSelection structSel = selection;
		Object[] objArray = structSel.toArray();

		if (objArray.length == 0)
			return false;

		// Comparing the workspace revision with itself is just stupid
		if (objArray.length == 1 && objArray[0] instanceof GitWorkspaceFileRevision)
			return false;

		for (int i = 0; i < objArray.length; i++) {

			// Don't bother showing if this a category
			if (objArray[i] instanceof AbstractHistoryCategory)
				return false;

			IFileRevision revision = (IFileRevision) objArray[i];
			//check to see if any of the selected revisions are deleted revisions
			if (revision != null && !revision.exists())
				return false;
		}

		return true;
	}

}