/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.prospero;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;

import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.prospero.api.exceptions.ArtifactPromoteException;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.NoChannelException;
import org.wildfly.prospero.api.exceptions.ProvisioningRuntimeException;

@MessageBundle(projectCode = "PRSP")
public interface Messages {

    public Messages MESSAGES = org.jboss.logging.Messages.getBundle(Messages.class);

    @Message("Given path '%s' is a regular file. An empty directory or a non-existing path must be given.")
    IllegalArgumentException dirMustBeDirectory(Path path);

    @Message("Can't install into a non empty directory '%s'. Use `update` command if you want to modify existing installation.")
    IllegalArgumentException cannotInstallIntoNonEmptyDirectory(Path path);

    @Message("Installation dir '%s' doesn't exist")
    IllegalArgumentException installationDirDoesNotExist(Path path);

    @Message("Installation dir '%s' already exists")
    ProvisioningException installationDirAlreadyExists(Path installDir);

    @Message("Artifact [%s:%s] not found")
    ArtifactResolutionException artifactNotFound(String g, String a, @Cause UnresolvedMavenArtifactException e);

    @Message("At least one channel reference must be given.")
    NoChannelException noChannelReference();

    @Message("[%s] doesn't specify any channels and no additional channels are selected.")
    NoChannelException fplDefinitionDoesntContainChannel(String fpl);

    @Message("Channel '%s' is already present.")
    MetadataException channelExists(String channelName);

    @Message("Channel with name [%s] cannot be found.")
    MetadataException channelNotFound(String channelName);

    @Message("Channel name cannot be empty.")
    MetadataException emptyChannelName();

    @Message("Promoting artifacts to %s:")
    String promotingArtifacts(URL targetRepository);

    @Message("Provided FPL has invalid format `%s`.")
    String invalidFpl(String fplText);

    @Message("Unable to parse server configuration at '%s'")
    MetadataException unableToParseConfiguration(Path path, @Cause Throwable e);

    @Message("Unable to parse server configuration at '%s'")
    MetadataException unableToParseConfigurationUri(URI uri, @Cause Throwable e);

    @Message("Unable to save server configuration at '%s'")
    MetadataException unableToSaveConfiguration(Path path, @Cause Exception e);

    @Message("Unable to close the update store.")
    MetadataException unableToCloseStore(@Cause Exception e);

    @Message("Path `%s` does not contain a server installation provisioned by prospero.")
    IllegalArgumentException invalidInstallationDir(Path path);

    @Message("Unable to create history store at [%s]")
    MetadataException unableToCreateHistoryStorage(Path path, @Cause Exception e);

    @Message("Unable to access history store at [%s]")
    MetadataException unableToAccessHistoryStorage(Path path, @Cause Exception e);

    @Message("Unable to read file at [%s]")
    MetadataException unableToReadFile(Path path, @Cause Exception e);

    // provisioning errors
    @Message("Unable to create temporary cache for provisioning cache folder.")
    ProvisioningException unableToCreateCache(@Cause Exception e);

    @Message("Failed to initiate maven repository system")
    ProvisioningRuntimeException failedToInitMaven(@Cause Throwable exception);

    @Message("Invalide URL [%s]")
    IllegalArgumentException invalidUrl(String text, @Cause Exception e);

    @Message("Incomplete configuration: either a predefined fpl (%s) or a provisionConfigFile must be given.")
    IllegalArgumentException incompleteProvisioningConfiguration(String availableFpls);

    @Message("Provided metadata bundle [%s] is missing one or more entries")
    IllegalArgumentException incompleteMetadataBundle(Path path);

    @Message("Found unexpected artifact [%s]")
    ProvisioningRuntimeException unexpectedArtifact(String gav);

    @Message("Unable to resolve [%s]")
    MavenUniverseException unableToResolve(String gav);

    @Message("File already exists [%s]")
    IllegalArgumentException fileAlreadyExists(Path path);

    @Message("Promoting to non-file repositories is not currently supported")
    IllegalArgumentException unsupportedPromotionTarget();

    @Message("Wrong format of custom channel version [%s]")
    IllegalArgumentException wrongVersionFormat(String baseVersion);

    @Message("Custom channel version exceeded limit [%s]")
    IllegalArgumentException versionLimitExceeded(String baseVersion);

    @Message("Cannot create bundle without artifacts.")
    IllegalArgumentException noArtifactsToPackage();

    @Message("Channel reference has to use Maven GA.")
    IllegalArgumentException nonMavenChannelRef();

    @Message("Unable to promote artifacts to [%s].")
    ArtifactPromoteException unableToPromote(URL target, @Cause Exception e);

    @Message("Unable to parse the customization bundle [%s].")
    ArtifactPromoteException unableToParseCustomizationBundle(Path path, @Cause Exception e);
}
