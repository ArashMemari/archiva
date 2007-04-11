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

import org.apache.maven.archiva.model.ArchivaArtifact;
import org.apache.maven.archiva.repository.layout.BidirectionalRepositoryLayout;
import org.apache.maven.archiva.repository.layout.LayoutException;

/**
 * DefaultBidirectionalRepositoryLayoutTest 
 *
 * @author <a href="mailto:joakim@erdfelt.com">Joakim Erdfelt</a>
 * @version $Id$
 */
public class DefaultBidirectionalRepositoryLayoutTest extends AbstractBidirectionalRepositoryLayoutTestCase
{
    private BidirectionalRepositoryLayout layout;

    protected void setUp() throws Exception
    {
        super.setUp();

        layout = (BidirectionalRepositoryLayout) lookup( BidirectionalRepositoryLayout.class.getName(), "default" );
    }

    public void testToPathBasic()
    {
        ArchivaArtifact artifact = createArtifact( "com.foo", "foo-tool", "1.0", "", "jar" );

        assertEquals( "com/foo/foo-tool/1.0/foo-tool-1.0.jar", layout.toPath( artifact ) );
    }

    public void testToPathEjbClient()
    {
        ArchivaArtifact artifact = createArtifact( "com.foo", "foo-client", "1.0", "", "ejb-client" );

        assertEquals( "com/foo/foo-client/1.0/foo-client-1.0.jar", layout.toPath( artifact ) );
    }

    public void testToPathWithClassifier()
    {
        ArchivaArtifact artifact = createArtifact( "com.foo.lib", "foo-lib", "2.1-alpha-1", "sources", "java-source" );

        assertEquals( "com/foo/lib/foo-lib/2.1-alpha-1/foo-lib-2.1-alpha-1-sources.jar", layout.toPath( artifact ) );
    }

    public void testToPathUsingUniqueSnapshot()
    {
        ArchivaArtifact artifact = createArtifact( "com.foo", "foo-connector", "2.1-20060822.123456-35", "", "jar" );

        assertEquals( "com/foo/foo-connector/2.1-SNAPSHOT/foo-connector-2.1-20060822.123456-35.jar",
                      layout.toPath( artifact ) );
    }

    public void testToArtifactBasicSimpleGroupId() throws LayoutException
    {
        ArchivaArtifact artifact = layout.toArtifact( "commons-lang/commons-lang/2.1/commons-lang-2.1.jar" );
        assertArtifact( artifact, "commons-lang", "commons-lang", "2.1", "", "jar" );
    }

    public void testToArtifactBasicLongGroupId() throws LayoutException
    {
        ArchivaArtifact artifact = layout.toArtifact( "com/foo/foo-tool/1.0/foo-tool-1.0.jar" );
        assertArtifact( artifact, "com.foo", "foo-tool", "1.0", "", "jar" );
    }

    public void testToArtifactEjbClient() throws LayoutException
    {
        ArchivaArtifact artifact = layout.toArtifact( "com/foo/foo-client/1.0/foo-client-1.0.jar" );
        // The type is correct. as we cannot possibly know this is an ejb client without parsing the pom
        assertArtifact( artifact, "com.foo", "foo-client", "1.0", "", "jar" );
    }

    public void testToArtifactWithClassifier() throws LayoutException
    {
        ArchivaArtifact artifact =
            layout.toArtifact( "com/foo/lib/foo-lib/2.1-alpha-1/foo-lib-2.1-alpha-1-sources.jar" );
        // The 'java-source' type is correct.  You might be thinking of extension, which we are not testing here.
        assertArtifact( artifact, "com.foo.lib", "foo-lib", "2.1-alpha-1", "sources", "java-source" );
    }

    public void testToArtifactUsingUniqueSnapshot() throws LayoutException
    {
        ArchivaArtifact artifact =
            layout.toArtifact( "com/foo/foo-connector/2.1-SNAPSHOT/foo-connector-2.1-20060822.123456-35.jar" );
        assertSnapshotArtifact( artifact, "com.foo", "foo-connector", "2.1-20060822.123456-35", "", "jar" );
    }

    public void testInvalidMissingType()
    {
        try
        {
            layout.toArtifact( "invalid/invalid/1/invalid-1" );
            fail( "Should have detected missing type." );
        }
        catch ( LayoutException e )
        {
            /* expected path */
        }
    }
    
    public void testInvalidNonSnapshotInSnapshotDir()
    {
        try
        {
            layout.toArtifact( "invalid/invalid/1.0-SNAPSHOT/invalid-1.0.jar" );
            fail( "Should have detected non snapshot artifact inside of a snapshot dir." );
        }
        catch ( LayoutException e )
        {
            /* expected path */
        }
    }
    
    public void testInvalidPathTooShort()
    {
        try
        {
            layout.toArtifact( "invalid/invalid-1.0.jar" );
            fail( "Should have detected that path is too short." );
        }
        catch ( LayoutException e )
        {
            /* expected path */
        }
    }
    
    public void testInvalidTimestampSnapshotNotInSnapshotDir()
    {
        try
        {
            layout.toArtifact( "invalid/invalid/1.0-20050611.123456-1/invalid-1.0-20050611.123456-1.jar" );
            fail( "Shoult have detected Timestamped Snapshot artifact not inside of an Snapshot dir is invalid." );
        }
        catch ( LayoutException e )
        {
            /* expected path */
        }
    }
    
    public void testInvalidVersionPathMismatch()
    {
        try
        {
            layout.toArtifact( "invalid/invalid/1.0/invalid-2.0.jar" );
            fail( "Should have detected version mismatch between path and artifact." );
        }
        catch ( LayoutException e )
        {
            /* expected path */
        }
    }
    
    public void testInvalidVersionPathMismatchAlt()
    {
        try
        {
            layout.toArtifact( "invalid/invalid/1.0/invalid-1.0b.jar" );
            fail( "Should have version mismatch between directory and artifact." );
        }
        catch ( LayoutException e )
        {
            /* expected path */
        }
    }
    
    public void testInvalidArtifactIdForPath()
    {
        try
        {
            layout.toArtifact( "org/apache/maven/test/1.0-SNAPSHOT/wrong-artifactId-1.0-20050611.112233-1.jar" );
            fail( "Should have detected wrong artifact Id." );
        }
        catch ( LayoutException e )
        {
            /* expected path */
        }
    }
}
