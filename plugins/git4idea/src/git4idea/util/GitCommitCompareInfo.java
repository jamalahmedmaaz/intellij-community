/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.changes.Change;
import git4idea.history.browser.GitHeavyCommit;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Kirill Likhodedov
 */
public class GitCommitCompareInfo {
  
  private static final Logger LOG = Logger.getInstance(GitCommitCompareInfo.class);
  
  private final Map<GitRepository, Pair<List<GitHeavyCommit>, List<GitHeavyCommit>>> myInfo = new HashMap<GitRepository, Pair<List<GitHeavyCommit>, List<GitHeavyCommit>>>();
  private final Map<GitRepository, Collection<Change>> myTotalDiff = new HashMap<GitRepository, Collection<Change>>();

  public void put(@NotNull GitRepository repository, @NotNull Pair<List<GitHeavyCommit>, List<GitHeavyCommit>> commits) {
    myInfo.put(repository, commits);
  }

  public void put(@NotNull GitRepository repository, @NotNull Collection<Change> totalDiff) {
    myTotalDiff.put(repository, totalDiff);
  }

  @NotNull
  public List<GitHeavyCommit> getHeadToBranchCommits(@NotNull GitRepository repo) {
    return getCompareInfo(repo).getFirst();
  }
  
  @NotNull
  public List<GitHeavyCommit> getBranchToHeadCommits(@NotNull GitRepository repo) {
    return getCompareInfo(repo).getSecond();
  }

  @NotNull
  private Pair<List<GitHeavyCommit>, List<GitHeavyCommit>> getCompareInfo(@NotNull GitRepository repo) {
    Pair<List<GitHeavyCommit>, List<GitHeavyCommit>> pair = myInfo.get(repo);
    if (pair == null) {
      LOG.error("Compare info not found for repository " + repo);
      return Pair.create(Collections.<GitHeavyCommit>emptyList(), Collections.<GitHeavyCommit>emptyList());
    }
    return pair;
  }

  @NotNull
  public Collection<GitRepository> getRepositories() {
    return myInfo.keySet();
  }

  public boolean isEmpty() {
    return myInfo.isEmpty();
  }

  @NotNull
  public List<Change> getTotalDiff() {
    List<Change> changes = new ArrayList<Change>();
    for (Collection<Change> changeCollection : myTotalDiff.values()) {
      changes.addAll(changeCollection);
    }
    return changes;
  }
}
