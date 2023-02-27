// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package rw.remote;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.target.TargetProgressIndicator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory;
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.Nullable;
import rw.action.FastDebugWithReloadium;
import rw.audit.RwSentry;
import rw.pkg.PackageManager;
import rw.remote.sftp.SFTPClient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;


public class RemoteSdkChecker {
    private Set<Sdk> invalidSdks;
    private static final Logger LOGGER = Logger.getInstance(RemoteSdkChecker.class);

    public RemoteSdkChecker() {
        this.invalidSdks = new HashSet<>();
    }

    public boolean isOk(Sdk sdk){
        boolean ret = !this.invalidSdks.contains(sdk);
        return ret;
    }

    public void check() {
        for (Sdk sdk : ProjectJdkTable.getInstance().getSdksOfType(PythonSdkType.getInstance())) {
            if(RemoteUtils.isSdkServerRemote(sdk)) {
                this.checkRemotePackageServer(sdk);
            }
        }
    }

    private void checkRemotePackageServer(Sdk sdk) {
        Project project = this.getProject();

        if (project==null) {
            return;
        }

        LOGGER.info("Checking remote package for " + sdk.getName());

        HelpersAwareTargetEnvironmentRequest helpersAwareTargetRequest =
        PythonInterpreterTargetEnvironmentFactory.findPythonTargetInterpreter(sdk, project);

        try {
            TargetEnvironment targetEnvironment;
            try {
                targetEnvironment = helpersAwareTargetRequest.getTargetEnvironmentRequest().prepareEnvironment(TargetProgressIndicator.EMPTY);
            }
            catch (ExecutionException ignored) {
                LOGGER.info("Can't connect");
                return;
            }

            Field connectionField = targetEnvironment.getClass().getDeclaredField("connection");
            connectionField.setAccessible(true);

            Object connection = connectionField.get(targetEnvironment);

            Field sshjBackendField = connection.getClass().getDeclaredField("sshjBackend");
            sshjBackendField.setAccessible(true);

            Object ssh = sshjBackendField.get(connection);
            Method newSFTPClient = ssh.getClass().getMethod("newSFTPClient");
            newSFTPClient.setAccessible(true);

            SFTPClient sftp = new SFTPClient(newSFTPClient.invoke(ssh));

            PackageManager packageManager = new PackageManager(new RemoteFileSystem(sftp), new RemoteMachine(targetEnvironment, sftp));
            if(packageManager.shouldInstall()) {
                packageManager.run(new PackageManager.Listener() {
                    @Override
                    public void started() {
                        invalidSdks.add(sdk);
                    }

                    @Override
                    public void success() {
                        invalidSdks.remove(sdk);
                    }

                    @Override
                    public void fail(Exception exception) {
                        invalidSdks.add(sdk);
                    }
                });
            }
            else{
                this.invalidSdks.remove(sdk);
            }
        } catch (Exception e) {
            RwSentry.get().captureException(e, true);
        }
    }

    @Nullable private Project getProject() {
        Project[] projects = ProjectManager.getInstance().getOpenProjects();

        if(projects.length == 0) {
            return null;
        }

        Project ret = projects[0];
        return ret;
    }
}
