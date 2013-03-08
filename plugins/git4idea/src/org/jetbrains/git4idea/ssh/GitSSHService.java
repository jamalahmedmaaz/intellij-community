/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.git4idea.ssh;

import com.intellij.ide.XmlRpcServer;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.io.FileUtilRt;
import com.trilead.ssh2.KnownHosts;
import git4idea.commands.GitSSHGUIHandler;
import gnu.trove.THashMap;
import org.apache.commons.codec.DecoderException;
import org.apache.xmlrpc.XmlRpcClientLite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.git4idea.util.ScriptGenerator;
import org.jetbrains.ide.WebServerManager;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.Vector;

/**
 * The provider of SSH scripts for the Git
 */
public class GitSSHService {

  /**
   * random number generator to use
   */
  private static final Random RANDOM = new Random();
  /**
   * Path to the generated script
   */
  private File myScriptPath;
  /**
   * Registered handlers
   */
  private final THashMap<Integer, GitSSHGUIHandler> handlers = new THashMap<Integer, GitSSHGUIHandler>();

  @NotNull
  public static GitSSHService getInstance() {
    return ServiceManager.getService(GitSSHService.class);
  }

  /**
   * @return the port number for XML RCP
   */
  public int getXmlRcpPort() {
    return WebServerManager.getInstance().waitForStart().getPort();
  }

  /**
   * Get file to the script service
   *
   * @return path to the script
   * @throws IOException if script cannot be generated
   */
  @NotNull
  public synchronized File getScriptPath() throws IOException {
    if (myScriptPath == null || !myScriptPath.exists()) {
      ScriptGenerator generator = new ScriptGenerator(GitSSHHandler.GIT_SSH_PREFIX, SSHMain.class, getTempDir());
      generator.addClasses(XmlRpcClientLite.class, DecoderException.class, KnownHosts.class, FileUtilRt.class);
      generator.addResource(SSHMainBundle.class, "/org/jetbrains/git4idea/ssh/SSHMainBundle.properties");
      myScriptPath = generator.generate();
    }
    return myScriptPath;
  }

  /**
   * @return the temporary directory to use or null if the default directory might be used
   */
  @SuppressWarnings({"MethodMayBeStatic"})
  @Nullable
  protected File getTempDir() {
    return null;
  }

  /**
   * Register handler. Note that handlers must be unregistered using {@link #unregisterHandler(int)}.
   *
   * @param handler a handler to register
   * @return an identifier to pass to the environment variable
   */
  public synchronized int registerHandler(@NotNull GitSSHGUIHandler handler) {
    XmlRpcServer xmlRpcServer = XmlRpcServer.SERVICE.getInstance();
    if (!xmlRpcServer.hasHandler(GitSSHHandler.HANDLER_NAME)) {
      xmlRpcServer.addHandler(GitSSHHandler.HANDLER_NAME, new InternalRequestHandler());
    }

    while (true) {
      int candidate = RANDOM.nextInt();
      if (candidate == Integer.MIN_VALUE) {
        continue;
      }
      candidate = Math.abs(candidate);
      if (handlers.containsKey(candidate)) {
        continue;
      }
      handlers.put(candidate, handler);
      return candidate;
    }
  }

  /**
   * Get handler for the key
   *
   * @param key the key to use
   * @return the registered handler
   */
  @NotNull
  private synchronized GitSSHGUIHandler getHandler(int key) {
    GitSSHGUIHandler rc = handlers.get(key);
    if (rc == null) {
      throw new IllegalStateException("No handler for the key " + key);
    }
    return rc;
  }

  /**
   * Unregister handler by the key
   *
   * @param key the key to unregister
   */
  public synchronized void unregisterHandler(int key) {
    if (handlers.remove(key) == null) {
      throw new IllegalArgumentException("The handler " + key + " is not registered");
    }
  }

  /**
   * Internal handler implementation class, do not use it.
   */
  public class InternalRequestHandler implements GitSSHHandler {
    /**
     * {@inheritDoc}
     */
    public boolean verifyServerHostKey(final int handler,
                                       final String hostname,
                                       final int port,
                                       final String serverHostKeyAlgorithm,
                                       final String serverHostKey,
                                       final boolean isNew) {
      return getHandler(handler).verifyServerHostKey(hostname, port, serverHostKeyAlgorithm, serverHostKey, isNew);
    }

    /**
     * {@inheritDoc}
     */
    public String askPassphrase(final int handler,
                                final String username,
                                final String keyPath,
                                final boolean resetPassword,
                                final String lastError) {
      return adjustNull(getHandler(handler).askPassphrase(username, keyPath, resetPassword, lastError));
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings({"UseOfObsoleteCollectionType"})
    public Vector<String> replyToChallenge(final int handlerNo,
                                           final String username,
                                           final String name,
                                           final String instruction,
                                           final int numPrompts,
                                           final Vector<String> prompt,
                                           final Vector<Boolean> echo,
                                           final String lastError) {
      return adjustNull(getHandler(handlerNo).replyToChallenge(username, name, instruction, numPrompts, prompt, echo, lastError));
    }

    /**
     * {@inheritDoc}
     */
    public String askPassword(final int handlerNo, final String username, final boolean resetPassword, final String lastError) {
      return adjustNull(getHandler(handlerNo).askPassword(username, resetPassword, lastError));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String setLastSuccessful(int handlerNo, String userName, String method, String error) {
      getHandler(handlerNo).setLastSuccessful(userName, method, error);
      return "";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLastSuccessful(int handlerNo, String userName) {
      return getHandler(handlerNo).getLastSuccessful(userName);
    }

    /**
     * Adjust null value ({@code "-"} if null, {@code "+"+s) if non-null)
     *
     * @param s a value to adjust
     * @return adjusted string
     */
    private String adjustNull(final String s) {
      return s == null ? "-" : "+" + s;
    }

    /**
     * Adjust null value (returns empty array)
     *
     * @param s if null return empty array
     * @return s if not null, empty array otherwise
     */
    @SuppressWarnings({"UseOfObsoleteCollectionType"})
    private <T> Vector<T> adjustNull(final Vector<T> s) {
      return s == null ? new Vector<T>() : s;
    }
  }
}
