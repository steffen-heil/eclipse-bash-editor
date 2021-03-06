/*
 * Copyright 2019 Albert Tregnaghi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 */
package de.jcup.basheditor.debug.launch;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import de.jcup.basheditor.BashEditorActivator;
import de.jcup.basheditor.debug.BashDebugConsole;
import de.jcup.basheditor.preferences.BashEditorPreferences;
import de.jcup.eclipse.commons.ui.EclipseUtil;

/* 
 * Working example output:
 * @formatter:off
 * 
 * win: cmd.exe /C "bash --login -c 'echo -e "color are \033[0;31mRED\033[0m - or not...";read -p "Press enter to continue"'"
 * lin: bash -c x-terminal-emulator -e bash --login -c 'cd /tmp;./terminallaunch3919760667320099616.sh -a 1 -b 2;_exit_status=$?;echo "Exit code=$_exit_status";if [ $_exit_status -ne 0 ]; then read -p "Unexpected exit code:$_exit_status , press enter to continue";fi;read -p "Press enter to continue"' &
 * 
 * diff linux: win:
 * win: cmd.exe /C "$callerSnippet"
 * lin: bash -c x-terminal-emulator -e $callerSnippet
 * 
 * 
 * @formatter:on
 */
public class TerminalLauncher {
    
    public Process execute(File file, String params, String terminalCommand, String starterCommand, Map<String, String> environment) {
        /* setup context */
        if (file==null) {
            EclipseUtil.logError("File was null", null, BashEditorActivator.getDefault());
            return null;
        }
        TerminalLaunchContext context = createContext(file, params, terminalCommand,starterCommand,environment);
        return execute(context);
        
    }

    
    public Process execute(TerminalLaunchContext context) {
        if (context==null) {
            EclipseUtil.logError("Context was null", null, BashEditorActivator.getDefault());
            return null;
        }
        if (context.file==null) {
            EclipseUtil.logError("File was null", null, BashEditorActivator.getDefault());
            return null;
        }
        /* execute in own thread */
        LaunchRunnable launchRunnable = new LaunchRunnable(context.getWorkingDirFile(), context.commands, context.environment);
        Thread thread = new Thread(launchRunnable);
        thread.setName("Launch in terminal:" + context.file.getName());
        thread.start();
        
        return launchRunnable.waitForStartedProcessOrNull();
    }
	private TerminalLaunchContext createContext(File file, String params, String terminalCommand, String starterCommand, Map<String, String> environment) {
		/* @formatter:off */
	    return TerminalLaunchContextBuilder.builder().
	    		file(file).
	    		starterCommand(starterCommand).
	    		terminalCommand(terminalCommand).
	    		params(params).
	    		waitingAlways(isWaitingAlways()).
	    		waitingOnErrors(isWaitingOnErrors()).
	    		environment(environment).
	    		build();
	    /* @formatter:on */
	}
	

    protected boolean isWaitingOnErrors() {
		return BashEditorPreferences.getInstance().isLaunchedTerminalWaitingOnErrors();
	}

	protected boolean isWaitingAlways() {
		return BashEditorPreferences.getInstance().isLaunchedTerminalAlwaysWaiting();
	}

	protected void logExectionError(IOException e) {
		EclipseUtil.logError("Cannot start real runtime process, fall back to dummy", e, BashEditorActivator.getDefault());
	}

	protected void logExecutedCommand(LaunchRunnable runnable) {
		if (BashEditorPreferences.getInstance().isShowMetaInfoInDebugConsoleEnabled()) {
			BashDebugConsole.println(">>> Launch Terminal:");
			BashDebugConsole.println("    " + runnable.createCommandString());
		}
	}

	

	protected class LaunchRunnable implements Runnable {

		private File workingDir;
		private List<String> commands;
		Process p;
		private boolean startedProcess;
		private Map<String, String> environment;

		public LaunchRunnable(File workingDir, List<String> commands, Map<String, String> environment) {
			this.workingDir = workingDir;
			this.commands = commands;
			this.environment=environment;
		}
		
		public Process waitForStartedProcessOrNull() {
			while (!startedProcess) {
				try {
					Thread.sleep(300);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			return p;
		}

		@Override
		public void run() {
		    if (commands==null || commands.isEmpty()) {
		    	startedProcess=true;
		    	return;
		    }
			ProcessBuilder pb = new ProcessBuilder(commands);
			pb.directory(workingDir);
			pb.environment().putAll(this.environment);
			logExecutedCommand(this);
			try {
				pb.inheritIO();
				p = pb.start();
				startedProcess=true;
				int result = p.waitFor();
				if (BashEditorPreferences.getInstance().isShowMetaInfoInDebugConsoleEnabled()) {
					BashDebugConsole.println("Bash process terminated with exit code:" + result);
				}

			} catch (IOException e) {
				logExectionError(e);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		/**
		 * Creates a string presenting the command. Interesting for showing user the
		 * expected command
		 * 
		 * @return
		 */
		String createCommandString() {
			StringBuilder sb = new StringBuilder();
			for (String command : commands) {
				sb.append(command);
				sb.append(" ");
			}
			return sb.toString().trim();
		}
	}

}