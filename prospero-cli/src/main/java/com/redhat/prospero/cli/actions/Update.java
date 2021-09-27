/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.redhat.prospero.cli.actions;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

import com.redhat.prospero.ChannelMavenArtifactRepositoryManager;
import com.redhat.prospero.api.Artifact;
import com.redhat.prospero.api.ArtifactDependencies;
import com.redhat.prospero.api.ArtifactNotFoundException;
import com.redhat.prospero.api.Gav;
import com.redhat.prospero.api.Manifest;
import com.redhat.prospero.api.PackageInstallationException;
import com.redhat.prospero.cli.MavenFallback;
import com.redhat.prospero.api.Repository;
import com.redhat.prospero.installation.LocalInstallation;
import com.redhat.prospero.impl.repository.MavenRepository;
import com.redhat.prospero.installation.Modules;
import com.redhat.prospero.xml.ManifestXmlSupport;
import com.redhat.prospero.xml.XmlException;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.layout.FeaturePackUpdatePlan;
import org.jboss.galleon.layout.ProvisioningPlan;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.maven.MavenArtifact;

public class Update {

    private final LocalInstallation localInstallation;
    private final Repository repository;

    public Update(Repository repository, LocalInstallation localInstallation) {
        this.localInstallation = localInstallation;
        this.repository = repository;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Not enough parameters. Need to provide WFLY installation.");
            return;
        }
        final String base = args[0];
        final String artifact;
        if (args.length == 3) {
            // TODO: take multiple artifacts
            artifact = args[2];
        } else {
            artifact = null;
        }


        LocalInstallation localInstallation = new LocalInstallation(Paths.get(base));
        MavenRepository repository = new MavenRepository(localInstallation.getChannels());

        final Update update = new Update(repository, localInstallation);
        if (artifact == null) {
            update.doUpdateAll(Paths.get(base));
        } else {
            update.doUpdate(artifact.split(":")[0], artifact.split(":")[1]);
        }
    }

    public void doUpdateAll(Path installationDir) throws ArtifactNotFoundException, XmlException, PackageInstallationException, ProvisioningException, IOException {
        final List<UpdateAction> updates = new ArrayList<>();
        for (Artifact artifact : localInstallation.getManifest().getArtifacts()) {
            updates.addAll(findUpdates(artifact.getGroupId(), artifact.getArtifactId()));
        }
        final ProvisioningPlan fpUpdates = findFPUpdates(installationDir);

        if (updates.isEmpty() && fpUpdates.isEmpty()) {
            System.out.println("No updates to execute");
            return;
        }

        if (!fpUpdates.isEmpty()) {
            System.out.println("Feature pack updates:");
            for (FeaturePackUpdatePlan update : fpUpdates.getUpdates()) {
                final FeaturePackLocation oldFp = update.getInstalledLocation();
                final FeaturePackLocation newFp = update.getNewLocation();
                System.out.println(newFp.getProducerName() + "   " + oldFp.getBuild() + "  ==>  " + newFp.getBuild());
            }

        }

        if (!updates.isEmpty()) {
            System.out.println("Artefact updates found: ");
            for (UpdateAction update : updates) {
                System.out.println(update);
            }
        }

        System.out.print("Continue with update [y/n]: ");
        Scanner sc = new Scanner(System.in);
        while (true) {
            String resp = sc.nextLine();
            if (resp.equalsIgnoreCase("n")) {
                System.out.println("Update cancelled");
                return;
            } else if (resp.equalsIgnoreCase("y")) {
                System.out.println("Applying updates");
                break;
            } else {
                System.out.print("Choose [y/n]: ");
            }
        }

        final Set<Artifact> updated;
        if (!fpUpdates.isEmpty()) {
            updated = applyFpUpdates(fpUpdates, installationDir);
        } else {
            updated = Collections.emptySet();
        }

        for (UpdateAction update : updates) {
            if (!updated.contains(update.newVersion)) {
                localInstallation.updateArtifact(update.oldVersion, update.newVersion, repository.resolve(update.newVersion));
            }
        }

        ManifestXmlSupport.write(localInstallation.getManifest());
    }

    private ProvisioningPlan findFPUpdates(Path installDir) throws ProvisioningException, IOException {
        final RepositorySystem repoSystem = newRepositorySystem();

        final ChannelMavenArtifactRepositoryManager maven
                = new ChannelMavenArtifactRepositoryManager(repoSystem, MavenFallback.getDefaultRepositorySystemSession(repoSystem),
                MavenFallback.buildRepositories(), installDir.resolve("channels.json"), false, null);
        try {
            ProvisioningManager provMgr = ProvisioningManager.builder().addArtifactResolver(maven).setInstallationHome(installDir).build();
            final ProvisioningPlan updates = provMgr.getUpdates(true);
            return updates;
        } finally {
            maven.close();
        }
    }

    private Set<Artifact> applyFpUpdates(ProvisioningPlan updates, Path installDir) throws ProvisioningException, IOException {
        final RepositorySystem repoSystem = newRepositorySystem();

        final ChannelMavenArtifactRepositoryManager maven
                = new ChannelMavenArtifactRepositoryManager(repoSystem, MavenFallback.getDefaultRepositorySystemSession(repoSystem),
                MavenFallback.buildRepositories(), installDir.resolve("channels.json"), false, null);
        try {
            ProvisioningManager provMgr = ProvisioningManager.builder().addArtifactResolver(maven).setInstallationHome(installDir).build();
            provMgr.apply(updates);
        } finally {
            final Set<MavenArtifact> resolvedArtfacts = maven.getResolver().resolvedArtfacts();

            // filter out non-installed artefacts
            final Modules modules = new Modules(installDir);
            final Set<Artifact> collected = resolvedArtfacts.stream()
                    .map(Artifact::from)
                    .filter(a -> !(modules.find(a)).isEmpty())
                    .collect(Collectors.toSet());
            localInstallation.registerUpdates(collected);

            maven.close();
            return collected;
        }
    }

    public void doUpdate(String groupId, String artifactId) throws ArtifactNotFoundException, XmlException, PackageInstallationException {
        final List<UpdateAction> updates = findUpdates(groupId, artifactId);
        if (updates.isEmpty()) {
            System.out.println("No updates to execute");
            return;
        }

        System.out.println("Updates found: ");
        for (UpdateAction update : updates) {
            System.out.print(update + "\t\t\t\t\t");

            localInstallation.updateArtifact(update.oldVersion, update.newVersion, repository.resolve(update.newVersion));

            System.out.println("DONE");
        }

        ManifestXmlSupport.write(localInstallation.getManifest());
    }

    public List<UpdateAction> findUpdates(String groupId, String artifactId) throws ArtifactNotFoundException, XmlException {
        List<UpdateAction> updates = new ArrayList<>();

        Set<Gav> unresolved = new HashSet<>();

        final Manifest manifest = localInstallation.getManifest();

        final Artifact artifact = manifest.find(new Artifact(groupId, artifactId, "", ""));

        if (artifact == null) {
            throw new ArtifactNotFoundException(String.format("Artifact [%s:%s] not found", groupId, artifactId));
        }

        final Gav latestVersion = repository.findLatestVersionOf(artifact);

        if (latestVersion.compareVersion(artifact) <= 0) {
            return updates;
        }

        updates.add(new UpdateAction(artifact, (Artifact) latestVersion));

        final ArtifactDependencies artifactDependencies = repository.resolveDescriptor(latestVersion);
        if (artifactDependencies == null) {
            return updates;
        }

        for (Artifact required : artifactDependencies.getDependencies()) {
            unresolved.add(required);
        }

        for (Gav required : unresolved) {
            // check if it is installed
            final Artifact dep = manifest.find(required);
            //   if not - throw ANFE
            if (dep == null) {
                throw new ArtifactNotFoundException(String.format("Artifact [%s:%s] not found", required.getGroupId(), required.getGroupId()));

            }
            // check if it's fulfills version
            if (required.compareVersion(dep) <= 0) {
                continue;
            } else {
                // can we resolve the required version?
                final Gav depLatestVersion = repository.findLatestVersionOf(required);
                if (depLatestVersion.compareVersion(required) < 0) {
                    throw new ArtifactNotFoundException(String.format("Unable to find [%s, %s] in version >= %s", required.getGroupId(),
                            required.getArtifactId(), required.getVersion()));
                }
                // add to updates
                updates.add(new UpdateAction(dep, (Artifact) depLatestVersion));
                // process dependencies

                final ArtifactDependencies depDependencies = repository.resolveDescriptor(latestVersion);
                if (artifactDependencies != null) {
                    for (Artifact depReq : depDependencies.getDependencies()) {
                        unresolved.add(depReq);
                    }
                }
            }
        }

        return updates;
    }

    private RepositorySystem newRepositorySystem() {
        /*
         * Aether's components implement org.eclipse.aether.spi.locator.Service to ease manual wiring and using the
         * prepopulated DefaultServiceLocator, we only need to register the repository connector and transporter
         * factories.
         */
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                System.out.println(String.format("Service creation failed for %s with implementation %s",
                        type, impl));
                exception.printStackTrace();
            }
        });

        return locator.getService(RepositorySystem.class);
    }

    class UpdateAction {
        private Artifact oldVersion;
        private Artifact newVersion;

        public UpdateAction(Artifact oldVersion, Artifact newVersion) {
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
        }

        public Artifact getNewVersion() {
            return newVersion;
        }

        public Artifact getOldVersion() {
            return oldVersion;
        }

        @Override
        public String toString() {
            return String.format("Update [%s, %s]:\t\t %s ==> %s", oldVersion.getGroupId(), oldVersion.getArtifactId(),
                    oldVersion.getVersion(), newVersion.getVersion());
        }
    }
}
