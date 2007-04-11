package org.apache.maven.archiva.repository.layout;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.commons.lang.StringUtils;
import org.apache.maven.archiva.model.ArchivaArtifact;
import org.apache.maven.archiva.model.ArtifactReference;
import org.apache.maven.archiva.model.ProjectReference;
import org.apache.maven.archiva.repository.content.ArtifactExtensionMapping;
import org.apache.maven.archiva.repository.content.LegacyArtifactExtensionMapping;

import java.util.HashMap;
import java.util.Map;

/**
 * LegacyBidirectionalRepositoryLayout - the layout mechanism for use by Maven 1.x repositories.
 *
 * @author <a href="mailto:joakim@erdfelt.com">Joakim Erdfelt</a>
 * @version $Id$
 * 
 * @plexus.component role-hint="legacy"
 */
public class LegacyBidirectionalRepositoryLayout
    implements BidirectionalRepositoryLayout
{
    private static final String PATH_SEPARATOR = "/";

    private ArtifactExtensionMapping extensionMapper = new LegacyArtifactExtensionMapping();

    private Map typeToDirectoryMap;

    public LegacyBidirectionalRepositoryLayout()
    {
        typeToDirectoryMap = new HashMap();
        typeToDirectoryMap.put( "ejb-client", "ejb" );
        typeToDirectoryMap.put( "distribution-tgz", "distribution" );
        typeToDirectoryMap.put( "distribution-zip", "distribution" );
    }

    public String getId()
    {
        return "legacy";
    }

    public String toPath( ArchivaArtifact reference )
    {
        return toPath( reference.getGroupId(), reference.getArtifactId(), reference
            .getVersion(), reference.getClassifier(), reference.getType() );
    }

    public String toPath( ProjectReference reference )
    {
        // TODO: Verify type
        return toPath( reference.getGroupId(), reference.getArtifactId(), null, null, "metadata-xml" );
    }

    public String toPath( ArtifactReference artifact )
    {
        return toPath( artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), artifact.getClassifier(),
                       artifact.getType() );
    }

    private String toPath( String groupId, String artifactId, String version, String classifier, String type )
    {
        StringBuffer path = new StringBuffer();

        path.append( groupId ).append( PATH_SEPARATOR );
        path.append( getDirectory( classifier, type ) ).append( PATH_SEPARATOR );

        if ( version != null )
        {
            path.append( artifactId ).append( '-' ).append( version );

            if ( StringUtils.isNotBlank( classifier ) )
            {
                path.append( '-' ).append( classifier );
            }

            path.append( '.' ).append( extensionMapper.getExtension( type ) );
        }

        return path.toString();
    }

    private String getDirectory( String classifier, String type )
    {
        // Special Cases involving classifiers and type.
        if ( "jar".equals( type ) && "sources".equals( classifier ) )
        {
            return "javadoc.jars";
        }

        // Special Cases involving only type.
        String dirname = (String) typeToDirectoryMap.get( type );

        if ( dirname != null )
        {
            return dirname + "s";
        }

        // Default process.
        return type + "s";
    }

    public ArchivaArtifact toArtifact( String path )
        throws LayoutException
    {
        String normalizedPath = StringUtils.replace( path, "\\", "/" );

        String pathParts[] = StringUtils.split( normalizedPath, '/' );

        /* Always 3 parts. (Never more or less)
         * 
         *   path = "commons-lang/jars/commons-lang-2.1.jar"
         *   path[0] = "commons-lang";          // The Group ID
         *   path[1] = "jars";                  // The Directory Type
         *   path[2] = "commons-lang-2.1.jar";  // The Filename.
         */

        if ( pathParts.length != 3 )
        {
            // Illegal Path Parts Length.
            throw new LayoutException( "Invalid number of parts to the path [" + path
                + "] to construct an ArchivaArtifact from. (Required to be 3 parts)" );
        }

        // The Group ID.
        String groupId = pathParts[0];

        // The Expected Type.
        String expectedType = pathParts[1];

        // The Filename.
        String filename = pathParts[2];

        FilenameParts fileParts = RepositoryLayoutUtils.splitFilename( filename, null );

        String type = extensionMapper.getType( filename );

        ArchivaArtifact artifact = new ArchivaArtifact( groupId, fileParts.artifactId, fileParts.version,
                                                        fileParts.classifier, type );

        // Sanity Checks.
        if ( StringUtils.isEmpty( fileParts.extension ) )
        {
            throw new LayoutException( "Invalid artifact, no extension." );
        }

        if ( !expectedType.equals( fileParts.extension + "s" ) )
        {
            throw new LayoutException( "Invalid artifact, extension and layout specified type mismatch." );
        }

        return artifact;
    }

}
