package com.intellij.openapi.vcs.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class AnnotatePreviousRevisionAction extends AnnotateRevisionAction {
  @Nullable private final List<VcsFileRevision> myRevisions;

  public AnnotatePreviousRevisionAction(@NotNull UpToDateLineNumberProvider getUpToDateLineNumber,
                                        @NotNull FileAnnotation annotation, @NotNull AbstractVcs vcs) {
    super("Annotate Previous Revision", "Annotate successor of selected revision in new tab", AllIcons.Actions.Annotate,
          getUpToDateLineNumber, annotation, vcs);
    List<VcsFileRevision> revisions = annotation.getRevisions();
    if (revisions == null) {
      myRevisions = null;
      return;
    }

    Map<VcsRevisionNumber, VcsFileRevision> map = new HashMap<VcsRevisionNumber, VcsFileRevision>();
    for (int i = 0; i < revisions.size(); i++) {
      VcsFileRevision revision = revisions.get(i);
      VcsFileRevision previousRevision = i + 1 < revisions.size() ? revisions.get(i + 1) : null;
      map.put(revision.getRevisionNumber(), previousRevision);
    }

    myRevisions = new ArrayList<VcsFileRevision>(annotation.getLineCount());
    for (int i = 0; i < annotation.getLineCount(); i++) {
      myRevisions.add(map.get(annotation.getLineRevisionNumber(i)));
    }
  }

  @Override
  @Nullable
  public List<VcsFileRevision> getRevisions() {
    return myRevisions;
  }
}
